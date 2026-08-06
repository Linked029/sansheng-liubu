import { getSetting } from './db';
import type { AiEngineSettings } from './types';

export function maskKey(key: string): string {
  if (!key) return '';
  if (key.length <= 8) return '****';
  return key.slice(0, 4) + '****' + key.slice(-4);
}

export function isMaskedKey(key: string): boolean {
  return key.includes('****');
}

export function getInternalAiSettings(): AiEngineSettings {
  const ai = parseObject(getSetting('ai')) as Partial<AiEngineSettings>;
  const envKey = process.env.SSS_AI_KEY;
  return {
    engineType: ai.engineType || 'OPENAI_COMPATIBLE',
    baseUrl: ai.baseUrl || '',
    modelName: ai.modelName || '',
    apiKey: envKey !== undefined ? envKey : (ai.apiKey || ''),
  };
}

function parseObject(raw: string | null): Record<string, unknown> {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}
