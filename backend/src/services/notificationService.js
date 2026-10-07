export class NotificationService {
  constructor({ demo = true, logger = console } = {}) { this.demo = demo; this.logger = logger; }
  async emergencyCreated(record) {
    if (this.demo) this.logger.info(`ALERTA SIMULADA: usuario ${record.userId} necesita ayuda.`, {
      hora: record.timestamp, ubicacion: { latitude: record.latitude, longitude: record.longitude },
      emergencyId: record.emergencyId, simulated: record.simulated,
    });
    else this.logger.info('emergency_created', { emergencyId: record.emergencyId });
  }
}
