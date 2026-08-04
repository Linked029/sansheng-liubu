import Parser from 'rss-parser';
import * as cheerio from 'cheerio';
import type { DraftArticle, FetchSourceResult, SourceRow } from './types';

const USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36';

export async function fetchSource(source: SourceRow): Promise<FetchSourceResult> {
  try {
    if (source.kind === 'feed') {
      try {
        const articles = await fetchFeed(source.location);
        return { sourceId: source.id, name: source.name, ok: true, error: null, articles };
      } catch (feedErr) {
        // Feed parse failed — if location looks like an HTML URL, try URL fetch
        const loc = source.location.trim();
        if (/^https?:\/\//.test(loc) && !/\.(xml|rss|atom)(\?|$)/i.test(loc)) {
          const articles = await fetchUrl(loc);
          return { sourceId: source.id, name: source.name, ok: true, error: null, articles };
        }
        throw feedErr;
      }
    }
    if (source.kind === 'url') {
      const articles = await fetchUrl(source.location);
      return { sourceId: source.id, name: source.name, ok: true, error: null, articles };
    }
    return {
      sourceId: source.id,
      name: source.name,
      ok: false,
      error: '手动源无需定时抓取，请在 Web 端直接录入。',
      articles: [],
    };
  } catch (error) {
    return {
      sourceId: source.id,
      name: source.name,
      ok: false,
      error: error instanceof Error ? error.message : String(error),
      articles: [],
    };
  }
}

async function fetchFeed(location: string): Promise<DraftArticle[]> {
  const parser = new Parser({
    timeout: 15000,
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/rss+xml, application/atom+xml, text/xml' },
  });
  const feed = await parser.parseURL(location);
  const items = feed.items ?? [];
  return items.slice(0, 50).map((item) => {
    const rawContent = item.content || item['content:encoded'] || item.contentSnippet || item.summary || '';
    const fullText = stripHtml(rawContent).slice(0, 20000);
    const snippet = stripHtml(item.contentSnippet || item.summary || '').slice(0, 500);
    return {
      title: (item.title || '').trim().slice(0, 200),
      summary: snippet || fullText.slice(0, 200),
      fullText,
      sourceUrl: item.link || null,
      publishedAt: item.isoDate || item.pubDate || null,
      qualityScore: 0,
      aiReason: '',
    };
  });
}

async function fetchUrl(location: string): Promise<DraftArticle[]> {
  const response = await fetch(location, {
    headers: {
      'User-Agent': USER_AGENT,
      Accept: 'text/html,application/xhtml+xml',
      'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
    },
    signal: AbortSignal.timeout(20000),
    redirect: 'follow',
  });
  if (!response.ok) throw new Error(`HTTP ${response.status} ${response.statusText}`);
  const html = await response.text();
  const $ = cheerio.load(html);

  const metaContent = (selector: string): string => $(selector).first().attr('content')?.trim() || '';
  const title =
    $('title').first().text().trim() ||
    metaContent('meta[property="og:title"]') ||
    metaContent('meta[name="title"]') ||
    metaContent('meta[property="twitter:title"]') ||
    location;
  const description =
    metaContent('meta[name="description"]') ||
    metaContent('meta[property="og:description"]') ||
    metaContent('meta[property="twitter:description"]') ||
    '';

  $('script, style, noscript, nav, footer, header, form, iframe, svg, .ad, .ads, .advertisement, .comment').remove();
  const contentSelectors = [
    '#js_content',
    '.rich_media_content',
    '.rich_media_area_primary',
    '#cnblogs_post_body',
    '.postBody',
    'article',
    'main',
    '.post-content',
    '.entry-content',
    '.article-content',
    '#content',
    '.content',
  ];
  let container: ReturnType<typeof $> = $('body').first();
  for (const selector of contentSelectors) {
    const el = $(selector).first();
    if (el.length > 0 && el.text().trim().length > 200) {
      container = el;
      break;
    }
  }
  const fullText = container.text().replace(/\s+/g, ' ').trim().slice(0, 20000);
  return [
    {
      title: title.slice(0, 200),
      summary: description || fullText.slice(0, 200),
      fullText,
      sourceUrl: location,
      publishedAt: null,
      qualityScore: 0,
      aiReason: '',
    },
  ];
}

export async function fetchUrlArticle(location: string): Promise<DraftArticle | null> {
  const articles = await fetchUrl(location);
  return articles[0] ?? null;
}

export function stripHtml(html: string): string {
  if (!html) return '';
  return html
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/\s+/g, ' ')
    .trim();
}
