import express from 'express';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import multer from 'multer';
import { ZodError } from 'zod';
import { emergencyRoutes } from './routes/emergencyRoutes.js';
import { JsonEmergencyRepository } from './repositories/jsonEmergencyRepository.js';
import { NotificationService } from './services/notificationService.js';
import { EmergencyService } from './services/emergencyService.js';
import { HttpError } from './models/emergency.js';

export function createApp(config, { logger = console, notifications, repository } = {}) {
  const app = express();
  app.disable('x-powered-by');
  app.use(helmet());
  app.use(rateLimit({ windowMs: 60_000, limit: 600, standardHeaders: 'draft-8', legacyHeaders: false,
    message: { error: 'Demasiadas solicitudes; reintenta en un minuto' } }));
  app.use(express.json({ limit: '16kb' }));
  app.get('/health', (req, res) => res.json({ status: 'ok' }));
  const service = new EmergencyService(repository ?? new JsonEmergencyRepository(config.dataDir),
    notifications ?? new NotificationService({ demo: config.demoNotifications, logger }), config, logger);
  app.use('/api/emergencies', emergencyRoutes(service, config));
  app.use((req, res) => res.status(404).json({ error: 'Ruta no encontrada' }));
  app.use((error, req, res, next) => {
    if (res.headersSent) return next(error);
    if (error instanceof ZodError) return res.status(400).json({ error: 'Datos inválidos', fields: error.issues.map(issue => issue.path.join('.')) });
    if (error instanceof multer.MulterError) return res.status(error.code === 'LIMIT_FILE_SIZE' ? 413 : 400).json({ error: 'Archivo o formulario fuera de los límites permitidos' });
    if (error instanceof HttpError) return res.status(error.status).json({ error: error.message });
    if (error.type === 'entity.too.large') return res.status(413).json({ error: 'Solicitud demasiado grande' });
    if (error instanceof SyntaxError && 'body' in error) return res.status(400).json({ error: 'JSON inválido' });
    logger.error('request_failed', { type: error.name });
    res.status(500).json({ error: 'No se pudo guardar la solicitud; reintenta' });
  });
  return app;
}
