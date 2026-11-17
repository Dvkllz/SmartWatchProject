import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
function integer(name, fallback, min, max) {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isInteger(value) || value < min || value > max) throw new Error(`Configuración inválida: ${name}`);
  return value;
}
export const config = {
  host: process.env.HOST ?? '127.0.0.1',
  port: integer('PORT', 3000, 1, 65535),
  dataDir: path.resolve(root, process.env.DATA_DIR ?? 'data'),
  uploadDir: path.resolve(root, process.env.UPLOAD_DIR ?? 'uploads'),
  demoNotifications: process.env.DEMO_NOTIFICATIONS !== 'false',
  voiceCalls: {
    accountSid: process.env.TWILIO_ACCOUNT_SID?.trim() ?? '',
    authToken: process.env.TWILIO_AUTH_TOKEN?.trim() ?? '',
    fromNumber: process.env.TWILIO_FROM_NUMBER?.trim() ?? '',
    trustedContacts: (process.env.TRUSTED_CONTACTS ?? '').split(',').map(value => value.trim()).filter(Boolean),
  },
  maxAudioBytes: integer('MAX_AUDIO_BYTES', 8 * 1024 * 1024, 44, 32 * 1024 * 1024),
  maxSessionAudioBytes: integer('MAX_SESSION_AUDIO_BYTES', 100 * 1024 * 1024, 44, 1024 * 1024 * 1024),
  maxLocations: integer('MAX_LOCATIONS', 20_000, 1, 100_000),
};

if (!config.demoNotifications) {
  const { accountSid, authToken, fromNumber, trustedContacts } = config.voiceCalls;
  if (!/^AC[0-9a-fA-F]{32}$/.test(accountSid) || !authToken || !/^\+[1-9]\d{7,14}$/.test(fromNumber)) {
    throw new Error('Configura TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_FROM_NUMBER para llamadas reales');
  }
  if (trustedContacts.length === 0 || trustedContacts.length > 5 || trustedContacts.some(number => !/^\+[1-9]\d{7,14}$/.test(number))) {
    throw new Error('TRUSTED_CONTACTS debe incluir entre 1 y 5 teléfonos E.164 separados por comas');
  }
}
