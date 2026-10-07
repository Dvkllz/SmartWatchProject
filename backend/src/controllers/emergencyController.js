import { audioFieldsSchema, createSchema, finishSchema, idSchema, locationSchema } from '../models/emergency.js';
import { randomUUID } from 'node:crypto';

export function emergencyController(service) {
  return {
    create: async (req, res) => {
      const key = req.get('Idempotency-Key');
      const result = await service.create(createSchema.parse(req.body), key ? idSchema.parse(key) : undefined);
      res.status(result.created ? 201 : 200).json(result.body);
    },
    location: async (req, res) => res.json(await service.location(req.params.id, locationSchema.parse(req.body))),
    audio: async (req, res) => {
      const uploadId = idSchema.parse(req.get('X-Upload-Id') ?? randomUUID());
      const fields = audioFieldsSchema.parse(req.body);
      res.json(await service.audio(req.params.id, uploadId, req.file, fields.simulated === 'true'));
    },
    finish: async (req, res) => res.json(await service.finish(req.params.id, finishSchema.parse(req.body))),
    get: async (req, res) => res.json(await service.get(req.params.id)),
  };
}
