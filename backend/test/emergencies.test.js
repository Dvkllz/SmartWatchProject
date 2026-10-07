import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createApp } from '../src/app.js';

const quiet = { info() {}, error() {} };
const timestamp = '2026-10-06T12:00:00.000Z';
const creation = { userId: 'demo-user', latitude: 1.0, longitude: -77.0, timestamp, status: 'ACTIVE', simulated: true };
const point = () => ({ latitude: 1.1, longitude: -77.1, timestamp, simulated: true, sampleId: randomUUID() });

async function setup(t, overrides = {}) {
  const root = await mkdtemp(path.join(os.tmpdir(), 'watch-sos-test-'));
  const config = { dataDir: path.join(root, 'data'), uploadDir: path.join(root, 'uploads'),
    maxAudioBytes: 1024 * 1024, maxSessionAudioBytes: 2 * 1024 * 1024, maxLocations: 100, demoNotifications: true, ...overrides };
  let notifications = 0;
  const app = createApp(config, { logger: quiet, notifications: { async emergencyCreated() { notifications++; } } });
  const server = app.listen(0, '127.0.0.1');
  await new Promise((resolve, reject) => { server.once('listening', resolve); server.once('error', reject); });
  const base = `http://127.0.0.1:${server.address().port}`;
  t.after(async () => {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
    await rm(root, { recursive: true, force: true });
  });
  async function request(route, body, headers = {}) {
    const response = await fetch(`${base}${route}`, { method: body === undefined ? 'GET' : 'POST',
      headers: { 'Content-Type': 'application/json', ...headers }, body: body === undefined ? undefined : JSON.stringify(body) });
    return { status: response.status, body: await response.json() };
  }
  async function create(id = randomUUID(), body = creation) {
    const result = await request('/api/emergencies', body, { 'Idempotency-Key': id });
    assert.equal(result.status, 201);
    assert.equal(result.body.emergencyId, id);
    return id;
  }
  async function upload(id, uploadId = randomUUID(), bytes = wav(), filename = '../../escape.wav') {
    const form = new FormData();
    form.set('simulated', 'true');
    form.set('audio', new Blob([bytes], { type: 'audio/wav' }), filename);
    const response = await fetch(`${base}/api/emergencies/${id}/audio`, { method: 'POST', headers: { 'X-Upload-Id': uploadId }, body: form });
    return { status: response.status, body: await response.json() };
  }
  return { request, create, upload, config, notificationCount: () => notifications };
}

function wav() {
  const buffer = Buffer.alloc(844);
  buffer.write('RIFF'); buffer.writeUInt32LE(buffer.length - 8, 4); buffer.write('WAVEfmt ', 8);
  buffer.writeUInt32LE(16, 16); buffer.writeUInt16LE(1, 20); buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(8000, 24); buffer.writeUInt32LE(16000, 28); buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34); buffer.write('data', 36); buffer.writeUInt32LE(800, 40);
  return buffer;
}

test('SOS completo persiste ubicación, audio, fecha y cierre; nombres seguros', async t => {
  const api = await setup(t);
  const id = await api.create();
  assert.equal((await api.request(`/api/emergencies/${id}/location`, point())).status, 200);
  const audio = await api.upload(id);
  assert.equal(audio.status, 200);
  assert.match(audio.body.filename, /^[a-f0-9-]+\.wav$/);
  const finish = await api.request(`/api/emergencies/${id}/finish`, { timestamp });
  assert.equal(finish.body.status, 'FINISHED');
  const record = (await api.request(`/api/emergencies/${id}`)).body;
  assert.equal(record.locations.length, 1);
  assert.equal(record.audio.length, 1);
  assert.equal(record.finishedAt, timestamp);
  assert.equal(record.fingerprint, undefined);
  const saved = JSON.parse(await readFile(path.join(api.config.dataDir, `${id}.json`), 'utf8'));
  assert.equal(saved.status, 'FINISHED');
  const storedAudio = await readFile(path.join(api.config.uploadDir, id, audio.body.filename));
  assert.deepEqual(storedAudio, wav());
  assert.equal(api.notificationCount(), 1);
});

test('reintentos de creación, ubicación, audio y cierre son idempotentes', async t => {
  const api = await setup(t);
  const id = await api.create();
  assert.equal((await api.request('/api/emergencies', creation, { 'Idempotency-Key': id })).status, 200);
  const location = point();
  await api.request(`/api/emergencies/${id}/location`, location);
  await api.request(`/api/emergencies/${id}/location`, location);
  const uploadId = randomUUID();
  await api.upload(id, uploadId); await api.upload(id, uploadId);
  await api.request(`/api/emergencies/${id}/finish`, { timestamp });
  assert.equal((await api.request(`/api/emergencies/${id}/finish`, { timestamp })).status, 200);
  assert.equal((await api.upload(id, uploadId)).status, 200);
  const record = (await api.request(`/api/emergencies/${id}`)).body;
  assert.equal(record.locations.length, 1); assert.equal(record.audio.length, 1);
  assert.equal(api.notificationCount(), 1);
});

test('rechaza claves reutilizadas con otros datos', async t => {
  const api = await setup(t); const id = await api.create();
  assert.equal((await api.request('/api/emergencies', { ...creation, userId: 'otro' }, { 'Idempotency-Key': id })).status, 409);
  const location = point(); await api.request(`/api/emergencies/${id}/location`, location);
  assert.equal((await api.request(`/api/emergencies/${id}/location`, { ...location, latitude: 2 })).status, 409);
  const uploadId = randomUUID(); await api.upload(id, uploadId);
  const changed = wav(); changed[100] = 1;
  assert.equal((await api.upload(id, uploadId, changed)).status, 409);
});

test('datos nuevos no modifican una emergencia finalizada', async t => {
  const api = await setup(t); const id = await api.create();
  await api.request(`/api/emergencies/${id}/finish`, { timestamp });
  assert.equal((await api.request(`/api/emergencies/${id}/location`, point())).status, 409);
  assert.equal((await api.upload(id)).status, 409);
});

test('valida coordenadas, fechas, IDs, campos y alertas sin GPS', async t => {
  const api = await setup(t);
  for (const body of [ { ...creation, latitude: 91 }, { ...creation, longitude: 'a' },
    { ...creation, timestamp: 'ayer' }, { ...creation, latitude: null }, { ...creation, admin: true },
    { ...creation, userId: 'x\nALERTA' }, { ...creation, status: 'FINISHED' } ]) {
    assert.equal((await api.request('/api/emergencies', body)).status, 400);
  }
  assert.equal((await api.request('/api/emergencies/no-es-uuid')).status, 400);
  assert.equal((await api.request(`/api/emergencies/${randomUUID()}`)).status, 404);
  await api.create(randomUUID(), { ...creation, latitude: null, longitude: null });
});

test('subidas vacías, falsas o demasiado grandes son rechazadas', async t => {
  const api = await setup(t, { maxAudioBytes: 900 }); const id = await api.create();
  assert.equal((await api.upload(id, randomUUID(), Buffer.from('falso'))).status, 400);
  assert.equal((await api.upload(id, randomUUID(), Buffer.alloc(100))).status, 415);
  assert.equal((await api.upload(id, randomUUID(), Buffer.alloc(901))).status, 413);
  const record = (await api.request(`/api/emergencies/${id}`)).body;
  assert.equal(record.audio.length, 0);
});

test('serializa escrituras concurrentes sin perder ubicaciones', async t => {
  const api = await setup(t); const id = await api.create();
  const results = await Promise.all(Array.from({ length: 20 }, () => api.request(`/api/emergencies/${id}/location`, point())));
  assert.ok(results.every(result => result.status === 200));
  assert.equal((await api.request(`/api/emergencies/${id}`)).body.locations.length, 20);
});

test('limita almacenamiento por sesión y rechaza cierre anterior al inicio', async t => {
  const api = await setup(t, { maxLocations: 1, maxSessionAudioBytes: 850 }); const id = await api.create();
  assert.equal((await api.request(`/api/emergencies/${id}/location`, point())).status, 200);
  assert.equal((await api.request(`/api/emergencies/${id}/location`, point())).status, 413);
  assert.equal((await api.upload(id)).status, 200);
  assert.equal((await api.upload(id)).status, 413);
  assert.equal((await api.request(`/api/emergencies/${id}/finish`, { timestamp: '2026-10-05T00:00:00Z' })).status, 400);
  assert.equal((await readdir(path.join(api.config.uploadDir, id))).length, 1);
});
