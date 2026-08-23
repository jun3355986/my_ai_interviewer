import type { AiLlmCall, AiObservabilityStats } from './types';

export function formatDate(value?: string | null) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

export function formatRelative(value?: string | null) {
  if (!value) {
    return '-';
  }
  const time = new Date(value).getTime();
  if (Number.isNaN(time)) {
    return formatDate(value);
  }
  const diff = Date.now() - time;
  if (diff < 60_000) return '刚刚';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`;
  if (diff < 7 * 86_400_000) return `${Math.floor(diff / 86_400_000)} 天前`;
  return formatDate(value).slice(0, 10);
}

export function userStatusText(value?: number | null) {
  if (value === 1) return '启用';
  if (value === 0) return '停用';
  if (value === 2) return '已完成';
  return value == null ? '-' : String(value);
}

export function branchStatusText(value?: number | null) {
  if (value === 1) return '进行中';
  if (value === 2) return '已完成';
  if (value === 3) return '已取消';
  return value == null ? '-' : String(value);
}

export function questionStatusText(value?: number | null) {
  if (value === 1) return '已上架';
  if (value === 0) return '已下架';
  if (value === 2) return '待审核';
  if (value === 3) return '已驳回';
  return value == null ? '-' : String(value);
}

export function questionStatusTone(value?: number | null): 'success' | 'warn' | 'danger' | undefined {
  if (value === 1) return 'success';
  if (value === 2) return 'warn';
  if (value === 3) return 'danger';
  return undefined;
}

export function userStatusTone(value?: number | null): 'success' | 'warn' | undefined {
  if (value === 1) return 'success';
  if (value === 2) return undefined;
  return 'warn';
}

export function textStatusTone(value?: string | null): 'success' | 'danger' | 'warn' | undefined {
  const normalized = (value || '').toUpperCase();
  if (['SUCCESS', 'COMPLETED', 'OK'].includes(normalized)) return 'success';
  if (['FAILED', 'ERROR', 'TIMEOUT'].includes(normalized)) return 'danger';
  return 'warn';
}

export function stageDisplay(stage?: string | null) {
  const map: Record<string, string> = {
    opening: '开场与说明',
    self_introduction: '自我介绍',
    project_qna: '项目经历',
    technical_qna: '技术能力',
    concluded: '总结与评分',
  };
  if (!stage) return '-';
  return map[stage] || stage;
}

export const STAGE_ORDER = ['opening', 'self_introduction', 'project_qna', 'technical_qna', 'concluded'];

export function formatNumber(value?: number | null) {
  return value == null ? '-' : Intl.NumberFormat('zh-CN').format(value);
}

export function formatDuration(value?: number | null) {
  return value == null ? '-' : `${Intl.NumberFormat('zh-CN').format(Math.round(value))}ms`;
}

export function formatPercent(value?: number | null) {
  if (value == null) {
    return '-';
  }
  return `${(value * 100).toFixed(2)}%`;
}

export function compactId(value?: string | null) {
  if (!value) {
    return '-';
  }
  return value.length > 14 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value;
}

export function providerModel(call?: Pick<AiLlmCall, 'provider' | 'model'> | null) {
  if (!call?.provider && !call?.model) {
    return '-';
  }
  return `${call.provider || '-'} / ${call.model || '-'}`;
}

export function statNumber(stats: AiObservabilityStats | null, frontendKey: keyof AiObservabilityStats, backendKey?: keyof AiObservabilityStats) {
  if (!stats) {
    return 0;
  }
  const direct = stats[frontendKey];
  const fallback = backendKey ? stats[backendKey] : undefined;
  const value = typeof direct === 'number' ? direct : typeof fallback === 'number' ? fallback : 0;
  return value;
}

export function statRate(stats: AiObservabilityStats | null, frontendKey: keyof AiObservabilityStats, backendKey?: keyof AiObservabilityStats) {
  if (!stats) {
    return null;
  }
  const direct = stats[frontendKey];
  const fallback = backendKey ? stats[backendKey] : undefined;
  return typeof direct === 'number' ? direct : typeof fallback === 'number' ? fallback : null;
}

export function splitList(value: string) {
  return value
    .split(/[,\n，]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function compactPayload<T extends object>(payload: T): T {
  const next = { ...payload } as Record<string, unknown>;
  Object.keys(next).forEach((key) => {
    if (next[key] === '') {
      delete next[key];
    }
  });
  return next as T;
}

export function uuid() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function splitStrengths(value?: string | null): string[] {
  if (!value) {
    return [];
  }
  return value
    .split(/[\n；;]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}
