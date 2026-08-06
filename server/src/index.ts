import fs from 'node:fs';
import path from 'node:path';
import express from 'express';
import { createApp } from './api';
import { getDb } from './db';
import { ensureExplorationSchema } from './exploration';
import { startScheduler } from './scheduler';

const PORT = Number(process.env.PORT || 4318);
const DIST_DIR = path.resolve(__dirname, '..', '..', 'archive-assistant-web', 'dist');

function main(): void {
  getDb();
  ensureExplorationSchema();
  const app = createApp();

  if (fs.existsSync(DIST_DIR)) {
    app.use(express.static(DIST_DIR));
    app.get(/^\/(?!api\/).*/, (_req, res) => {
      res.sendFile(path.join(DIST_DIR, 'index.html'));
    });
  }

  app.listen(PORT, '127.0.0.1', () => {
    console.log(`三省六部本地服务已启动：http://localhost:${PORT}`);
  });

  startScheduler();
  console.log('本机调度器已启动（每 30 秒检查一次，启动时补抓当天任务）。');
}

main();
