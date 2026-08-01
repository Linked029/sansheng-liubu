import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import type { AddressInfo } from 'node:net';
import type Database from 'better-sqlite3';

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'sansheng-smoke-'));
process.env.SSS_DB_PATH = path.join(tmpDir, 'smoke.sqlite');

let appServer: http.Server | null = null;
let mockAiServer: http.Server | null = null;
let pageServer: http.Server | null = null;
let port = 0;

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(`断言失败：${message}`);
}

function listen(server: http.Server): Promise<void> {
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve());
  });
}

function closeServer(server: http.Server | null): Promise<void> {
  if (!server || !server.listening) return Promise.resolve();
  return new Promise((resolve) => server.close(() => resolve()));
}

async function closeAll(): Promise<void> {
  await closeServer(appServer);
  await closeServer(mockAiServer);
  await closeServer(pageServer);
}

async function api(method: string, route: string, body?: unknown): Promise<{ status: number; json: any }> {
  const response = await fetch(`http://127.0.0.1:${port}${route}`, {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let json: any = null;
  try {
    json = JSON.parse(text);
  } catch {
    // 非 JSON 响应不参与断言
  }
  return { status: response.status, json };
}

async function main(): Promise<void> {
  let db: Database.Database | null = null;
  try {
  const dbModule = await import('./db');
  const { createApp } = await import('./api');
  db = dbModule.getDb();
  const app = createApp();
  appServer = app.listen(0);
  await new Promise<void>((resolve) => appServer!.once('listening', () => resolve()));
  port = (appServer.address() as AddressInfo).port;
  const today = dbModule.todayDate();

  const health = await api('GET', '/api/health');
  assert(health.status === 200, 'health 返回 200');
  assert(health.json?.ok === true, 'health ok=true');
  console.log('PASS health');

  const custom = await api('POST', '/api/ministries', { id: 'smoke-custom', title: '烟测部' });
  assert(custom.status === 201, '创建自定义六部');
  const customId = custom.json.id;
  const srcCreated = await api('POST', `/api/ministries/${customId}/sources`, {
    name: '烟测源',
    kind: 'url',
    location: 'http://example.com/smoke',
  });
  assert(srcCreated.status === 201, '自定义六部下创建信息源');
  assert(srcCreated.json.kind === 'url' && srcCreated.json.ministryId === customId, '信息源读回一致');
  const srcUpdated = await api('PUT', `/api/sources/${srcCreated.json.id}`, { name: '烟测源改', enabled: false });
  assert(srcUpdated.status === 200, '更新信息源');
  assert(srcUpdated.json.name === '烟测源改' && srcUpdated.json.enabled === false, '信息源更新读回一致');
  const pref = await api('GET', `/api/preferences/${customId}`);
  assert(pref.status === 200 && pref.json.dailyLimit === 3, '读取偏好卡默认 dailyLimit=3');
  const prefHi = await api('PUT', `/api/preferences/${customId}`, { dailyLimit: 99 });
  assert(prefHi.status === 200 && prefHi.json.dailyLimit === 10, 'dailyLimit 钳制到 10');
  const prefLo = await api('PUT', `/api/preferences/${customId}`, { dailyLimit: 0 });
  assert(prefLo.status === 200 && prefLo.json.dailyLimit === 1, 'dailyLimit 钳制到 1');
  const delDefault = await api('DELETE', '/api/ministries/officials');
  assert(delDefault.status === 400, '删除默认六部返回 400');
  const delCustom = await api('DELETE', `/api/ministries/${customId}`);
  assert(delCustom.status === 200, '删除自定义六部');
  console.log('PASS ministries/sources/preferences CRUD');

  await api('PUT', '/api/settings/ai', {
    engineType: 'OPENAI_COMPATIBLE',
    baseUrl: '',
    modelName: '',
    apiKey: '',
  });

  const createdA = await api('POST', '/api/items', {
    ministryId: 'rites',
    title: '烟测准奏 A',
    fullText: 'A 正文',
    qualityScore: 80,
  });
  assert(createdA.status === 201, '创建 candidate A');
  const dashBeforeApprove = await api('GET', '/api/dashboard');
  const archivedBefore = dashBeforeApprove.json.today.archivedToday;
  const approveA = await api('POST', `/api/items/${createdA.json.id}/approve`);
  assert(approveA.status === 200, '准奏 A');
  assert(
    approveA.json.status === 'archived' && approveA.json.approvedAt && approveA.json.archivedAt,
    '准奏后 archived 且 approvedAt/archivedAt 非空',
  );
  const dashAfterApprove = await api('GET', '/api/dashboard');
  assert(
    dashAfterApprove.json.today.archivedToday === archivedBefore + 1,
    'dashboard archivedToday 按本地今日计数',
  );

  const createdB = await api('POST', '/api/items', { ministryId: 'rites', title: '烟测驳回 B', fullText: 'B 正文' });
  const rejectB = await api('POST', `/api/items/${createdB.json.id}/reject`, { reason: '与偏好不符' });
  assert(rejectB.status === 200, '驳回 B');
  const goneB = await api('GET', `/api/items/${createdB.json.id}`);
  assert(goneB.status === 404, '驳回后条目不存在');
  const rejectLogs = await api('GET', '/api/reject-logs');
  assert(
    rejectLogs.json.some((r: any) => r.itemId === createdB.json.id && r.reason === '与偏好不符'),
    'reject_logs 有对应记录',
  );

  const createdC = await api('POST', '/api/items', {
    ministryId: 'rites',
    title: '烟测重拟 C',
    summary: '原摘要',
    fullText: 'C 正文',
    qualityScore: 61,
  });
  const cBefore = createdC.json;
  const redraft1 = await api('POST', `/api/items/${createdC.json.id}/redraft`, { reason: '标题不准确' });
  assert(redraft1.status === 200, '打回重拟（AI 未配置）');
  assert(redraft1.json.status === 'pending' && redraft1.json.redraftCount === 1, '重拟后 pending 且 redraft_count=1');
  assert(
    redraft1.json.title === cBefore.title &&
      redraft1.json.summary === cBefore.summary &&
      redraft1.json.qualityScore === cBefore.qualityScore,
    'AI 未配置时保留原 title/summary/qualityScore',
  );
  assert(
    typeof redraft1.json.aiReason === 'string' &&
      redraft1.json.aiReason.includes('标题不准确') &&
      redraft1.json.aiReason.includes('AI 未配置'),
    'ai_reason 含原因与“AI 未配置”标注',
  );
  console.log('PASS item approve/reject/redraft(no-ai)');

  mockAiServer = http.createServer((req, res) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
    });
    req.on('end', () => {
      if (req.url?.endsWith('/chat/completions') && req.method === 'POST') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(
          JSON.stringify({
            choices: [
              {
                message: {
                  content:
                    '{"title":"重拟标题","summary":"重拟摘要","aiReason":"重拟理由","qualityScore":88}',
                },
              },
            ],
          }),
        );
        return;
      }
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end('{}');
    });
  });
  await listen(mockAiServer);
  const mockPort = (mockAiServer.address() as AddressInfo).port;
  await api('PUT', '/api/settings/ai', {
    engineType: 'OPENAI_COMPATIBLE',
    baseUrl: `http://127.0.0.1:${mockPort}/v1`,
    modelName: 'mock-model',
    apiKey: 'test-key',
  });
  const redraft2 = await api('POST', `/api/items/${createdC.json.id}/redraft`, { reason: '再改一次' });
  assert(redraft2.status === 200, '打回重拟（AI 已配置 mock）');
  assert(
    redraft2.json.title === '重拟标题' &&
      redraft2.json.summary === '重拟摘要' &&
      redraft2.json.aiReason === '重拟理由' &&
      redraft2.json.qualityScore === 88,
    '重拟结果等于 mock 返回值',
  );
  assert(redraft2.json.redraftCount === 2, 'redraft_count 再 +1');
  assert(
    redraft2.json.status === 'pending' && redraft2.json.approvedAt === null && redraft2.json.archivedAt === null,
    '重拟后 approved_at/archived_at 保持 NULL',
  );
  console.log('PASS redraft with AI mock');

  pageServer = http.createServer((_req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(
      [
        '<html><head>',
        '<title>烟测本地文章</title>',
        '<meta name="description" content="烟测本地文章简介">',
        '</head><body><article>',
        '这是一篇用于烟测的本地文章正文，内容足够长以通过正文提取。',
        '第二句继续补充正文长度，避免提取器回退到 body。',
        '第三句继续补充正文长度，确保文章正文完整进入语料。',
        '</article></body></html>',
      ].join(''),
    );
  });
  await listen(pageServer);
  const pagePort = (pageServer.address() as AddressInfo).port;
  const pageUrl = `http://127.0.0.1:${pagePort}/article.html`;
  const urlSrc = await api('POST', '/api/ministries/rites/sources', {
    name: '本地 URL 源',
    kind: 'url',
    location: pageUrl,
  });
  assert(urlSrc.status === 201, '创建本地 URL 源');
  const urlSourceId = urlSrc.json.id;

  const run1 = await api('POST', '/api/scheduler/run', { ministryId: 'rites' });
  assert(run1.status === 200, '调度器运行');
  assert(run1.json.summaries?.[0]?.newCandidates === 1, `首次运行 newCandidates=1，实际 ${run1.json?.summaries?.[0]?.newCandidates}`);
  const candidates = await api('GET', '/api/items?ministryId=rites&status=candidate');
  const candidate = candidates.json.find((it: any) => it.sourceUrl === pageUrl);
  assert(candidate, '存在 sourceUrl 为本地页的候选奏折');
  assert(candidate.sourceId === urlSourceId, '候选 sourceId 等于实际源 id');
  const logs1 = await api('GET', `/api/fetch-logs?date=${today}`);
  assert(
    logs1.json.some((r: any) => r.sourceId === urlSourceId && r.status === 'ok' && r.date === today),
    'fetch_log 有 ok 记录且 date 等于本地今日',
  );
  const run2 = await api('POST', '/api/scheduler/run', { ministryId: 'rites' });
  assert(run2.json.summaries?.[0]?.newCandidates === 0, '再次运行因 URL 去重 newCandidates=0');

  const manualSrc = await api('POST', '/api/ministries/rites/sources', {
    name: '手动源',
    kind: 'manual',
    location: '',
  });
  assert(manualSrc.status === 201, '创建手动源');
  await api('POST', '/api/scheduler/run', { ministryId: 'rites' });
  const logs2 = await api('GET', `/api/fetch-logs?date=${today}`);
  assert(
    logs2.json.some(
      (r: any) => r.sourceId === manualSrc.json.id && r.status === 'error' && r.error?.includes('手动源无需定时抓取'),
    ),
    '手动源产生 error fetch_log 且含预期提示',
  );
  console.log('PASS scheduler fetch_log/source_id/dedupe/manual');

  const dashBefore = await api('GET', '/api/dashboard');
  const createdBefore = dashBefore.json.today.createdToday;
  const tzIso = `${today}T12:00:00.000Z`;
  db.prepare(
    `INSERT INTO items (
      id, ministry_id, content_type, title, summary, full_text, source_url,
      document_format, file_name, status, quality_score, ai_reason, redraft_count,
      created_at, approved_at, archived_at, read_at
    ) VALUES (
      'smoke-tz', 'rites', 'WEB_ARTICLE', '时区烟测', '', '', NULL,
      NULL, NULL, 'candidate', 0, NULL, 0, ?, NULL, NULL, NULL
    )`,
  ).run(tzIso);
  dbModule.migrateTimestampFormat(db);
  const migrated = db.prepare('SELECT created_at FROM items WHERE id = ?').get('smoke-tz') as { created_at: string };
  assert(!/(Z|[+-]\d{2}:?\d{2})$/.test(migrated.created_at), '迁移后无 Z/时区偏移');
  const expectedLocalDate = dbModule.formatLocalIso(new Date(tzIso)).slice(0, 10);
  assert(migrated.created_at.slice(0, 10) === expectedLocalDate, '迁移后为本地无时区 ISO');
  const dbDate = db.prepare('SELECT date(created_at) AS d FROM items WHERE id = ?').get('smoke-tz') as { d: string };
  assert(dbDate.d === expectedLocalDate, 'date(created_at) 等于本地对应日期');
  dbModule.migrateTimestampFormat(db);
  const migratedAgain = db.prepare('SELECT created_at FROM items WHERE id = ?').get('smoke-tz') as { created_at: string };
  assert(migratedAgain.created_at === migrated.created_at, '迁移幂等');
  const dashAfter = await api('GET', '/api/dashboard');
  assert(
    dashAfter.json.today.createdToday === createdBefore + (expectedLocalDate === today ? 1 : 0),
    'dashboard createdToday 按本地今日计数',
  );
  console.log('PASS timezone migration/dashboard');

  console.log('SMOKE PASS: health/CRUD/item-flow/redraft/scheduler/timezone 全部断言通过');
  } finally {
    await closeAll();
    if (db) db.close();
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
}

void main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error('SMOKE FAIL:', error instanceof Error ? error.message : error);
    process.exit(1);
  });
