import express, { type NextFunction, type Request, type Response } from 'express';
import cors from 'cors';
import { classifyManual } from './ai';
import {
  DEFAULT_MINISTRY_IDS,
  generateId,
  getDb,
  getItem,
  getPreference,
  getSetting,
  ensureDefaultPreferences,
  listItems,
  listMinistries,
  listSources,
  nowIso,
  setSetting,
  todayDate,
} from './db';
import { fetchSource } from './fetcher';
import { runAllMinistries, runMinistryFetch } from './scheduler';
import type {
  AiEngineSettings,
  FetchLogRow,
  ItemRow,
  MinistryRow,
  PreferenceRow,
  RejectLogRow,
  SourceRow,
} from './types';

export function createApp(): express.Express {
  const app = express();
  app.use(cors());
  app.use(express.json({ limit: '50mb' }));

  app.get('/api/health', (_req, res) => {
    res.json({ ok: true, name: '三省六部本地服务', time: new Date().toISOString() });
  });

  app.get('/api/dashboard', (_req, res, next) => {
    try {
      const db = getDb();
      const today = todayDate();
      const pendingCount = (db.prepare(
        "SELECT COUNT(*) AS c FROM items WHERE status IN ('candidate', 'pending')"
      ).get() as { c: number }).c;
      const archivedToday = (db.prepare(
        "SELECT COUNT(*) AS c FROM items WHERE status IN ('archived','read','reviewing','mastered') AND date(archived_at) = ?"
      ).get(today) as { c: number }).c;
      const createdToday = (db.prepare(
        "SELECT COUNT(*) AS c FROM items WHERE date(created_at) = ?"
      ).get(today) as { c: number }).c;
      const fetchedOk = (db.prepare(
        "SELECT COUNT(*) AS c FROM fetch_logs WHERE date = ? AND status = 'ok'"
      ).get(today) as { c: number }).c;
      const fetchedError = (db.prepare(
        "SELECT COUNT(*) AS c FROM fetch_logs WHERE date = ? AND status = 'error'"
      ).get(today) as { c: number }).c;

      const ministries = listMinistries().map((m) => ({
        ...ministryJson(m),
        itemCount: countBy(m.id, "SELECT COUNT(*) AS c FROM items WHERE ministry_id = ?"),
        archivedCount: countBy(m.id, "SELECT COUNT(*) AS c FROM items WHERE ministry_id = ? AND status IN ('archived','read','reviewing','mastered')"),
        pendingCount: countBy(m.id, "SELECT COUNT(*) AS c FROM items WHERE ministry_id = ? AND status IN ('candidate','pending')"),
        sourceCount: countBy(m.id, 'SELECT COUNT(*) AS c FROM sources WHERE ministry_id = ?'),
      }));

      const recentFetchLogs = db.prepare(
        `SELECT f.*, m.title AS ministry_title FROM fetch_logs f
         LEFT JOIN ministries m ON m.id = f.ministry_id
         ORDER BY f.created_at DESC LIMIT 20`
      ).all() as (FetchLogRow & { ministry_title: string })[];

      res.json({
        today: {
          pending: pendingCount,
          archivedToday,
          createdToday,
          fetchedOk,
          fetchedError,
        },
        ministries,
        recentFetchLogs: recentFetchLogs.map((row) => ({
          ...fetchLogJson(row),
          ministryTitle: row.ministry_title,
        })),
      });
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/ministries', (_req, res, next) => {
    try {
      res.json(listMinistries().map(ministryJson));
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/ministries', (req, res, next) => {
    try {
      const db = getDb();
      const title = String(req.body.title || '').trim();
      if (!title) {
        res.status(400).json({ error: 'title 必填' });
        return;
      }
      const id = typeof req.body.id === 'string' && req.body.id.trim()
        ? req.body.id.trim()
        : `custom-${Date.now()}`;
      if (db.prepare('SELECT id FROM ministries WHERE id = ?').get(id)) {
        res.status(400).json({ error: '主题 ID 已存在' });
        return;
      }
      const maxOrder = (db.prepare('SELECT COALESCE(MAX(sort_order), -1) AS o FROM ministries').get() as { o: number }).o;
      db.prepare('INSERT INTO ministries (id, title, icon, color, sort_order) VALUES (?, ?, ?, ?, ?)').run(
        id,
        title,
        req.body.icon || 'folder-spark',
        req.body.color || '#8B7D6B',
        maxOrder + 1,
      );
      db.prepare(
        `INSERT INTO preferences (ministry_id, description, exclude_keywords, exclude_domains, daily_limit, schedule_cron)
         VALUES (?, '', '[]', '[]', 3, '0 8 * * *')`
      ).run(id);
      const row = db.prepare('SELECT * FROM ministries WHERE id = ?').get(id) as MinistryRow;
      res.status(201).json(ministryJson(row));
    } catch (error) {
      next(error);
    }
  });

  app.put('/api/ministries/:id', (req, res, next) => {
    try {
      const db = getDb();
      const existing = db.prepare('SELECT * FROM ministries WHERE id = ?').get(req.params.id) as MinistryRow | undefined;
      if (!existing) {
        res.status(404).json({ error: '六部不存在' });
        return;
      }
      const title = typeof req.body.title === 'string' ? req.body.title : existing.title;
      const icon = typeof req.body.icon === 'string' ? req.body.icon : existing.icon;
      const color = typeof req.body.color === 'string' ? req.body.color : existing.color;
      db.prepare('UPDATE ministries SET title = ?, icon = ?, color = ? WHERE id = ?').run(title, icon, color, existing.id);
      res.json(ministryJson({ ...existing, title, icon, color }));
    } catch (error) {
      next(error);
    }
  });

  app.delete('/api/ministries/:id', (req, res, next) => {
    try {
      const db = getDb();
      const id = req.params.id;
      if ((DEFAULT_MINISTRY_IDS as readonly string[]).includes(id)) {
        res.status(400).json({ error: '六部默认频道不可删除' });
        return;
      }
      const itemCount = countBy(id, 'SELECT COUNT(*) AS c FROM items WHERE ministry_id = ?');
      if (itemCount > 0) {
        res.status(400).json({ error: '该主题下仍有归档内容，请先迁移或删除条目' });
        return;
      }
      db.prepare('DELETE FROM ministries WHERE id = ?').run(id);
      res.json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/ministries/:id/sources', (req, res, next) => {
    try {
      res.json(listSources(req.params.id).map(sourceJson));
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/ministries/:id/sources', (req, res, next) => {
    try {
      const db = getDb();
      const ministry = db.prepare('SELECT id FROM ministries WHERE id = ?').get(req.params.id);
      if (!ministry) {
        res.status(404).json({ error: '六部不存在' });
        return;
      }
      const name = String(req.body.name || '').trim();
      const kind = String(req.body.kind || 'feed');
      const location = String(req.body.location || '').trim();
      if (!name || !['feed', 'url', 'manual'].includes(kind)) {
        res.status(400).json({ error: 'name 必填，kind 只能是 feed/url/manual' });
        return;
      }
      const id = generateId();
      db.prepare(
        `INSERT INTO sources (id, ministry_id, name, kind, location, enabled, fail_streak)
         VALUES (?, ?, ?, ?, ?, ?, 0)`
      ).run(id, req.params.id, name, kind, location, req.body.enabled === false ? 0 : 1);
      const row = db.prepare('SELECT * FROM sources WHERE id = ?').get(id) as SourceRow;
      res.status(201).json(sourceJson(row));
    } catch (error) {
      next(error);
    }
  });

  app.put('/api/sources/:id', (req, res, next) => {
    try {
      const db = getDb();
      const existing = db.prepare('SELECT * FROM sources WHERE id = ?').get(req.params.id) as SourceRow | undefined;
      if (!existing) {
        res.status(404).json({ error: '信息源不存在' });
        return;
      }
      const nextRow = {
        ...existing,
        name: typeof req.body.name === 'string' ? req.body.name.trim() : existing.name,
        kind: ['feed', 'url', 'manual'].includes(req.body.kind) ? req.body.kind : existing.kind,
        location: typeof req.body.location === 'string' ? req.body.location.trim() : existing.location,
        enabled: typeof req.body.enabled === 'boolean' ? (req.body.enabled ? 1 : 0) : existing.enabled,
      };
      db.prepare('UPDATE sources SET name = ?, kind = ?, location = ?, enabled = ? WHERE id = ?').run(
        nextRow.name,
        nextRow.kind,
        nextRow.location,
        nextRow.enabled,
        existing.id,
      );
      res.json(sourceJson({ ...nextRow, id: existing.id }));
    } catch (error) {
      next(error);
    }
  });

  app.delete('/api/sources/:id', (req, res, next) => {
    try {
      const db = getDb();
      const result = db.prepare('DELETE FROM sources WHERE id = ?').run(req.params.id);
      if (result.changes === 0) {
        res.status(404).json({ error: '信息源不存在' });
        return;
      }
      res.json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/sources/:id/test', async (req, res, next) => {
    try {
      const db = getDb();
      const source = db.prepare('SELECT * FROM sources WHERE id = ?').get(req.params.id) as SourceRow | undefined;
      if (!source) {
        res.status(404).json({ error: '信息源不存在' });
        return;
      }
      const result = await fetchSource(source);
      res.json({
        ok: result.ok,
        error: result.error,
        articleCount: result.articles.length,
        preview: result.articles.slice(0, 5).map((a) => ({ title: a.title, summary: a.summary })),
      });
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/preferences/:ministryId', (req, res, next) => {
    try {
      const pref = getPreference(req.params.ministryId);
      if (!pref) {
        res.status(404).json({ error: '偏好卡不存在' });
        return;
      }
      res.json(preferenceJson(pref));
    } catch (error) {
      next(error);
    }
  });

  app.put('/api/preferences/:ministryId', (req, res, next) => {
    try {
      const db = getDb();
      const existing = getPreference(req.params.ministryId);
      if (!existing) {
        res.status(404).json({ error: '偏好卡不存在' });
        return;
      }
      const description = typeof req.body.description === 'string' ? req.body.description : existing.description;
      const excludeKeywords = Array.isArray(req.body.excludeKeywords)
        ? JSON.stringify(req.body.excludeKeywords)
        : existing.exclude_keywords;
      const excludeDomains = Array.isArray(req.body.excludeDomains)
        ? JSON.stringify(req.body.excludeDomains)
        : existing.exclude_domains;
      const dailyLimit = Number.isInteger(req.body.dailyLimit) ? Math.max(1, Math.min(10, req.body.dailyLimit)) : existing.daily_limit;
      const scheduleCron = typeof req.body.scheduleCron === 'string' ? req.body.scheduleCron : existing.schedule_cron;
      db.prepare(
        `UPDATE preferences SET description = ?, exclude_keywords = ?, exclude_domains = ?, daily_limit = ?, schedule_cron = ?
         WHERE ministry_id = ?`
      ).run(description, excludeKeywords, excludeDomains, dailyLimit, scheduleCron, existing.ministry_id);
      res.json({
        ministryId: existing.ministry_id,
        description,
        excludeKeywords: safeParseArray(excludeKeywords),
        excludeDomains: safeParseArray(excludeDomains),
        dailyLimit,
        scheduleCron,
      });
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/items', (req, res, next) => {
    try {
      const ministryId = typeof req.query.ministryId === 'string' ? req.query.ministryId : undefined;
      const statusRaw = typeof req.query.status === 'string' ? req.query.status : undefined;
      const statuses = statusRaw ? statusRaw.split(',').map((s) => s.trim()).filter(Boolean) : undefined;
      res.json(listItems(ministryId, statuses).map(itemJson));
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/items/:id', (req, res, next) => {
    try {
      const item = getItem(req.params.id);
      if (!item) {
        res.status(404).json({ error: '奏折不存在' });
        return;
      }
      res.json(itemJson(item));
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/items', (req, res, next) => {
    try {
      const db = getDb();
      const ministry = db.prepare('SELECT id FROM ministries WHERE id = ?').get(req.body.ministryId);
      if (!ministry) {
        res.status(400).json({ error: 'ministryId 无效' });
        return;
      }
      const id = generateId();
      const now = nowIso();
      const item: ItemRow = {
        id,
        ministry_id: req.body.ministryId,
        source_id: req.body.sourceId || null,
        content_type: req.body.contentType || 'WEB_ARTICLE',
        title: String(req.body.title || '').trim(),
        summary: String(req.body.summary || '').trim(),
        full_text: String(req.body.fullText || ''),
        source_url: req.body.sourceUrl || null,
        document_format: req.body.documentFormat || null,
        file_name: req.body.fileName || null,
        status: 'candidate',
        quality_score: Number.isFinite(req.body.qualityScore) ? req.body.qualityScore : 70,
        ai_reason: req.body.aiReason || '手动录入，待门下省批阅。',
        redraft_count: 0,
        created_at: now,
        approved_at: null,
        archived_at: null,
        read_at: null,
      };
      if (!item.title) {
        res.status(400).json({ error: 'title 必填' });
        return;
      }
      insertItem(item);
      res.status(201).json(itemJson(getItem(id)!));
    } catch (error) {
      next(error);
    }
  });

  app.put('/api/items/:id', (req, res, next) => {
    try {
      const db = getDb();
      const existing = getItem(req.params.id);
      if (!existing) {
        res.status(404).json({ error: '奏折不存在' });
        return;
      }
      const fields: Partial<ItemRow> = {};
      if (typeof req.body.title === 'string') fields.title = req.body.title.trim();
      if (typeof req.body.summary === 'string') fields.summary = req.body.summary.trim();
      if (typeof req.body.fullText === 'string') fields.full_text = req.body.fullText;
      if (typeof req.body.sourceUrl === 'string') fields.source_url = req.body.sourceUrl || null;
      if (typeof req.body.contentType === 'string') fields.content_type = req.body.contentType;
      if (typeof req.body.documentFormat === 'string') fields.document_format = req.body.documentFormat || null;
      if (typeof req.body.fileName === 'string') fields.file_name = req.body.fileName || null;
      if (typeof req.body.ministryId === 'string') {
        const ministry = db.prepare('SELECT id FROM ministries WHERE id = ?').get(req.body.ministryId);
        if (!ministry) {
          res.status(400).json({ error: 'ministryId 无效' });
          return;
        }
        fields.ministry_id = req.body.ministryId;
      }
      if (Object.keys(fields).length === 0) {
        res.status(400).json({ error: '没有可更新的字段' });
        return;
      }
      const setClause = Object.keys(fields).map((key) => `${key} = ?`).join(', ');
      db.prepare(`UPDATE items SET ${setClause} WHERE id = ?`).run(...Object.values(fields), existing.id);
      res.json(itemJson(getItem(existing.id)!));
    } catch (error) {
      next(error);
    }
  });

  app.delete('/api/items/:id', (req, res, next) => {
    try {
      const db = getDb();
      const result = db.prepare('DELETE FROM items WHERE id = ?').run(req.params.id);
      if (result.changes === 0) {
        res.status(404).json({ error: '奏折不存在' });
        return;
      }
      res.json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/items/:id/approve', (req, res, next) => {
    try {
      const db = getDb();
      const item = getItem(req.params.id);
      if (!item) {
        res.status(404).json({ error: '奏折不存在' });
        return;
      }
      if (item.status !== 'candidate' && item.status !== 'pending') {
        res.status(400).json({ error: '只有待批奏折可以准奏' });
        return;
      }
      const now = nowIso();
      db.prepare("UPDATE items SET status = 'archived', approved_at = ?, archived_at = ? WHERE id = ?").run(now, now, item.id);
      res.json(itemJson(getItem(item.id)!));
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/items/:id/reject', (req, res, next) => {
    try {
      const db = getDb();
      const item = getItem(req.params.id);
      if (!item) {
        res.status(404).json({ error: '奏折不存在' });
        return;
      }
      if (item.status !== 'candidate' && item.status !== 'pending') {
        res.status(400).json({ error: '只有待批奏折可以驳回' });
        return;
      }
      const reason = typeof req.body.reason === 'string' ? req.body.reason.trim() : '';
      db.prepare(
        'INSERT INTO reject_logs (id, item_id, source_id, reason, title, source_url, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
      ).run(generateId(), item.id, item.source_id, reason || null, item.title, item.source_url, nowIso());
      db.prepare('DELETE FROM items WHERE id = ?').run(item.id);
      res.json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/items/:id/redraft', (req, res, next) => {
    try {
      const db = getDb();
      const item = getItem(req.params.id);
      if (!item) {
        res.status(404).json({ error: '奏折不存在' });
        return;
      }
      if (item.status !== 'candidate' && item.status !== 'pending') {
        res.status(400).json({ error: '只有待批奏折可以打回重拟' });
        return;
      }
      const reason = typeof req.body.reason === 'string' ? req.body.reason.trim() : '';
      const aiReason = reason ? `打回重拟：${reason}` : '打回重拟';
      db.prepare(
        "UPDATE items SET status = 'pending', redraft_count = redraft_count + 1, ai_reason = ?, approved_at = NULL, archived_at = NULL WHERE id = ?"
      ).run(aiReason, item.id);
      res.json(itemJson(getItem(item.id)!));
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/scheduler/run', async (req, res, next) => {
    try {
      const ministryId = typeof req.body.ministryId === 'string' ? req.body.ministryId : undefined;
      if (ministryId) {
        const summary = await runMinistryFetch(ministryId);
        res.json({ summaries: [summary] });
      } else {
        const summaries = await runAllMinistries();
        res.json({ summaries });
      }
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/fetch-logs', (req, res, next) => {
    try {
      const db = getDb();
      const date = typeof req.query.date === 'string' ? req.query.date : undefined;
      const rows = date
        ? db.prepare(
            `SELECT f.*, m.title AS ministry_title FROM fetch_logs f
             LEFT JOIN ministries m ON m.id = f.ministry_id
             WHERE f.date = ? ORDER BY f.created_at DESC LIMIT 200`
          ).all(date)
        : db.prepare(
            `SELECT f.*, m.title AS ministry_title FROM fetch_logs f
             LEFT JOIN ministries m ON m.id = f.ministry_id
             ORDER BY f.created_at DESC LIMIT 200`
          ).all();
      res.json((rows as (FetchLogRow & { ministry_title: string })[]).map((row) => ({
        ...fetchLogJson(row),
        ministryTitle: row.ministry_title,
      })));
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/reject-logs', (_req, res, next) => {
    try {
      const db = getDb();
      const rows = db.prepare('SELECT * FROM reject_logs ORDER BY created_at DESC LIMIT 200').all() as RejectLogRow[];
      res.json(rows.map(rejectLogJson));
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/settings', (_req, res, next) => {
    try {
      res.json(readAppSettings());
    } catch (error) {
      next(error);
    }
  });

  app.put('/api/settings/ai', (req, res, next) => {
    try {
      const current = readAppSettings().ai;
      const nextAi: AiEngineSettings = {
        engineType: req.body.engineType || current.engineType,
        baseUrl: typeof req.body.baseUrl === 'string' ? req.body.baseUrl : current.baseUrl,
        modelName: typeof req.body.modelName === 'string' ? req.body.modelName : current.modelName,
        apiKey: typeof req.body.apiKey === 'string' ? req.body.apiKey : current.apiKey,
      };
      setSetting('ai', JSON.stringify(nextAi));
      res.json(nextAi);
    } catch (error) {
      next(error);
    }
  });

  app.put('/api/settings/ai-presets', (req, res, next) => {
    try {
      const presets = Array.isArray(req.body.presets) ? req.body.presets : [];
      setSetting('aiPresets', JSON.stringify(presets));
      res.json({ presets });
    } catch (error) {
      next(error);
    }
  });

  app.put('/api/settings/approval', (req, res, next) => {
    try {
      const threshold = Math.max(0, Math.min(100, Number(req.body.autoApproveThreshold || 0)));
      setSetting('autoApproveThreshold', String(threshold));
      res.json({ autoApproveThreshold: threshold });
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/ai/classify', async (req, res, next) => {
    try {
      const rawText = String(req.body.rawText || '');
      if (!rawText.trim()) {
        res.status(400).json({ error: 'rawText 必填' });
        return;
      }
      const settings = readAppSettings().ai;
      const result = await classifyManual(rawText, listMinistries(), req.body.sourceUrl || undefined, settings);
      res.json(result);
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/export/json', (_req, res, next) => {
    try {
      const payload = {
        version: 'v3',
        exportedAt: new Date().toISOString(),
        ministries: listMinistries().map(ministryJson),
        sources: listSources().map(sourceJson),
        preferences: listPreferences().map(preferenceJson),
        items: listItems().map(itemJson),
        settings: readAppSettings(),
      };
      res.type('application/json').send(JSON.stringify(payload, null, 2));
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/import/json', (req, res, next) => {
    try {
      const data = req.body.data || req.body;
      if (!data || typeof data !== 'object') {
        res.status(400).json({ error: '导入数据格式无效' });
        return;
      }
      importData(data);
      res.json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/export/markdown', (_req, res, next) => {
    try {
      const ministries = new Map(listMinistries().map((m) => [m.id, m.title]));
      const items = listItems();
      let output = '';
      for (const item of items) {
        const ministryTitle = ministries.get(item.ministry_id) || '未分类';
        const date = new Date(item.created_at).toISOString().slice(0, 10);
        output += [
          '---',
          `title: "${item.title.replace(/"/g, '\\"')}"`,
          `topic: "${ministryTitle}"`,
          `type: ${item.content_type}`,
          `date: ${date}`,
          item.source_url ? `source: ${item.source_url}` : '',
          item.file_name ? `file: ${item.file_name}` : '',
          `id: ${item.id}`,
          '---',
          '',
          item.summary ? `> ${item.summary}\n` : '',
          item.full_text ? `${item.full_text}\n` : '',
          '---',
          '',
        ].filter((line) => line !== '').join('\n');
      }
      res.type('text/markdown; charset=utf-8').send(output);
    } catch (error) {
      next(error);
    }
  });

  app.use((error: unknown, _req: Request, res: Response, _next: NextFunction) => {
    console.error('API error:', error);
    res.status(500).json({ error: error instanceof Error ? error.message : '服务器内部错误' });
  });

  return app;
}

function readAppSettings(): {
  ai: AiEngineSettings;
  aiPresets: { name: string; engineType: string; baseUrl: string; modelName: string; apiKey: string }[];
  autoApproveThreshold: number;
} {
  const ai = safeParseObject(getSetting('ai')) as Partial<AiEngineSettings>;
  const presets = safeParseArray(getSetting('aiPresets')) as unknown as {
    name: string;
    engineType: string;
    baseUrl: string;
    modelName: string;
    apiKey: string;
  }[];
  const threshold = Number(getSetting('autoApproveThreshold') || '0');
  return {
    ai: {
      engineType: ai.engineType || 'OPENAI_COMPATIBLE',
      baseUrl: ai.baseUrl || '',
      modelName: ai.modelName || '',
      apiKey: ai.apiKey || '',
    },
    aiPresets: presets,
    autoApproveThreshold: Number.isFinite(threshold) ? threshold : 0,
  };
}

export function importData(data: Record<string, unknown>): void {
  const db = getDb();
  const insertAll = db.transaction(() => {
    const ministries = Array.isArray(data.ministries) ? data.ministries : Array.isArray(data.topics) ? data.topics : [];
    const items = Array.isArray(data.items) ? data.items : [];
    const sources = Array.isArray(data.sources) ? data.sources : [];
    const preferences = Array.isArray(data.preferences) ? data.preferences : [];

    db.prepare('DELETE FROM items').run();
    db.prepare('DELETE FROM sources').run();
    db.prepare('DELETE FROM preferences').run();
    db.prepare('DELETE FROM reject_logs').run();
    db.prepare('DELETE FROM fetch_logs').run();
    ensureDefaultPreferences();

    const upsertMinistry = db.prepare(
      `INSERT INTO ministries (id, title, icon, color, sort_order) VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET title = excluded.title, icon = excluded.icon, color = excluded.color, sort_order = excluded.sort_order`
    );
    for (const raw of ministries) {
      const m = raw as Record<string, unknown>;
      upsertMinistry.run(
        String(m.id),
        String(m.title || '未命名主题'),
        String(m.icon || m.iconName || 'folder-spark'),
        String(m.color || m.iconColor || '#8B7D6B'),
        Number(m.order ?? m.sort_order ?? 0),
      );
    }

    const upsertPreference = db.prepare(
      `INSERT INTO preferences (ministry_id, description, exclude_keywords, exclude_domains, daily_limit, schedule_cron)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT(ministry_id) DO UPDATE SET description = excluded.description`
    );
    for (const raw of preferences) {
      const p = raw as Record<string, unknown>;
      upsertPreference.run(
        String(p.ministryId || p.ministry_id),
        String(p.description || ''),
        JSON.stringify(p.excludeKeywords ?? p.exclude_keywords ?? []),
        JSON.stringify(p.excludeDomains ?? p.exclude_domains ?? []),
        Number(p.dailyLimit ?? p.daily_limit ?? 3),
        String(p.scheduleCron ?? p.schedule_cron ?? '0 8 * * *'),
      );
    }

    const insertSource = db.prepare(
      `INSERT INTO sources (id, ministry_id, name, kind, location, enabled, last_fetched_at, last_status, fail_streak)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
    );
    for (const raw of sources) {
      const s = raw as Record<string, unknown>;
      insertSource.run(
        String(s.id || generateId()),
        String(s.ministryId || s.ministry_id),
        String(s.name || ''),
        String(s.kind || 'feed'),
        String(s.location || ''),
        s.enabled === false ? 0 : 1,
        s.lastFetchedAt || s.last_fetched_at || null,
        s.lastStatus || s.last_status || null,
        Number(s.failStreak ?? s.fail_streak ?? 0),
      );
    }

    const insertItem = db.prepare(
      `INSERT INTO items (
        id, ministry_id, source_id, content_type, title, summary, full_text, source_url,
        document_format, file_name, status, quality_score, ai_reason, redraft_count,
        created_at, approved_at, archived_at, read_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    );
    for (const raw of items) {
      const it = raw as Record<string, unknown>;
      const createdAt = Number(it.createdAtEpochMillis ?? it.created_at_epoch_millis ?? Date.now());
      const createdAtIso = new Date(createdAt).toISOString();
      const archivedAt = it.archivedAt || createdAtIso;
      insertItem.run(
        String(it.id || generateId()),
        String(it.ministryId || it.topicId || 'rites'),
        it.sourceId || null,
        String(it.contentType || 'WEB_ARTICLE'),
        String(it.title || '无标题'),
        String(it.summary || ''),
        String(it.fullText || it.full_text || ''),
        it.sourceUrl || it.source_url || null,
        it.documentFormat || it.document_format || null,
        it.fileName || it.file_name || null,
        String(it.status || 'archived'),
        Number(it.qualityScore ?? 60),
        it.aiReason || it.ai_reason || 'V2 数据迁移导入',
        Number(it.redraftCount ?? it.redraft_count ?? 0),
        createdAtIso,
        it.approvedAt || (String(it.status || 'archived') === 'archived' ? createdAtIso : null),
        archivedAt,
        it.readAt || null,
      );
    }

    if (data.settings && typeof data.settings === 'object') {
      const settings = data.settings as Record<string, unknown>;
      if (settings.engineType || settings.baseUrl) {
        setSetting('ai', JSON.stringify({
          engineType: settings.engineType || 'OPENAI_COMPATIBLE',
          baseUrl: settings.baseUrl || '',
          modelName: settings.modelName || '',
          apiKey: settings.apiKey || '',
        }));
      }
    }
  });
  insertAll();
}

function insertItem(item: ItemRow): void {
  getDb().prepare(
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

function countBy(id: string, sql: string): number {
  return (getDb().prepare(sql).get(id) as { c: number }).c;
}

function ministryJson(row: MinistryRow) {
  return {
    id: row.id,
    title: row.title,
    icon: row.icon,
    color: row.color,
    order: row.sort_order,
  };
}

function sourceJson(row: SourceRow) {
  return {
    id: row.id,
    ministryId: row.ministry_id,
    name: row.name,
    kind: row.kind,
    location: row.location,
    enabled: row.enabled === 1,
    lastFetchedAt: row.last_fetched_at,
    lastStatus: row.last_status,
    failStreak: row.fail_streak,
  };
}

function preferenceJson(row: PreferenceRow) {
  return {
    ministryId: row.ministry_id,
    description: row.description,
    excludeKeywords: safeParseArray(row.exclude_keywords),
    excludeDomains: safeParseArray(row.exclude_domains),
    dailyLimit: row.daily_limit,
    scheduleCron: row.schedule_cron,
  };
}

function itemJson(row: ItemRow) {
  return {
    id: row.id,
    ministryId: row.ministry_id,
    sourceId: row.source_id,
    contentType: row.content_type,
    title: row.title,
    summary: row.summary,
    fullText: row.full_text,
    sourceUrl: row.source_url,
    documentFormat: row.document_format,
    fileName: row.file_name,
    status: row.status,
    qualityScore: row.quality_score,
    aiReason: row.ai_reason,
    redraftCount: row.redraft_count,
    createdAt: row.created_at,
    approvedAt: row.approved_at,
    archivedAt: row.archived_at,
    readAt: row.read_at,
  };
}

function fetchLogJson(row: FetchLogRow) {
  return {
    id: row.id,
    date: row.date,
    ministryId: row.ministry_id,
    sourceId: row.source_id,
    status: row.status,
    itemCount: row.item_count,
    error: row.error,
    createdAt: row.created_at,
  };
}

function rejectLogJson(row: RejectLogRow) {
  return {
    id: row.id,
    itemId: row.item_id,
    sourceId: row.source_id,
    reason: row.reason,
    title: row.title,
    sourceUrl: row.source_url,
    createdAt: row.created_at,
  };
}

function listPreferences() {
  return getDb().prepare('SELECT * FROM preferences').all() as PreferenceRow[];
}

function safeParseArray(raw: string | null | undefined): unknown[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function safeParseObject(raw: string | null | undefined): Record<string, unknown> {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}
