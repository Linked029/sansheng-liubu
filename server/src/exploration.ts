// Exploration module: search_directions, search_terms, exploration_items
// ─── Schema, CRUD, dedup, and archive-suggestion logic.
import { generateId, getDb, nowIso } from "./db";
import type {
  ExplorationItemRow,
  SearchDirectionRow,
  SearchTermRow,
} from "./types";

// ─── Schema migration ──────────────────────────────────────────────

export function ensureExplorationSchema(): void {
  const db = getDb();
  db.exec(`
    CREATE TABLE IF NOT EXISTS search_directions (
      id TEXT PRIMARY KEY,
      ministry_id TEXT NOT NULL REFERENCES ministries(id) ON DELETE CASCADE,
      direction_text TEXT NOT NULL,
      created_at TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT "active" CHECK (status IN ('active', 'fulfilled'))
    );

    CREATE TABLE IF NOT EXISTS search_terms (
      id TEXT PRIMARY KEY,
      direction_id TEXT NOT NULL REFERENCES search_directions(id) ON DELETE CASCADE,
      term TEXT NOT NULL,
      created_at TEXT NOT NULL,
      last_searched_at TEXT
    );

    CREATE TABLE IF NOT EXISTS exploration_items (
      id TEXT PRIMARY KEY,
      ministry_id TEXT NOT NULL REFERENCES ministries(id),
      direction_id TEXT NOT NULL REFERENCES search_directions(id) ON DELETE CASCADE,
      search_term_id TEXT REFERENCES search_terms(id) ON DELETE SET NULL,
      title TEXT NOT NULL,
      summary TEXT NOT NULL DEFAULT "",
      full_text TEXT NOT NULL DEFAULT "",
      source_url TEXT,
      source_name TEXT NOT NULL DEFAULT "",
      status TEXT NOT NULL DEFAULT "new" CHECK (status IN ('new', 'archived', 'dismissed')),
      created_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_exploration_items_status ON exploration_items(status);
    CREATE INDEX IF NOT EXISTS idx_exploration_items_ministry ON exploration_items(ministry_id);
    CREATE INDEX IF NOT EXISTS idx_search_terms_direction ON search_terms(direction_id);
  `);
}

// ─── Search Directions ─────────────────────────────────────────────

export function listSearchDirections(ministryId?: string): SearchDirectionRow[] {
  const db = getDb();
  const sql = ministryId
    ? "SELECT * FROM search_directions WHERE ministry_id = ? ORDER BY created_at DESC"
    : "SELECT * FROM search_directions ORDER BY created_at DESC";
  return (ministryId ? db.prepare(sql).all(ministryId) : db.prepare(sql).all()) as SearchDirectionRow[];
}

export function getSearchDirection(id: string): SearchDirectionRow | undefined {
  return getDb().prepare("SELECT * FROM search_directions WHERE id = ?").get(id) as SearchDirectionRow | undefined;
}

export function insertSearchDirection(ministryId: string, directionText: string): SearchDirectionRow {
  const db = getDb();
  const row: SearchDirectionRow = {
    id: generateId(),
    ministry_id: ministryId,
    direction_text: directionText,
    created_at: nowIso(),
    status: "active",
  };
  db.prepare(
    "INSERT INTO search_directions (id, ministry_id, direction_text, created_at, status) VALUES (?, ?, ?, ?, ?)"
  ).run(row.id, row.ministry_id, row.direction_text, row.created_at, row.status);
  return row;
}

export function fulfillSearchDirection(id: string): void {
  getDb().prepare("UPDATE search_directions SET status = 'fulfilled' WHERE id = ?").run(id);
}

// ─── Search Terms ──────────────────────────────────────────────────

export function insertSearchTerm(directionId: string, term: string): SearchTermRow {
  const db = getDb();
  const row: SearchTermRow = {
    id: generateId(),
    direction_id: directionId,
    term,
    created_at: nowIso(),
    last_searched_at: null,
  };
  db.prepare(
    "INSERT INTO search_terms (id, direction_id, term, created_at, last_searched_at) VALUES (?, ?, ?, ?, ?)"
  ).run(row.id, row.direction_id, row.term, row.created_at, row.last_searched_at);
  return row;
}

export function listSearchTerms(directionId: string): SearchTermRow[] {
  return getDb().prepare(
    "SELECT * FROM search_terms WHERE direction_id = ? ORDER BY created_at ASC"
  ).all(directionId) as SearchTermRow[];
}

export function touchSearchTerm(id: string): void {
  getDb().prepare("UPDATE search_terms SET last_searched_at = ? WHERE id = ?").run(nowIso(), id);
}

// ─── Exploration Items ─────────────────────────────────────────────

export function insertExplorationItem(item: ExplorationItemRow): void {
  getDb().prepare(
    `INSERT INTO exploration_items
       (id, ministry_id, direction_id, search_term_id, title, summary, full_text, source_url, source_name, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(
    item.id, item.ministry_id, item.direction_id, item.search_term_id,
    item.title, item.summary, item.full_text, item.source_url, item.source_name,
    item.status, item.created_at
  );
}

export function listExplorationItems(
  ministryId?: string,
  statuses?: string[]
): ExplorationItemRow[] {
  const db = getDb();
  let sql = "SELECT * FROM exploration_items";
  const where: string[] = [];
  const params: string[] = [];
  if (ministryId) {
    where.push("ministry_id = ?");
    params.push(ministryId);
  }
  if (statuses && statuses.length > 0) {
    where.push("status IN (" + statuses.map(() => "?").join(", ") + ")");
    params.push(...statuses);
  }
  if (where.length > 0) sql += " WHERE " + where.join(" AND ");
  sql += " ORDER BY created_at DESC";
  return db.prepare(sql).all(...params) as ExplorationItemRow[];
}

export function archiveExplorationItem(id: string): boolean {
  return getDb().prepare(
    "UPDATE exploration_items SET status = 'archived' WHERE id = ? AND status = 'new'"
  ).run(id).changes > 0;
}

export function dismissExplorationItem(id: string): boolean {
  return getDb().prepare(
    "UPDATE exploration_items SET status = 'dismissed' WHERE id = ? AND status = 'new'"
  ).run(id).changes > 0;
}

// ─── Dedup helpers ─────────────────────────────────────────────────

export function isExplorationUrlDuplicate(sourceUrl: string): boolean {
  if (!sourceUrl) return false;
  const db = getDb();
  if (db.prepare("SELECT id FROM items WHERE source_url = ? LIMIT 1").get(sourceUrl)) return true;
  if (db.prepare("SELECT id FROM exploration_items WHERE source_url = ? LIMIT 1").get(sourceUrl)) return true;
  return false;
}

export function countArchivesFromSource(sourceName: string): number {
  const row = getDb().prepare(
    "SELECT COUNT(*) AS c FROM exploration_items WHERE source_name = ? AND status = 'archived'"
  ).get(sourceName) as { c: number };
  return row.c;
}