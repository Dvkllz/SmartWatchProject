import { z } from 'zod';

export class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
export const idSchema = z.uuid();
const timestamp = z.iso.datetime().refine(value => Number.isFinite(Date.parse(value)), 'Fecha inválida');
const latitude = z.number().min(-90).max(90);
const longitude = z.number().min(-180).max(180);
export const createSchema = z.strictObject({
  userId: z.string().regex(/^[A-Za-z0-9_-]{1,80}$/),
  latitude: latitude.nullable(), longitude: longitude.nullable(),
  timestamp, status: z.literal('ACTIVE'), simulated: z.boolean().default(false),
}).refine(value => (value.latitude === null) === (value.longitude === null), 'Coordenadas incompletas');
export const locationSchema = z.strictObject({
  latitude, longitude, timestamp, simulated: z.boolean().default(false), sampleId: idSchema.optional(),
});
export const finishSchema = z.strictObject({ timestamp });
export const audioFieldsSchema = z.strictObject({ simulated: z.enum(['true', 'false']).default('false') });

export function requireActive(record) {
  if (!record) throw new HttpError(404, 'Emergencia no encontrada');
  if (record.status !== 'ACTIVE') throw new HttpError(409, 'La emergencia ya finalizó');
}

export function audioFormat(file) {
  if (!file || file.buffer.length < 44) throw new HttpError(400, 'Se requiere un archivo de audio válido');
  const bytes = file.buffer;
  if (file.mimetype === 'audio/wav' && bytes.toString('ascii', 0, 4) === 'RIFF' && bytes.toString('ascii', 8, 12) === 'WAVE') {
    if (bytes.readUInt32LE(4) + 8 !== bytes.length) throw new HttpError(400, 'WAV incompleto');
    return 'wav';
  }
  if (['audio/mp4', 'audio/m4a', 'audio/x-m4a'].includes(file.mimetype) && bytes.toString('ascii', 4, 8) === 'ftyp') return 'm4a';
  throw new HttpError(415, 'Formato permitido: WAV o MPEG-4/AAC');
}
