import { createApp } from './app.js';
import { config } from './config.js';

const server = createApp(config).listen(config.port, config.host, () => {
  console.info(`Watch SOS backend: http://${config.host}:${config.port}`);
});
server.requestTimeout = 60_000;
server.on('error', error => { console.error('server_failed', error.code); process.exitCode = 1; });
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 10_000).unref();
});
