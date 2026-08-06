import { draftArticle } from './ai';
import { fetchSource } from './fetcher';
import { getInternalAiSettings } from './settings';
import {
  formatLocalIso,
  generateId,
  getDb,
  getPreference,
  getSetting,
  listMinistries,
  listPreferences,
  listSources,
  nowIso,
  todayDate,
} from './db';
import type {
  DraftArticle,
  ItemRow,
  MinistryFetchSummary,
  PreferenceRow,
  SourceRow,
} from './types';

interface FetchLogInput {
  date: string;
  ministryId: string;
  sourceId: string | null;
  status: string;
  itemCount: number;
  error: string | null;
}

const running = new Set<string>();

export function isRunning(ministryId: string): boolean {
  return running.has(ministryId);
}

export async function runMinistryFetch(ministryId: string): Promise<MinistryFetchSummary> {
  if (running.has(ministryId)) {
    throw new Error(`本部抓取正在运行：${ministryId}`);
  }
  running.add(ministryId);
  try {
    return await doRunMinistryFetch(ministryId);
  } finally {
    running.delete(ministryId);
  }
}

async function doRunMinistryFetch(ministryId: string): Promise<MinistryFetchSummary> {
  const db = getDb();
  const ministry = db.prepare('SELECT * FROM ministries WHERE id = ?').get(ministryId) as
    | { id: string; title: string; icon: string; color: string; sort_order: number }
    | undefined;
  if (!ministry) throw new Error(`六部不存在：${ministryId}`);

  const preference = getPreference(ministryId);
  const sources = listSources(ministryId).filter((s) => s.enabled === 1);
  const settings = getInternalAiSettings();
  const autoApproveThreshold = Number(getSetting('autoApproveThreshold') || '0');

  if (sources.length === 0) {
    markRan(ministryId);
    return { ministryId, ministryTitle: ministry.title, sources: [], newCandidates: 0, errors: [] };
  }

  const fetched: Array<{ article: DraftArticle; sourceId: string }> = [];
  const sourceResults: MinistryFetchSummary['sources'] = [];
  const errors: string[] = [];
  const today = todayDate();

  for (const source of sources) {
    const result = await fetchSource(source);
    sourceResults.push(result);
    if (!result.ok) {
      errors.push(`${source.name}: ${result.error}`);
      db.prepare(
        'UPDATE sources SET last_fetched_at = ?, last_status = ?, fail_streak = fail_streak + 1 WHERE id = ?'
      ).run(nowIso(), 'error', source.id);
      insertFetchLog({ date: today, ministryId, sourceId: source.id, status: 'error', itemCount: 0, error: result.error });
      continue;
    }

    db.prepare(
      'UPDATE sources SET last_fetched_at = ?, last_status = ?, fail_streak = 0 WHERE id = ?'
    ).run(nowIso(), 'ok', source.id);

    const accepted: Array<{ article: DraftArticle; sourceId: string }> = [];
    for (const article of result.articles) {
      if (isDuplicate(article, ministryId)) continue;
      if (isExcludedByPreference(article, preference)) continue;
      accepted.push({ article, sourceId: result.sourceId });
    }
    fetched.push(...accepted);
    insertFetchLog({ date: today, ministryId, sourceId: source.id, status: 'ok', itemCount: accepted.length, error: null });
  }

  let newCandidates = 0;
  if (fetched.length > 0) {
    const drafts = await Promise.all(
      fetched.map(async ({ article, sourceId }) => {
        const draft = await draftArticle(article, ministry, preference, settings);
        return { draft, sourceId };
      }),
    );
    drafts.sort((a, b) => b.draft.qualityScore - a.draft.qualityScore);

    const dailyLimit = clampDailyLimit(preference?.daily_limit);
    const todayCount = countItemsCreatedToday(ministryId);
    for (const { draft, sourceId } of drafts) {
      if (todayCount + newCandidates >= dailyLimit) break;
      const approved = autoApproveThreshold > 0 && draft.qualityScore >= autoApproveThreshold;
      insertCandidate(ministryId, draft, sourceId, approved, autoApproveThreshold);
      newCandidates++;
    }
  }

  if (newCandidates === 0 && sources.length > 0 && errors.length > 0 && errors.length === sources.length) {
    insertExceptionItem(ministryId, sources, errors);
    newCandidates++;
  }

  markRan(ministryId);
  return { ministryId, ministryTitle: ministry.title, sources: sourceResults, newCandidates, errors };
}

export async function runAllMinistries(): Promise<MinistryFetchSummary[]> {
  const ministries = listMinistries();
  const summaries: MinistryFetchSummary[] = [];
  for (const ministry of ministries) {
    summaries.push(await runMinistryFetch(ministry.id));
  }
  return summaries;
}

function insertCandidate(
  ministryId: string,
  draft: DraftArticle,
  sourceId: string,
  autoApproved: boolean,
  threshold: number,
): void {
  const db = getDb();
  const id = generateId();
  const now = nowIso();
  const item: ItemRow = {
    id,
    ministry_id: ministryId,
    source_id: sourceId,
    content_type: 'WEB_ARTICLE',
    title: draft.title || '无标题',
    summary: draft.summary || '',
    full_text: draft.fullText,
    source_url: draft.sourceUrl,
    document_format: null,
    file_name: null,
    status: autoApproved ? 'archived' : 'candidate',
    quality_score: draft.qualityScore,
    ai_reason: autoApproved
      ? `${draft.aiReason}（质量分 ${draft.qualityScore} ≥ 自动准奏阈值 ${threshold}）`
      : draft.aiReason,
    redraft_count: 0,
    created_at: now,
    approved_at: autoApproved ? now : null,
    archived_at: autoApproved ? now : null,
    read_at: null,
  };
  db.prepare(
    `INSERT INTO items (
      id, ministry_id, source_id, content_type, title, summary, full_text, source_url,
      document_format, file_name, status, quality_score, ai_reason, redraft_count,
      created_at, approved_at, archived_at, read_at
    ) VALUES (
      @id, @ministry_id, @source_id, @content_type, @title, @summary, @full_text, @source_url,
      @document_format, @file_name, @status, @quality_score, @ai_reason, @redraft_count,
      @created_at, @approved_at, @archived_at, @read_at
    )`
  ).run(item);
}

function insertExceptionItem(ministryId: string, sources: SourceRow[], errors: string[]): void {
  const db = getDb();
  const id = generateId();
  const now = nowIso();
  const sourceNames = sources.map((s) => s.name).join('、');
  const errorText = errors.join('；');
  db.prepare(
    `INSERT INTO items (
      id, ministry_id, source_id, content_type, title, summary, full_text, source_url,
      document_format, file_name, status, quality_score, ai_reason, redraft_count,
      created_at, approved_at, archived_at, read_at
    ) VALUES (?, ?, NULL, 'WEB_ARTICLE', ?, ?, ?, NULL, NULL, NULL, 'candidate', 0, ?, 0, ?, NULL, NULL, NULL)`
  ).run(
    id,
    ministryId,
    `[异常] ${sourceNames} 采集失败`,
    `全部信息源抓取失败，请检查源配置。`,
    errorText,
    `采集失败原因：${errorText}`,
    now,
  );
}

function insertFetchLog(log: FetchLogInput): void {
  getDb().prepare(
    'INSERT INTO fetch_logs (id, date, ministry_id, source_id, status, item_count, error, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
  ).run(generateId(), log.date, log.ministryId, log.sourceId, log.status, log.itemCount, log.error, nowIso());
}

function isDuplicate(article: DraftArticle, ministryId: string): boolean {
  const db = getDb();
  if (article.sourceUrl) {
    const found = db.prepare('SELECT id FROM items WHERE source_url = ? LIMIT 1').get(article.sourceUrl);
    if (found) return true;
    const rejected = db.prepare('SELECT id FROM reject_logs WHERE source_url = ? AND created_at >= ? LIMIT 1')
      .get(article.sourceUrl, daysAgoIso(7));
    if (rejected) return true;
  }

  const titles = db
    .prepare('SELECT title FROM items WHERE ministry_id = ? AND title IS NOT NULL AND title != ?')
    .all(ministryId, '') as { title: string }[];
  const normalized = normalizeTitle(article.title);
  if (!normalized) return false;
  for (const row of titles) {
    if (titleSimilarity(normalized, normalizeTitle(row.title)) >= 0.78) return true;
  }

  const rejectedTitles = db
    .prepare('SELECT title FROM reject_logs WHERE created_at >= ?')
    .all(daysAgoIso(7)) as { title: string }[];
  for (const row of rejectedTitles) {
    if (titleSimilarity(normalized, normalizeTitle(row.title)) >= 0.78) return true;
  }
  return false;
}

function isExcludedByPreference(article: DraftArticle, preference: PreferenceRow | undefined): boolean {
  if (!preference) return false;
  const keywords = parseStringArray(preference.exclude_keywords);
  const haystack = `${article.title} ${article.summary} ${article.fullText}`.toLowerCase();
  if (keywords.some((k) => k && haystack.includes(k.toLowerCase()))) return true;

  const domains = parseStringArray(preference.exclude_domains).map((d) => d.toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, ''));
  if (article.sourceUrl && domains.length > 0) {
    try {
      const host = new URL(article.sourceUrl).hostname.toLowerCase();
      if (domains.some((d) => host === d || host.endsWith(`.${d}`))) return true;
    } catch {
      // 忽略无法解析的 URL
    }
  }
  return false;
}

function countItemsCreatedToday(ministryId: string): number {
  const row = getDb().prepare(
    "SELECT COUNT(*) AS c FROM items WHERE ministry_id = ? AND status != 'rejected' AND date(created_at) = date('now', 'localtime')"
  ).get(ministryId) as { c: number };
  return row.c;
}

function clampDailyLimit(value: number | undefined): number {
  if (value === undefined || !Number.isFinite(value)) return 3;
  return Math.max(1, Math.min(10, value));
}

function parseStringArray(raw: string): string[] {
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map((x) => String(x).trim()).filter(Boolean) : [];
  } catch {
    return [];
  }
}

function normalizeTitle(title: string): string {
  return title
    .toLowerCase()
    .replace(/[\s\p{P}\p{S}]+/gu, '')
    .trim();
}

function titleSimilarity(a: string, b: string): number {
  if (!a || !b) return 0;
  if (a === b) return 1;
  const bigrams = (s: string): Set<string> => {
    const set = new Set<string>();
    for (let i = 0; i < s.length - 1; i++) set.add(s.slice(i, i + 2));
    return set;
  };
  const ga = bigrams(a);
  const gb = bigrams(b);
  let intersection = 0;
  for (const gram of ga) {
    if (gb.has(gram)) intersection++;
  }
  return (2 * intersection) / (ga.size + gb.size || 1);
}

function daysAgoIso(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return formatLocalIso(d);
}

function markRan(ministryId: string): void {
  const today = todayDate();
  getDb().prepare(
    `INSERT INTO scheduler_state (ministry_id, last_run_date, last_run_at)
     VALUES (?, ?, ?)
     ON CONFLICT(ministry_id) DO UPDATE SET last_run_date = excluded.last_run_date, last_run_at = excluded.last_run_at`
  ).run(ministryId, today, nowIso());
}

function ranToday(ministryId: string): boolean {
  const row = getDb().prepare('SELECT last_run_date FROM scheduler_state WHERE ministry_id = ?').get(ministryId) as
    | { last_run_date: string }
    | undefined;
  return row?.last_run_date === todayDate();
}

function parseCron(cron: string): { minute: number; hour: number; dom: string; month: string; dow: string } | null {
  let parts = cron.trim().split(/\s+/);
  if (parts.length === 2 && /^\d{1,2}:\d{2}$/.test(cron.trim())) {
    const [hour, minute] = cron.trim().split(':').map(Number);
    return { minute, hour, dom: '*', month: '*', dow: '*' };
  }
  if (parts.length === 6) parts = parts.slice(1);
  if (parts.length !== 5) return null;
  const [minute, hour, dom, month, dow] = parts;
  const m = Number(minute);
  const h = Number(hour);
  if (!Number.isInteger(m) || !Number.isInteger(h)) return null;
  return { minute: m, hour: h, dom, month, dow };
}

function cronFieldMatches(field: string, value: number): boolean {
  if (field === '*' || field === '?') return true;
  if (field.startsWith('*/')) {
    const step = Number(field.slice(2));
    return step > 0 && value % step === 0;
  }
  if (field.includes(',')) return field.split(',').some((part) => cronFieldMatches(part, value));
  if (field.includes('-')) {
    const [start, end] = field.split('-').map(Number);
    return value >= start && value <= end;
  }
  return Number(field) === value;
}

export function isCronDue(cron: string, date: Date): boolean {
  const parsed = parseCron(cron);
  if (!parsed) return false;
  return (
    date.getMinutes() === parsed.minute &&
    date.getHours() === parsed.hour &&
    cronFieldMatches(parsed.dom, date.getDate()) &&
    cronFieldMatches(parsed.month, date.getMonth() + 1) &&
    cronFieldMatches(parsed.dow, date.getDay())
  );
}

function scheduledTimeToday(cron: string): Date | null {
  const parsed = parseCron(cron);
  if (!parsed) return null;
  const d = new Date();
  d.setHours(parsed.hour, parsed.minute, 0, 0);
  return d;
}

async function catchUpMissed(): Promise<void> {
  const now = new Date();
  for (const preference of listPreferences()) {
    if (ranToday(preference.ministry_id)) continue;
    const scheduled = scheduledTimeToday(preference.schedule_cron);
    if (scheduled && now >= scheduled) {
      try {
        await runMinistryFetch(preference.ministry_id);
      } catch (error) {
        console.error(`补抓失败 ${preference.ministry_id}:`, error);
      }
    }
  }
}

function tick(): void {
  const now = new Date();
  for (const preference of listPreferences()) {
    if (ranToday(preference.ministry_id)) continue;
    if (isCronDue(preference.schedule_cron, now)) {
      runMinistryFetch(preference.ministry_id).catch((error) => {
        console.error(`定时抓取失败 ${preference.ministry_id}:`, error);
      });
    }
  }
}

let intervalHandle: NodeJS.Timeout | null = null;

export function startScheduler(): void {
  if (intervalHandle) return;
  catchUpMissed().catch((error) => console.error('补抓检查失败:', error));
  intervalHandle = setInterval(tick, 30_000);
}

export function stopScheduler(): void {
  if (intervalHandle) {
    clearInterval(intervalHandle);
    intervalHandle = null;
  }
}
