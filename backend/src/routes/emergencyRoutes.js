import { Router } from 'express';
import multer from 'multer';
import { emergencyController } from '../controllers/emergencyController.js';
import { idSchema } from '../models/emergency.js';

export function emergencyRoutes(service, config) {
  const router = Router();
  const controller = emergencyController(service);
  const upload = multer({ storage: multer.memoryStorage(),
    limits: { fileSize: config.maxAudioBytes, files: 1, fields: 1, parts: 2, fieldSize: 16, fieldNameSize: 32 } });
  router.param('id', (req, res, next, id) => {
    try { idSchema.parse(id); next(); } catch (error) { next(error); }
  });
  router.post('/', controller.create);
  router.get('/:id', controller.get);
  router.post('/:id/location', controller.location);
  router.post('/:id/audio', upload.single('audio'), controller.audio);
  router.post('/:id/finish', controller.finish);
  return router;
}
