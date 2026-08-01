import Database from 'better-sqlite3';
import fs from 'node:fs';
import path from 'node:path';
import type { MinistryRow, SourceRow, PreferenceRow, ItemRow } from './types';

const DATA_DIR = path.resolve(__dirname, '..', 'data');
const DB_PATH = process.env.SSS_DB_PATH || path.join(DATA_DIR, 'sansheng.sqlite');

let db: Database.Database | null = null;

export const DEFAULT_MINISTRY_IDS = ['officials', 'treasury', 'rites', 'military', 'justice', 'works'] as const;

const DEFAULT_MINISTRIES: MinistryRow[] = [
  { id: 'officials', title: '吏 · 名籍', icon: 'folder-spark', color: '#8B7D6B', sort_order: 0 },
  { id: 'treasury', title: '户 · 府库', icon: 'folder-clover', color: '#A67C52', sort_order: 1 },
  { id: 'rites', title: '礼 · 典章', icon: 'folder-star', color: '#7A5C3A', sort_order: 2 },
  { id: 'military', title: '兵 · 行令', icon: 'folder-shield', color: '#5E5D59', sort_order: 3 },
  { id: 'justice', title: '刑 · 稽核', icon: 'folder-gavel', color: '#4A4A44', sort_order: 4 },
  { id: 'works', title: '工 · 营造', icon: 'folder-tool', color: '#6B5E50', sort_order: 5 },
];

const DEFAULT_PREFERENCES: PreferenceRow[] = [
  { ministry_id: 'officials', description: '关注人物、作者、组织与人脉动向；收录访谈、人物志与职业经历。', exclude_keywords: '[]', exclude_domains: '[]', daily_limit: 3, schedule_cron: '0 8 * * *' },
  { ministry_id: 'treasury', description: '关注个人财务、账单、资产与订阅管理；不收录荐股和营销软文。', exclude_keywords: '["荐股","加群","私域变现"]', exclude_domains: '[]', daily_limit: 3, schedule_cron: '0 8 * * *' },
  { ministry_id: 'rites', description: '关注学习、技术与知识体系；收录深度教程、论文和技术周刊。', exclude_keywords: '[]', exclude_domains: '[]', daily_limit: 3, schedule_cron: '0 8 * * *' },
  { ministry_id: 'military', description: '关注健康、效率、工具与执行方法；收录健康管理和效率工具实践。', exclude_keywords: '[]', exclude_domains: '[]', daily_limit: 3, schedule_cron: '0 8 * * *' },
  { ministry_id: 'justice', description: '关注法规、稽核、合规与防坑；收录法规标准、消费者警示和开源协议。', exclude_keywords: '[]', exclude_domains: '[]', daily_limit: 3, schedule_cron: '0 8 * * *' },
  { ministry_id: 'works', description: '关注工程、项目与实操；收录工程博客、项目文档和部署教程。', exclude_keywords: '[]', exclude_domains: '[]', daily_limit: 3, schedule_cron: '0 8 * * *' },
];

export function getDb(): Database.Database {
  if (db) return db;
  fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });
  db = new Database(DB_PATH);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  initSchema(db);
  seedDefaults(db);
  return db;
}

function initSchema(database: Database.Database): void {
  database.exec(`
    CREATE TABLE IF NOT EXISTS ministries (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      icon TEXT NOT NULL DEFAULT 'folder-spark',
      color TEXT NOT NULL DEFAULT '#8B7D6B',
      sort_order INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS sources (
      id TEXT PRIMARY KEY,
      ministry_id TEXT NOT NULL REFERENCES ministries(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      kind TEXT NOT NULL CHECK (kind IN ('feed', 'url', 'manual')),
      location TEXT NOT NULL DEFAULT '',
      enabled INTEGER NOT NULL DEFAULT 1,
      last_fetched_at TEXT,
      last_status TEXT,
      fail_streak INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS preferences (
      ministry_id TEXT PRIMARY KEY REFERENCES ministries(id) ON DELETE CASCADE,
      description TEXT NOT NULL DEFAULT '',
      exclude_keywords TEXT NOT NULL DEFAULT '[]',
      exclude_domains TEXT NOT NULL DEFAULT '[]',
      daily_limit INTEGER NOT NULL DEFAULT 3,
      schedule_cron TEXT NOT NULL DEFAULT '0 8 * * *'
    );

    CREATE TABLE IF NOT EXISTS items (
      id TEXT PRIMARY KEY,
      ministry_id TEXT NOT NULL REFERENCES ministries(id),
      source_id TEXT REFERENCES sources(id) ON DELETE SET NULL,
      content_type TEXT NOT NULL DEFAULT 'WEB_ARTICLE',
      title TEXT NOT NULL,
      summary TEXT NOT NULL DEFAULT '',
      full_text TEXT NOT NULL DEFAULT '',
      source_url TEXT,
      document_format TEXT,
      file_name TEXT,
      status TEXT NOT NULL DEFAULT 'candidate'
        CHECK (status IN ('candidate', 'pending', 'archived', 'read', 'reviewing', 'mastered')),
      quality_score REAL NOT NULL DEFAULT 0,
      ai_reason TEXT,
      redraft_count INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      approved_at TEXT,
      archived_at TEXT,
      read_at TEXT
    );

    CREATE INDEX IF NOT EXISTS idx_items_ministry_status ON items(ministry_id, status);
    CREATE INDEX IF NOT EXISTS idx_items_source_url ON items(source_url);

    CREATE TABLE IF NOT EXISTS reviews (
      item_id TEXT PRIMARY KEY REFERENCES items(id) ON DELETE CASCADE,
      stage INTEGER NOT NULL DEFAULT 0,
      due_at TEXT NOT NULL,
      interval_days INTEGER NOT NULL DEFAULT 0,
      ease REAL NOT NULL DEFAULT 2.5,
      review_count INTEGER NOT NULL DEFAULT 0,
      last_reviewed_at TEXT,
      status TEXT NOT NULL DEFAULT 'reviewing'
    );

    CREATE TABLE IF NOT EXISTS annotations (
      id TEXT PRIMARY KEY,
      item_id TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
      created_at TEXT NOT NULL,
      text TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS fetch_logs (
      id TEXT PRIMARY KEY,
      date TEXT NOT NULL,
      ministry_id TEXT NOT NULL REFERENCES ministries(id),
      source_id TEXT REFERENCES sources(id) ON DELETE SET NULL,
      status TEXT NOT NULL,
      item_count INTEGER NOT NULL DEFAULT 0,
      error TEXT,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS reject_logs (
      id TEXT PRIMARY KEY,
      item_id TEXT NOT NULL,
      source_id TEXT REFERENCES sources(id) ON DELETE SET NULL,
      reason TEXT,
      title TEXT NOT NULL,
      source_url TEXT,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS settings (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS scheduler_state (
      ministry_id TEXT PRIMARY KEY REFERENCES ministries(id) ON DELETE CASCADE,
      last_run_date TEXT NOT NULL,
      last_run_at TEXT NOT NULL
    );
  `);
}

function seedDefaults(database: Database.Database): void {
  const ministryCount = (database.prepare('SELECT COUNT(*) AS c FROM ministries').get() as { c: number }).c;
  if (ministryCount === 0) {
    const insertMinistry = database.prepare(
      'INSERT INTO ministries (id, title, icon, color, sort_order) VALUES (@id, @title, @icon, @color, @sort_order)'
    );
    const insertPreference = database.prepare(
      `INSERT INTO preferences (ministry_id, description, exclude_keywords, exclude_domains, daily_limit, schedule_cron)
       VALUES (@ministry_id, @description, @exclude_keywords, @exclude_domains, @daily_limit, @schedule_cron)`
    );
    const insertAll = database.transaction(() => {
      for (const m of DEFAULT_MINISTRIES) insertMinistry.run(m);
      for (const p of DEFAULT_PREFERENCES) insertPreference.run(p);
    });
    insertAll();
  }

  const settingsCount = (database.prepare('SELECT COUNT(*) AS c FROM settings').get() as { c: number }).c;
  if (settingsCount === 0) {
    const insertSetting = database.prepare('INSERT INTO settings (key, value) VALUES (?, ?)');
    const defaults: [string, string][] = [
      ['ai', JSON.stringify({ engineType: 'OPENAI_COMPATIBLE', baseUrl: 'https://api.deepseek.com/v1', modelName: 'deepseek-chat', apiKey: '' })],
      ['aiPresets', '[]'],
      ['autoApproveThreshold', '0'],
    ];
    const insertAll = database.transaction(() => {
      for (const [key, value] of defaults) insertSetting.run(key, value);
    });
    insertAll();
  }
}

export function getSetting(key: string): string | null {
  const row = getDb().prepare('SELECT value FROM settings WHERE key = ?').get(key) as { value: string } | undefined;
  return row ? row.value : null;
}

export function setSetting(key: string, value: string): void {
  getDb().prepare(
    'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value'
  ).run(key, value);
}

export function listMinistries(): MinistryRow[] {
  return getDb().prepare('SELECT * FROM ministries ORDER BY sort_order ASC, id ASC').all() as MinistryRow[];
}

export function listSources(ministryId?: string): SourceRow[] {
  const rows = ministryId
    ? getDb().prepare('SELECT * FROM sources WHERE ministry_id = ? ORDER BY id ASC').all(ministryId)
    : getDb().prepare('SELECT * FROM sources ORDER BY ministry_id ASC, id ASC').all();
  return rows as SourceRow[];
}

export function listPreferences(): PreferenceRow[] {
  return getDb().prepare('SELECT * FROM preferences').all() as PreferenceRow[];
}

export function getPreference(ministryId: string): PreferenceRow | undefined {
  return getDb().prepare('SELECT * FROM preferences WHERE ministry_id = ?').get(ministryId) as PreferenceRow | undefined;
}

export function listItems(ministryId?: string, statuses?: string[]): ItemRow[] {
  let sql = 'SELECT * FROM items';
  const where: string[] = [];
  const params: string[] = [];
  if (ministryId) {
    where.push('ministry_id = ?');
    params.push(ministryId);
  }
  if (statuses && statuses.length > 0) {
    where.push(`status IN (${statuses.map(() => '?').join(', ')})`);
    params.push(...statuses);
  }
  if (where.length > 0) sql += ' WHERE ' + where.join(' AND ');
  sql += ' ORDER BY created_at DESC';
  return getDb().prepare(sql).all(...params) as ItemRow[];
}

export function getItem(id: string): ItemRow | undefined {
  return getDb().prepare('SELECT * FROM items WHERE id = ?').get(id) as ItemRow | undefined;
}

export function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function nowIso(): string {
  return new Date().toISOString();
}

export function todayDate(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function ensureDefaultPreferences(database: Database.Database = getDb()): void {
  const insert = database.prepare(
    `INSERT OR IGNORE INTO preferences (ministry_id, description, exclude_keywords, exclude_domains, daily_limit, schedule_cron)
     VALUES (?, ?, ?, ?, ?, ?)`
  );
  for (const p of DEFAULT_PREFERENCES) {
    insert.run(p.ministry_id, p.description, p.exclude_keywords, p.exclude_domains, p.daily_limit, p.schedule_cron);
  }
}
