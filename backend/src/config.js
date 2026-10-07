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
  maxAudioBytes: integer('MAX_AUDIO_BYTES', 8 * 1024 * 1024, 44, 32 * 1024 * 1024),
  maxSessionAudioBytes: integer('MAX_SESSION_AUDIO_BYTES', 100 * 1024 * 1024, 44, 1024 * 1024 * 1024),
  maxLocations: integer('MAX_LOCATIONS', 20_000, 1, 100_000),
};
