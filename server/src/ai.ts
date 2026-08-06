import type { AiEngineSettings, DraftArticle, MinistryRow, PreferenceRow } from './types';

interface AiRequest {
  endpoint: string;
  method: string;
  headers: Record<string, string>;
  body: string | null;
}

interface AiResponse {
  code: number;
  body: string;
}

async function sendRequest(request: AiRequest): Promise<AiResponse> {
  const response = await fetch(request.endpoint, {
    method: request.method,
    headers: request.headers,
    body: request.body,
    signal: AbortSignal.timeout(60000),
  });
  return { code: response.status, body: await response.text() };
}

function endpoint(baseUrl: string, path: string): string {
  const base = baseUrl.trim().replace(/\/+$/, '');
  return base ? `${base}/${path}` : '';
}

function authHeaders(apiKey: string): Record<string, string> {
  return apiKey.trim() ? { Authorization: `Bearer ${apiKey.trim()}` } : {};
}

function buildRequest(settings: AiEngineSettings, prompt: string): AiRequest {
  switch (settings.engineType) {
    case 'OPENAI_COMPATIBLE':
      return {
        endpoint: endpoint(settings.baseUrl, 'chat/completions'),
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(settings.apiKey) },
        body: JSON.stringify({
          model: settings.modelName,
          messages: [{ role: 'user', content: prompt }],
          max_tokens: 900,
          temperature: 0.3,
          response_format: { type: 'json_object' },
        }),
      };
    case 'OPENAI_RESPONSES':
      return {
        endpoint: endpoint(settings.baseUrl, 'responses'),
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(settings.apiKey) },
        body: JSON.stringify({
          model: settings.modelName,
          input: prompt,
          max_output_tokens: 900,
          text: { format: { type: 'json_object' } },
        }),
      };
    case 'ANTHROPIC':
      return {
        endpoint: endpoint(settings.baseUrl, 'messages'),
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-api-key': settings.apiKey.trim(),
          'anthropic-version': '2023-06-01',
        },
        body: JSON.stringify({
          model: settings.modelName,
          max_tokens: 900,
          messages: [{ role: 'user', content: prompt }],
        }),
      };
    case 'GEMINI':
      return {
        endpoint: `${endpoint(settings.baseUrl, `models/${settings.modelName}:generateContent`)}?key=${encodeURIComponent(settings.apiKey.trim())}`,
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: { maxOutputTokens: 900 },
        }),
      };
    default:
      throw new Error(`不支持的引擎类型: ${settings.engineType}`);
  }
}

function extractModelText(engineType: string, body: string): string {
  const json = JSON.parse(body);
  switch (engineType) {
    case 'OPENAI_COMPATIBLE':
      return json.choices?.[0]?.message?.content || '';
    case 'OPENAI_RESPONSES':
      return json.output_text || json.output?.[0]?.content?.[0]?.text || '';
    case 'ANTHROPIC':
      return json.content?.[0]?.text || '';
    case 'GEMINI':
      return json.candidates?.[0]?.content?.parts?.[0]?.text || '';
    default:
      return '';
  }
}

function extractJsonObject(text: string): string {
  const trimmed = text.trim();
  const unfenced = trimmed.startsWith('```')
    ? trimmed.replace(/^```(?:json|JSON)?\n?/, '').replace(/\n?```$/, '').trim()
    : trimmed;
  const start = unfenced.indexOf('{');
  if (start < 0) throw new Error('未找到 JSON 对象');
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < unfenced.length; i++) {
    const ch = unfenced[i];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (ch === '\\' && inString) {
      escaped = true;
      continue;
    }
    if (ch === '"') {
      inString = !inString;
      continue;
    }
    if (!inString) {
      if (ch === '{') depth++;
      if (ch === '}') {
        depth--;
        if (depth === 0) return unfenced.substring(start, i + 1);
      }
    }
  }
  throw new Error('JSON 对象不完整');
}

export function isAiConfigured(settings: AiEngineSettings): boolean {
  return Boolean(settings.baseUrl.trim()) && Boolean(settings.modelName.trim());
}

export async function draftArticle(
  article: DraftArticle,
  ministry: MinistryRow,
  preference: PreferenceRow | undefined,
  settings: AiEngineSettings,
): Promise<DraftArticle> {
  if (!isAiConfigured(settings)) {
    return {
      ...article,
      summary: article.summary || article.fullText.slice(0, 200),
      qualityScore: 60,
      aiReason: '启发式拟折：未配置 AI 引擎，采用标题与正文摘要。',
    };
  }

  const prompt = [
    '你是三省六部个人知识库的中书省拟折官，负责为候选文章生成奏折摘要。',
    `目标六部：${ministry.title}（id=${ministry.id}）`,
    `本部偏好：${preference?.description || '无特别偏好'}`,
    `文章标题：${article.title}`,
    `文章来源：${article.sourceUrl || '未知'}`,
    `文章正文节选：${article.fullText.slice(0, 6000)}`,
    '',
    '请生成：',
    '1. title：不超过 28 个中文字符的简洁标题；',
    '2. summary：不超过 120 个中文字符的简介；',
    '3. aiReason：一句话说明为何适合收入本部；',
    '4. qualityScore：0-100 的整数，代表与本部偏好的匹配质量。',
    '',
    '只返回严格 JSON 对象，不要 Markdown：',
    '{"title":"...","summary":"...","aiReason":"...","qualityScore":80}',
  ].join('\n');

  try {
    const request = buildRequest(settings, prompt);
    if (!request.endpoint) return heuristicDraft(article);
    const response = await sendRequest(request);
    if (response.code < 200 || response.code >= 300) return heuristicDraft(article);
    const text = extractModelText(settings.engineType, response.body);
    const json = JSON.parse(extractJsonObject(text));
    return {
      ...article,
      title: String(json.title || article.title).slice(0, 200),
      summary: String(json.summary || article.summary || '').slice(0, 500),
      aiReason: String(json.aiReason || 'AI 拟折完成'),
      qualityScore: clampScore(Number(json.qualityScore)),
    };
  } catch {
    return heuristicDraft(article);
  }
}

export function heuristicDraft(article: DraftArticle): DraftArticle {
  return {
    ...article,
    summary: article.summary || article.fullText.slice(0, 200),
    qualityScore: 60,
    aiReason: '启发式拟折：AI 引擎不可用，采用原标题与正文摘要。',
  };
}

function clampScore(score: number): number {
  if (Number.isNaN(score)) return 60;
  return Math.max(0, Math.min(100, Math.round(score)));
}

export interface RedraftResult {
  title: string;
  summary: string;
  aiReason: string;
  qualityScore: number;
}

export async function redraftArticle(
  article: Pick<DraftArticle, 'title' | 'summary' | 'fullText' | 'qualityScore'>,
  reason: string,
  ministry: MinistryRow,
  preference: PreferenceRow | undefined,
  settings: AiEngineSettings,
): Promise<RedraftResult> {
  const fallback = (note: string): RedraftResult => ({
    title: article.title,
    summary: article.summary,
    qualityScore: article.qualityScore,
    aiReason: `打回重拟${reason ? `：${reason}` : ''}（${note}，保留原标题与摘要）`,
  });

  if (!isAiConfigured(settings)) return fallback('AI 未配置');

  const prompt = [
    '你是三省六部个人知识库的中书省拟折官，负责根据打回原因重新生成奏折。',
    `目标六部：${ministry.title}（id=${ministry.id}）`,
    `本部偏好：${preference?.description || '无特别偏好'}`,
    `原标题：${article.title}`,
    `原摘要：${article.summary}`,
    `打回原因：${reason || '未填写'}`,
    `文章正文节选：${article.fullText.slice(0, 6000)}`,
    '',
    '请针对打回原因修正并重新生成：',
    '1. title：不超过 28 个中文字符的简洁标题；',
    '2. summary：不超过 120 个中文字符的简介；',
    '3. aiReason：一句话说明针对打回原因做了哪些修正；',
    '4. qualityScore：0-100 的整数，代表与本部门偏好的匹配质量。',
    '',
    '只返回严格 JSON 对象，不要 Markdown：',
    '{"title":"...","summary":"...","aiReason":"...","qualityScore":80}',
  ].join('\n');

  try {
    const request = buildRequest(settings, prompt);
    if (!request.endpoint) return fallback('AI 调用失败');
    const response = await sendRequest(request);
    if (response.code < 200 || response.code >= 300) return fallback('AI 调用失败');
    const text = extractModelText(settings.engineType, response.body);
    const json = JSON.parse(extractJsonObject(text));
    return {
      title: String(json.title || article.title).slice(0, 200),
      summary: String(json.summary || article.summary || '').slice(0, 500),
      aiReason: String(json.aiReason || 'AI 重拟完成'),
      qualityScore: clampScore(Number(json.qualityScore)),
    };
  } catch {
    return fallback('AI 调用失败');
  }
}

export interface ClassifyResult {
  topicId: string;
  contentType: string;
  title: string;
  summary: string;
  sourceUrl?: string;
  documentFormat?: string;
}

export async function classifyManual(
  rawText: string,
  ministries: MinistryRow[],
  sourceUrl: string | undefined,
  settings: AiEngineSettings,
): Promise<ClassifyResult> {
  const fallback: ClassifyResult = {
    topicId: ministries[0]?.id || 'rites',
    contentType: 'WEB_ARTICLE',
    title: rawText.trim().slice(0, 28),
    summary: rawText.trim().slice(0, 96),
    sourceUrl,
  };
  if (!isAiConfigured(settings) || ministries.length === 0) return fallback;

  const prompt = [
    '你是三省六部个人知识库的中书省拟折官。请基于用户输入内容选择最合适的六部并生成奏折。',
    '六部选项：',
    ministries.map((m) => `- id=${m.id}; title=${m.title}`).join('\n'),
    '',
    `用户输入：${rawText.slice(0, 8000)}`,
    sourceUrl ? `来源 URL：${sourceUrl}` : '',
    '',
    'contentType 只能是 WEB_ARTICLE、IMAGE_SCREENSHOT、DOCUMENT 之一。',
    'title 不超过 28 个中文字符，summary 不超过 96 个中文字符。',
    '只返回严格 JSON：{"topicId":"六部ID","contentType":"WEB_ARTICLE","title":"...","summary":"...","sourceUrl":"","documentFormat":""}',
  ].join('\n');

  try {
    const request = buildRequest(settings, prompt);
    const response = await sendRequest(request);
    if (response.code < 200 || response.code >= 300) return fallback;
    const json = JSON.parse(extractJsonObject(extractModelText(settings.engineType, response.body)));
    const validTopic = ministries.some((m) => m.id === json.topicId);
    return {
      topicId: validTopic ? json.topicId : ministries[0]?.id || 'rites',
      contentType: ['WEB_ARTICLE', 'IMAGE_SCREENSHOT', 'DOCUMENT'].includes(json.contentType) ? json.contentType : 'WEB_ARTICLE',
      title: String(json.title || fallback.title).slice(0, 200),
      summary: String(json.summary || fallback.summary).slice(0, 500),
      sourceUrl: json.sourceUrl?.trim() || sourceUrl,
      documentFormat: json.documentFormat || undefined,
    };
  } catch {
    return fallback;
  }
}

// ─── Direction decomposition (exploration track) ───────────────────

export interface DecomposeDirectionResult {
  terms: string[];
}

export async function decomposeDirection(
  directionText: string,
  ministryTitle: string,
  settings: AiEngineSettings,
): Promise<DecomposeDirectionResult> {
  const fallback: DecomposeDirectionResult = {
    terms: [directionText],
  };
  if (!isAiConfigured(settings)) return fallback;

  const prompt = [
    '你是三省六部个人知识库的探索官。用户希望扩展知识边界，请将他的方向描述拆解为 3~5 个具体的搜索关键词。',
    '拆分原则：',
      '- 搜索词必须紧扣方向描述的核心主题，不能偏离替换为其他技术栈',
      '- 1~2 个搜索词直接使用方向原文的核心关键词组合',
      '- 1~2 个搜索词去掉方向中的具体技术栈，只围绕应用场景本身搜索',
      '- 其余搜索词围绕该方向的技术实现、教程、方案对比展开',
      '- 返回 JSON 数组，不要额外解释',
    `目标六部：${ministryTitle}`,
    `方向描述：${directionText}`,
    '',
    '只返回严格 JSON 对象：',
    '{"terms":["搜索词1","搜索词2","搜索词3","搜索词4","搜索词5"]}',
  ].join('\n');

  try {
    const request = buildRequest(settings, prompt);
    if (!request.endpoint) return fallback;
    const response = await sendRequest(request);
    if (response.code < 200 || response.code >= 300) return fallback;
    const text = extractModelText(settings.engineType, response.body);
    const json = JSON.parse(extractJsonObject(text));
    const terms: string[] = Array.isArray(json.terms)
      ? json.terms.map((t: unknown) => String(t).trim().slice(0, 120)).filter(Boolean).slice(0, 5)
      : [directionText];
    return { terms: terms.length > 0 ? terms : [directionText] };
  } catch {
    return fallback;
  }
}
