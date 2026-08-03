export type SourceKind = 'feed' | 'url' | 'manual';

export interface MinistryRow {
  id: string;
  title: string;
  icon: string;
  color: string;
  sort_order: number;
}

export interface SourceRow {
  id: string;
  ministry_id: string;
  name: string;
  kind: SourceKind;
  location: string;
  enabled: number;
  last_fetched_at: string | null;
  last_status: string | null;
  fail_streak: number;
}

export interface PreferenceRow {
  ministry_id: string;
  description: string;
  exclude_keywords: string;
  exclude_domains: string;
  daily_limit: number;
  schedule_cron: string;
}

export type ItemStatus =
  | 'candidate'
  | 'pending'
  | 'archived'
  | 'read'
  | 'reviewing'
  | 'mastered';

export interface ItemRow {
  id: string;
  ministry_id: string;
  source_id: string | null;
  content_type: string;
  title: string;
  summary: string;
  full_text: string;
  source_url: string | null;
  document_format: string | null;
  file_name: string | null;
  status: ItemStatus;
  quality_score: number;
  ai_reason: string | null;
  redraft_count: number;
  created_at: string;
  approved_at: string | null;
  archived_at: string | null;
  read_at: string | null;
}

export interface FetchLogRow {
  id: string;
  date: string;
  ministry_id: string;
  source_id: string | null;
  status: string;
  item_count: number;
  error: string | null;
  created_at: string;
}

export interface RejectLogRow {
  id: string;
  item_id: string;
  source_id: string | null;
  reason: string | null;
  title: string;
  source_url: string | null;
  created_at: string;
}

export interface AiEngineSettings {
  engineType: 'OPENAI_COMPATIBLE' | 'OPENAI_RESPONSES' | 'ANTHROPIC' | 'GEMINI';
  baseUrl: string;
  modelName: string;
  apiKey: string;
}

export interface AppSettings {
  ai: AiEngineSettings;
  aiPresets: { name: string; engineType: string; baseUrl: string; modelName: string; apiKey: string }[];
  autoApproveThreshold: number;
}

export interface DraftArticle {
  title: string;
  summary: string;
  fullText: string;
  sourceUrl: string | null;
  publishedAt: string | null;
  qualityScore: number;
  aiReason: string;
}

export interface FetchSourceResult {
  sourceId: string;
  name: string;
  ok: boolean;
  error: string | null;
  articles: DraftArticle[];
}

export interface MinistryFetchSummary {
  ministryId: string;
  ministryTitle: string;
  sources: FetchSourceResult[];
  newCandidates: number;
  errors: string[];
}

export type ReviewStatus = 'reviewing' | 'mastered';
export type ReviewRating = 'forgot' | 'hard' | 'good' | 'easy';

export interface ReviewRow {
  item_id: string;
  stage: number;
  due_at: string;
  interval_days: number;
  ease: number;
  review_count: number;
  last_reviewed_at: string | null;
  status: ReviewStatus;
}

export interface AnnotationRow {
  id: string;
  item_id: string;
  created_at: string;
  text: string;
}

export interface LearningStats {
  date: string;
  dueToday: number;
  completedToday: number;
  completionRate: number | null;
  dueByMinistry: Record<string, number>;
  weeklyCount: number;
  masteredCount: number;
}
