import { randomUUID, createHash } from 'node:crypto';
import { mkdir, writeFile, rename } from 'node:fs/promises';
import path from 'node:path';
import { HttpError, requireActive, audioFormat } from '../models/emergency.js';

const hash = value => createHash('sha256').update(value).digest('hex');
const response = record => ({ emergencyId: record.emergencyId, status: record.status });

export class EmergencyService {
  constructor(repository, notifications, config, logger = console) {
    Object.assign(this, { repository, notifications, config, logger });
  }
  async create(body, key) {
    const id = key ?? randomUUID();
    const fingerprint = hash(JSON.stringify(body));
    const result = await this.repository.transaction(id, record => {
      if (record) {
        if (record.fingerprint !== fingerprint) throw new HttpError(409, 'Clave de idempotencia reutilizada con otros datos');
        return { result: { created: false, record } };
      }
      const next = { ...body, emergencyId: id, receivedAt: new Date().toISOString(), finishedAt: null,
        fingerprint, locations: [], audio: [] };
      return { record: next, result: { created: true, record: next } };
    });
    if (result.created) await this.notifications.emergencyCreated(result.record);
    return { created: result.created, body: response(result.record) };
  }
  async location(id, body) {
    return this.repository.transaction(id, record => {
      if (!record) throw new HttpError(404, 'Emergencia no encontrada');
      const duplicate = body.sampleId && record.locations.find(point => point.sampleId === body.sampleId);
      if (duplicate) {
        if (JSON.stringify(duplicate) !== JSON.stringify(body)) throw new HttpError(409, 'sampleId reutilizado con otros datos');
        return { result: { received: true } };
      }
      requireActive(record);
      if (record.locations.length >= this.config.maxLocations) throw new HttpError(413, 'Límite de ubicaciones alcanzado');
      record.locations.push(body);
      this.logger.info('location_received', { emergencyId: id, simulated: body.simulated });
      return { record, result: { received: true } };
    });
  }
  async audio(id, uploadId, file, simulated) {
    const extension = audioFormat(file);
    if (extension === 'wav' && !simulated) throw new HttpError(400, 'WAV reservado al audio de demostración');
    const digest = hash(file.buffer);
    return this.repository.transaction(id, async record => {
      if (!record) throw new HttpError(404, 'Emergencia no encontrada');
      const existing = record.audio.find(audio => audio.uploadId === uploadId);
      if (existing) {
        if (existing.sha256 !== digest || existing.simulated !== simulated) throw new HttpError(409, 'ID de audio reutilizado con otros datos');
        return { result: existing };
      }
      requireActive(record);
      if (record.audio.reduce((sum, item) => sum + item.bytes, 0) + file.size > this.config.maxSessionAudioBytes) {
        throw new HttpError(413, 'Límite de audio por emergencia alcanzado');
      }
      const directory = path.join(this.config.uploadDir, id);
      await mkdir(directory, { recursive: true });
      // Nunca se utiliza originalname ni una ruta suministrada por el cliente.
      const filename = `${uploadId}.${extension}`;
      const target = path.join(directory, filename);
      await writeFile(`${target}.part`, file.buffer, { mode: 0o600, flush: true });
      await rename(`${target}.part`, target);
      const entry = { uploadId, filename, bytes: file.size, mimeType: file.mimetype, simulated,
        sha256: digest, receivedAt: new Date().toISOString() };
      record.audio.push(entry);
      this.logger.info('audio_received', { emergencyId: id, bytes: file.size, simulated });
      return { record, result: entry };
    });
  }
  async finish(id, body) {
    return this.repository.transaction(id, record => {
      if (!record) throw new HttpError(404, 'Emergencia no encontrada');
      if (record.status === 'FINISHED') return { result: response(record) };
      if (Date.parse(body.timestamp) < Date.parse(record.timestamp)) throw new HttpError(400, 'El cierre es anterior al inicio');
      record.status = 'FINISHED';
      record.finishedAt = body.timestamp;
      record.finishReceivedAt = new Date().toISOString();
      this.logger.info('emergency_finished', { emergencyId: id });
      return { record, result: response(record) };
    });
  }
  async get(id) {
    const record = await this.repository.get(id);
    if (!record) throw new HttpError(404, 'Emergencia no encontrada');
    const { fingerprint, ...publicRecord } = record;
    return publicRecord;
  }
}
