// Exploration search: Bing HTML scraping + pipeline
import * as cheerio from "cheerio";
import type { AiEngineSettings, ExplorationItemRow, SearchDirectionRow, SearchTermRow } from "./types";
import { decomposeDirection } from "./ai";
import {
  insertExplorationItem,
  insertSearchTerm,
  isExplorationUrlDuplicate,
  listSearchTerms,
  touchSearchTerm,
} from "./exploration";
import { generateId, getDb, getSetting, listMinistries, nowIso } from "./db";

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

const RESULTS_PER_TERM = 5;

export interface SearchResult {
  title: string;
  url: string;
  snippet: string;
  sourceName: string;
}

// ─── DuckDuckGo search ─────────────────────────────────────────────

export async function searchWeb(query: string): Promise<SearchResult[]> {
  // Bing search (accessible in China); DuckDuckGo is geo-blocked
  const url = "https://www.bing.com/search?q=" + encodeURIComponent(query) + "&setlang=zh-cn";
  const response = await fetch(url, {
    headers: { "User-Agent": USER_AGENT, "Accept": "text/html" },
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) return [];
  if (!response.ok) throw new Error(`搜索请求失败: HTTP ${response.status}`);
  const html = await response.text();
  const $ = cheerio.load(html);
  const results: SearchResult[] = [];
  $("#b_results .b_algo").each((_, el) => {
    const $el = $(el);
    const $link = $el.find("h2 a").first();
    const $snippet = $el.find(".b_caption p").first();
    const title = $link.text().trim();
    const href = $link.attr("href") || "";
    const snippet = $snippet.text().trim();
    if (!title || !href) return;
    const sourceName = extractSourceName(href);
    results.push({ title, url: href, snippet, sourceName });
  });
  return results.slice(0, RESULTS_PER_TERM * 2);
}

function extractSourceName(url: string): string {
  try {
    const host = new URL(url).hostname.replace(/^www\./, "");
    return host.split(".")[0] || host;
  } catch {
    return url.slice(0, 40);
  }
}

// ─── Run search for a term ─────────────────────────────────────────

async function runTermSearch(
  term: SearchTermRow,
  ministryId: string,
  directionId: string,
  seenUrls: Set<string>,
): Promise<number> {
  const results = await searchWeb(term.term);
  let added = 0;
  for (const r of results) {
    if (added >= RESULTS_PER_TERM) break;
    if (!r.url || isExplorationUrlDuplicate(r.url) || seenUrls.has(r.url)) continue;
    // Relevance filter disabled; Chinese tokenisation is fragile. Future: lightweight 2-gram match.
    const item: ExplorationItemRow = {
      id: generateId(),
      ministry_id: ministryId,
      direction_id: directionId,
      search_term_id: term.id,
      title: r.title || "无标题",
      summary: r.snippet || "",
      full_text: r.snippet || "",
      source_url: r.url,
      source_name: r.sourceName || "未知来源",
      status: "new",
      created_at: nowIso(),
    };
    insertExplorationItem(item);
    added++;
  }
  touchSearchTerm(term.id);
  return added;
}

// ─── Pipeline: direction → terms → search → items ──────────────────

export interface ExplorationRunResult {
  directionId: string;
  terms: string[];
  totalResults: number;
  errors: string[];
}

export async function runExplorationPipeline(
  direction: SearchDirectionRow,
): Promise<ExplorationRunResult> {
  const errors: string[] = [];
  const ministries = listMinistries();
  const ministry = ministries.find((m) => m.id === direction.ministry_id);
  const ministryTitle = ministry?.title || direction.ministry_id;

  const raw = getSetting("ai");
  let settings: AiEngineSettings;
  try {
    settings = raw ? JSON.parse(raw) : { engineType: "OPENAI_COMPATIBLE", baseUrl: "", modelName: "", apiKey: "" };
  } catch {
    settings = { engineType: "OPENAI_COMPATIBLE", baseUrl: "", modelName: "", apiKey: "" };
  }

  const decomposed = await decomposeDirection(direction.direction_text, ministryTitle, settings);
  if (decomposed.terms.length === 0) {
    errors.push("AI 未能拆解搜索词，回退为方向原文。");
    decomposed.terms.push(direction.direction_text);
  }

  const terms: SearchTermRow[] = [];
  for (const t of decomposed.terms) {
    const existing = listSearchTerms(direction.id).find((st) => st.term === t);
    if (existing) {
      terms.push(existing);
    } else {
      terms.push(insertSearchTerm(direction.id, t));
    }
  }

  let total = 0;
  const seenUrls = new Set<string>();
  for (const term of terms) {
    try {
      total += await runTermSearch(term, direction.ministry_id, direction.id, seenUrls);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      errors.push("搜索词 \"" + term.term + "\" 失败: " + msg);
    }
  }

  // Direction stays active — user can re-run to refine results
  return { directionId: direction.id, terms: decomposed.terms, totalResults: total, errors };
}

// ─── Suggest new fixed sources from exploration archives ────────────

export interface SourceSuggestion {
  sourceName: string;
  archiveCount: number;
}

export function suggestFixedSources(threshold = 2): SourceSuggestion[] {
  const db = getDb();
  const rows = db.prepare(
    "SELECT source_name, COUNT(*) AS c FROM exploration_items WHERE status = 'archived' GROUP BY source_name HAVING c >= ? ORDER BY c DESC"
  ).all(threshold) as Array<{ source_name: string; c: number }>;
  return rows.map((r) => ({ sourceName: r.source_name, archiveCount: r.c }));
}
