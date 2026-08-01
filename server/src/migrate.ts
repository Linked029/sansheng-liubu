import fs from 'node:fs';
import { importData } from './api';
import { ensureDefaultPreferences, getDb } from './db';

function main(): void {
  const file = process.argv[2];
  if (!file) {
    console.error('用法：npm run migrate -- <V2导出JSON文件>');
    process.exit(1);
  }
  if (!fs.existsSync(file)) {
    console.error(`文件不存在：${file}`);
    process.exit(1);
  }

  const raw = fs.readFileSync(file, 'utf8');
  const data = JSON.parse(raw) as Record<string, unknown>;
  getDb();
  importData(data);
  ensureDefaultPreferences();

  const topics = Array.isArray(data.topics) ? data.topics.length : Array.isArray(data.ministries) ? data.ministries.length : 0;
  const items = Array.isArray(data.items) ? data.items.length : 0;
  console.log(`迁移完成：${topics} 个主题 / 六部，${items} 条条目已导入 SQLite。`);
}

try {
  main();
} catch (error) {
  console.error('迁移失败：', error instanceof Error ? error.message : error);
  process.exit(1);
}
