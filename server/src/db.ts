import Database from 'better-sqlite3';
import fs from 'node:fs';
import path from 'node:path';
import type {
  AnnotationRow,
  ItemRow,
  LearningStats,
  MinistryRow,
  PreferenceRow,
  ReviewRating,
  ReviewRow,
  SourceRow,
} from './types';

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
  migrateTimestampFormat(db);
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
  return formatLocalIso(new Date());
}

export function formatLocalIso(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  const millis = String(date.getMilliseconds()).padStart(3, '0');
  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}.${millis}`;
}

export function todayDate(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const TIMESTAMP_MIGRATIONS: ReadonlyArray<{ table: string; columns: readonly string[] }> = [
  { table: 'items', columns: ['created_at', 'approved_at', 'archived_at', 'read_at'] },
  { table: 'reviews', columns: ['due_at', 'last_reviewed_at'] },
  { table: 'annotations', columns: ['created_at'] },
  { table: 'fetch_logs', columns: ['created_at'] },
  { table: 'reject_logs', columns: ['created_at'] },
  { table: 'scheduler_state', columns: ['last_run_at'] },
];

const UTC_ISO_TIMESTAMP_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:?\d{2})$/;

function convertStoredTimestamp(value: string): string | null {
  if (!UTC_ISO_TIMESTAMP_RE.test(value)) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return formatLocalIso(date);
}

export function migrateTimestampFormat(database: Database.Database): void {
  const migrate = database.transaction(() => {
    for (const { table, columns } of TIMESTAMP_MIGRATIONS) {
      const tableExists = database.prepare(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
      ).get(table);
      if (!tableExists) continue;
      for (const column of columns) {
        const rows = database.prepare(
          `SELECT rowid AS __rowid, ${column} AS __value FROM ${table} WHERE ${column} IS NOT NULL`
        ).all() as Array<{ __rowid: number; __value: string }>;
        const update = database.prepare(`UPDATE ${table} SET ${column} = ? WHERE rowid = ?`);
        for (const row of rows) {
          const converted = convertStoredTimestamp(row.__value);
          if (converted !== null && converted !== row.__value) {
            update.run(converted, row.__rowid);
          }
        }
      }
    }
  });
  migrate();
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

export const REVIEW_STAGES = [1, 3, 7, 15, 30] as const;

export function addDaysLocalIso(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return formatLocalIso(d);
}

export function getReview(itemId: string): ReviewRow | undefined {
  return getDb().prepare('SELECT * FROM reviews WHERE item_id = ?').get(itemId) as ReviewRow | undefined;
}

export function listReviews(): ReviewRow[] {
  return getDb().prepare('SELECT * FROM reviews ORDER BY due_at ASC').all() as ReviewRow[];
}

export function ensureReviewOnRead(itemId: string): ReviewRow {
  const db = getDb();
  const existing = getReview(itemId);
  if (existing) return existing;
  const review: ReviewRow = {
    item_id: itemId,
    stage: 0,
    due_at: addDaysLocalIso(1),
    interval_days: REVIEW_STAGES[0],
    ease: 2.5,
    review_count: 0,
    last_reviewed_at: null,
    status: 'reviewing',
  };
  db.prepare(
    `INSERT INTO reviews (item_id, stage, due_at, interval_days, ease, review_count, last_reviewed_at, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(review.item_id, review.stage, review.due_at, review.interval_days, review.ease, review.review_count, review.last_reviewed_at, review.status);
  return review;
}

export function listDueReviews(date: string): Array<{ review: ReviewRow; item: ItemRow }> {
  const rows = getDb().prepare(
    `SELECT r.item_id AS review_item_id, r.stage AS review_stage, r.due_at AS review_due_at,
            r.interval_days AS review_interval_days, r.ease AS review_ease,
            r.review_count AS review_review_count, r.last_reviewed_at AS review_last_reviewed_at,
            r.status AS review_status, i.*
     FROM reviews r JOIN items i ON i.id = r.item_id
     WHERE r.status = 'reviewing' AND date(r.due_at) <= ?
     ORDER BY r.due_at ASC, i.created_at DESC`
  ).all(date) as Array<Record<string, unknown>>;
  return rows.map((row) => ({ review: reviewFromRow(row), item: itemFromRow(row) }));
}

function reviewFromRow(row: Record<string, unknown>): ReviewRow {
  return {
    item_id: String(row.review_item_id),
    stage: Number(row.review_stage),
    due_at: String(row.review_due_at),
    interval_days: Number(row.review_interval_days),
    ease: Number(row.review_ease),
    review_count: Number(row.review_review_count),
    last_reviewed_at: row.review_last_reviewed_at == null ? null : String(row.review_last_reviewed_at),
    status: row.review_status as ReviewRow['status'],
  };
}

function itemFromRow(row: Record<string, unknown>): ItemRow {
  return {
    id: String(row.id),
    ministry_id: String(row.ministry_id),
    source_id: row.source_id == null ? null : String(row.source_id),
    content_type: String(row.content_type),
    title: String(row.title),
    summary: String(row.summary),
    full_text: String(row.full_text),
    source_url: row.source_url == null ? null : String(row.source_url),
    document_format: row.document_format == null ? null : String(row.document_format),
    file_name: row.file_name == null ? null : String(row.file_name),
    status: row.status as ItemRow['status'],
    quality_score: Number(row.quality_score),
    ai_reason: row.ai_reason == null ? null : String(row.ai_reason),
    redraft_count: Number(row.redraft_count),
    created_at: String(row.created_at),
    approved_at: row.approved_at == null ? null : String(row.approved_at),
    archived_at: row.archived_at == null ? null : String(row.archived_at),
    read_at: row.read_at == null ? null : String(row.read_at),
  };
}

export function applyReviewFeedback(itemId: string, rating: ReviewRating): { review: ReviewRow; item: ItemRow } {
  const db = getDb();
  const review = getReview(itemId);
  if (!review) throw new Error('该奏折尚无复习记录');
  const item = getItem(itemId);
  if (!item) throw new Error('奏折不存在');
  if (review.status === 'mastered') return { review, item };

  let nextStage: number;
  if (rating === 'forgot') nextStage = 0;
  else if (rating === 'hard') nextStage = Math.max(0, review.stage - 1);
  else if (rating === 'good') nextStage = Math.min(REVIEW_STAGES.length - 1, review.stage + 1);
  else nextStage = Math.min(REVIEW_STAGES.length - 1, review.stage + 2);

  const mastered = nextStage === REVIEW_STAGES.length - 1 && (rating === 'good' || rating === 'easy');
  const now = nowIso();
  db.prepare(
    `UPDATE reviews SET stage = ?, due_at = ?, interval_days = ?, review_count = review_count + 1,
     last_reviewed_at = ?, status = ? WHERE item_id = ?`
  ).run(nextStage, addDaysLocalIso(REVIEW_STAGES[nextStage]), REVIEW_STAGES[nextStage], now, mastered ? 'mastered' : 'reviewing', itemId);
  db.prepare('UPDATE items SET status = ? WHERE id = ?').run(mastered ? 'mastered' : 'reviewing', itemId);
  return { review: getReview(itemId)!, item: getItem(itemId)! };
}

export function listAnnotations(itemId: string): AnnotationRow[] {
  return getDb().prepare(
    'SELECT * FROM annotations WHERE item_id = ? ORDER BY created_at DESC, id DESC'
  ).all(itemId) as AnnotationRow[];
}

export function listAllAnnotations(): AnnotationRow[] {
  return getDb().prepare('SELECT * FROM annotations ORDER BY created_at DESC, id DESC').all() as AnnotationRow[];
}

export function insertAnnotation(itemId: string, text: string): AnnotationRow {
  const row: AnnotationRow = { id: generateId(), item_id: itemId, created_at: nowIso(), text };
  getDb().prepare('INSERT INTO annotations (id, item_id, created_at, text) VALUES (?, ?, ?, ?)')
    .run(row.id, row.item_id, row.created_at, row.text);
  return row;
}

export function deleteAnnotation(id: string): boolean {
  return getDb().prepare('DELETE FROM annotations WHERE id = ?').run(id).changes > 0;
}

export function learningStats(date: string): LearningStats {
  const db = getDb();
  const dueToday = (db.prepare(
    "SELECT COUNT(*) AS c FROM reviews WHERE status = 'reviewing' AND date(due_at) <= ?"
  ).get(date) as { c: number }).c;
  const completedToday = (db.prepare(
    'SELECT COUNT(*) AS c FROM reviews WHERE last_reviewed_at IS NOT NULL AND date(last_reviewed_at) = ?'
  ).get(date) as { c: number }).c;
  const dueRows = db.prepare(
    `SELECT i.ministry_id AS ministryId, COUNT(*) AS c FROM reviews r
     JOIN items i ON i.id = r.item_id
     WHERE r.status = 'reviewing' AND date(r.due_at) <= ?
     GROUP BY i.ministry_id`
  ).all(date) as Array<{ ministryId: string; c: number }>;
  const dueByMinistry: Record<string, number> = {};
  for (const row of dueRows) dueByMinistry[row.ministryId] = row.c;
  const weeklyCount = (db.prepare(
    "SELECT COUNT(*) AS c FROM reviews WHERE last_reviewed_at IS NOT NULL AND date(last_reviewed_at) >= date(?, '-6 days')"
  ).get(date) as { c: number }).c;
  const masteredCount = (db.prepare("SELECT COUNT(*) AS c FROM reviews WHERE status = 'mastered'").get() as { c: number }).c;
  const total = completedToday + dueToday;
  return {
    date,
    dueToday,
    completedToday,
    completionRate: total === 0 ? null : Math.round((completedToday / total) * 100),
    dueByMinistry,
    weeklyCount,
    masteredCount,
  };
}
