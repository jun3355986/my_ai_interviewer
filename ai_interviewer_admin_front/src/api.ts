import type {
  AdminProfile,
  ApiResult,
  AuditLogRow,
  AiLlmCall,
  AiObservabilityStats,
  AiRawPayloadType,
  AiTraceDetail,
  AiTraceRow,
  DashboardOverview,
  InterviewRow,
  JobCreatePayload,
  JobRow,
  LoginResponse,
  LlmCallRawPayload,
  PageResult,
  QuestionCreatePayload,
  QuestionImportBatch,
  QuestionRow,
  UserRow,
} from './types';

const TOKEN_KEY = 'ai_interviewer_admin_token';
const PROFILE_KEY = 'ai_interviewer_admin_profile';
const API_BASE = (import.meta.env.VITE_ADMIN_API_BASE || '/admin').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly code?: number,
  ) {
    super(message);
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function saveSession(login: LoginResponse) {
  localStorage.setItem(TOKEN_KEY, login.accessToken);
  localStorage.setItem(PROFILE_KEY, JSON.stringify(login.admin));
}

export function readProfile(): AdminProfile | null {
  const raw = localStorage.getItem(PROFILE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AdminProfile;
  } catch {
    return null;
  }
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(PROFILE_KEY);
}

function queryString(params?: Record<string, unknown>) {
  if (!params) {
    return '';
  }
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  let payload: ApiResult<T> | null = null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    payload = (await response.json()) as ApiResult<T>;
  }

  if (!response.ok || !payload || payload.code !== 200) {
    if (response.status === 401) {
      clearSession();
    }
    throw new ApiError(payload?.message || response.statusText || '请求失败', response.status, payload?.code);
  }

  return payload.data;
}

export const adminApi = {
  login(username: string, password: string) {
    return request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
  },
  me() {
    return request<AdminProfile>('/auth/me');
  },
  dashboard() {
    return request<DashboardOverview>('/dashboard/overview');
  },
  users(params: Record<string, unknown>) {
    return request<PageResult<UserRow>>(`/users${queryString(params)}`);
  },
  disableUser(userId: number) {
    return request<void>(`/users/${userId}/disable`, { method: 'PATCH' });
  },
  resetPassword(userId: number, newPassword: string) {
    return request<void>(`/users/${userId}/reset-password`, {
      method: 'POST',
      body: JSON.stringify({ newPassword }),
    });
  },
  jobs(params: Record<string, unknown>) {
    return request<PageResult<JobRow>>(`/jobs${queryString(params)}`);
  },
  createJob(payload: JobCreatePayload) {
    return request<number>('/jobs', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  interviews(params: Record<string, unknown>) {
    return request<PageResult<InterviewRow>>(`/interviews${queryString(params)}`);
  },
  questions(params: Record<string, unknown>) {
    return request<PageResult<QuestionRow>>(`/questions${queryString(params)}`);
  },
  createQuestion(payload: QuestionCreatePayload) {
    return request<number>('/questions', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  importQuestions(file: File) {
    const form = new FormData();
    form.set('file', file);
    return request<QuestionImportBatch>('/questions/import', {
      method: 'POST',
      body: form,
    });
  },
  questionImports(params: Record<string, unknown>) {
    return request<PageResult<QuestionImportBatch>>(`/questions/import${queryString(params)}`);
  },
  approveQuestion(questionId: number) {
    return request<void>(`/questions/${questionId}/approve`, { method: 'PATCH' });
  },
  rejectQuestion(questionId: number) {
    return request<void>(`/questions/${questionId}/reject`, { method: 'PATCH' });
  },
  publishQuestion(questionId: number) {
    return request<void>(`/questions/${questionId}/publish`, { method: 'PATCH' });
  },
  unpublishQuestion(questionId: number) {
    return request<void>(`/questions/${questionId}/unpublish`, { method: 'PATCH' });
  },
  deleteQuestion(questionId: number) {
    return request<void>(`/questions/${questionId}`, { method: 'DELETE' });
  },
  syncQuestions() {
    return request<unknown>('/questions/vector-sync', { method: 'POST' });
  },
  auditLogs(params: Record<string, unknown>) {
    return request<PageResult<AuditLogRow>>(`/audit/logs${queryString(params)}`);
  },
  aiTraces(params: Record<string, unknown>) {
    return request<PageResult<AiTraceRow>>(`/ai-observability/traces${queryString(params)}`);
  },
  aiTraceDetail(traceId: string) {
    return request<AiTraceDetail>(`/ai-observability/traces/${traceId}`);
  },
  aiLlmCallDetail(callId: string) {
    return request<AiLlmCall>(`/ai-observability/llm-calls/${callId}`);
  },
  aiLlmCallRaw(callId: string, type: AiRawPayloadType) {
    return request<LlmCallRawPayload>(`/ai-observability/llm-calls/${callId}/raw${queryString({ type })}`);
  },
  aiObservabilityStats(params: Record<string, unknown>) {
    return request<AiObservabilityStats>(`/ai-observability/stats${queryString(params)}`);
  },
};
