import { mkdir, readFile, writeFile, rename, readdir } from 'node:fs/promises';
import path from 'node:path';
import { idSchema } from '../models/emergency.js';

// Contrato sustituible: get(id), transaction(id, mutation). Un solo proceso escritor.
export class JsonEmergencyRepository {
  #queue = Promise.resolve();
  constructor(directory) { this.directory = directory; }
  async get(id) {
    idSchema.parse(id);
    try { return JSON.parse(await readFile(path.join(this.directory, `${id}.json`), 'utf8')); }
    catch (error) { if (error.code === 'ENOENT') return null; throw error; }
  }
  async list() {
    let names;
    try { names = await readdir(this.directory); }
    catch (error) { if (error.code === 'ENOENT') return []; throw error; }
    const records = await Promise.all(names.filter(name => /^[0-9a-f-]{36}\.json$/i.test(name))
      .map(name => this.get(name.slice(0, -5))));
    return records.filter(Boolean).sort((left, right) =>
      Date.parse(right.receivedAt ?? right.timestamp) - Date.parse(left.receivedAt ?? left.timestamp));
  }
  transaction(id, mutation) {
    idSchema.parse(id);
    const operation = this.#queue.then(async () => {
      const original = await this.get(id);
      const { record, result } = await mutation(original);
      if (record) {
        await mkdir(this.directory, { recursive: true });
        const target = path.join(this.directory, `${id}.json`);
        await writeFile(`${target}.tmp`, JSON.stringify(record, null, 2), { mode: 0o600, flush: true });
        await rename(`${target}.tmp`, target);
      }
      return result;
    });
    this.#queue = operation.catch(() => {});
    return operation;
  }
}
