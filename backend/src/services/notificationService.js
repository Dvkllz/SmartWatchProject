export class NotificationService {
  constructor({ demo = true, logger = console, voiceCalls, fetchImpl = fetch } = {}) {
    this.demo = demo;
    this.logger = logger;
    this.voiceCalls = voiceCalls;
    this.fetch = fetchImpl;
  }

  async emergencyCreated(record) {
    if (this.demo) this.logger.info(`ALERTA SIMULADA: usuario ${record.userId} necesita ayuda.`, {
      hora: record.timestamp, ubicacion: { latitude: record.latitude, longitude: record.longitude },
      emergencyId: record.emergencyId, simulated: record.simulated,
    });
    else if (record.simulated) this.logger.info('trusted_contact_calls_skipped_simulated_emergency', {
      emergencyId: record.emergencyId,
    });
    else await this.callTrustedContacts(record);
  }

  async callTrustedContacts(record) {
    const { accountSid, authToken, fromNumber, trustedContacts } = this.voiceCalls;
    const url = `https://api.twilio.com/2010-04-01/Accounts/${accountSid}/Calls.json`;
    const authorization = `Basic ${Buffer.from(`${accountSid}:${authToken}`).toString('base64')}`;
    const body = new URLSearchParams({
      From: fromNumber,
      Twiml: buildEmergencyTwiML(record),
      Timeout: '20',
      TimeLimit: '60',
    });

    const outcomes = await Promise.allSettled(trustedContacts.map(async (to, index) => {
      const response = await this.fetch(url, {
        method: 'POST',
        headers: { Authorization: authorization, 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams([...body.entries(), ['To', to]]),
        signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) throw new Error(`Twilio respondió ${response.status}`);
      const result = await response.json();
      this.logger.info('trusted_contact_call_queued', {
        emergencyId: record.emergencyId,
        contactIndex: index + 1,
        callSid: result.sid,
        status: result.status,
      });
    }));

    const failed = outcomes.filter(outcome => outcome.status === 'rejected');
    if (failed.length) {
      this.logger.error('trusted_contact_call_failed', {
        emergencyId: record.emergencyId,
        failedCount: failed.length,
        error: failed[0].reason?.message ?? 'unknown',
      });
    }
    this.logger.info('trusted_contact_calls_submitted', {
      emergencyId: record.emergencyId,
      accepted: outcomes.length - failed.length,
      failed: failed.length,
    });
  }
}

function buildEmergencyTwiML(record) {
  const parts = [
    'Alerta SOS de Watch SOS. Una persona necesita ayuda.',
  ];
  if (record.latitude !== null && record.longitude !== null) {
    parts.push(`Última ubicación: latitud ${record.latitude.toFixed(5)}, longitud ${record.longitude.toFixed(5)}.`);
  } else {
    parts.push('La ubicación no está disponible todavía.');
  }
  parts.push('Por favor, contacta a esta persona cuanto antes.');
  const text = parts.join(' ').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;',
  })[character]);
  return `<Response><Say language="es-MX">${text}</Say></Response>`;
}
