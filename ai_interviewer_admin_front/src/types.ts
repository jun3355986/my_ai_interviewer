export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  timestamp?: number;
  traceId?: string;
  success?: boolean;
}

export interface PageResult<T> {
  current: number;
  size: number;
  total: number;
  pages: number;
  records: T[];
}

export interface AdminProfile {
  id: number;
  username: string;
  nickname?: string | null;
  email?: string | null;
  phone?: string | null;
  avatarUrl?: string | null;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  admin: AdminProfile;
  roles: string[];
}

export interface DashboardOverview {
  userCount: number;
  jobCount: number;
  resumeCount: number;
  interviewCount: number;
  evaluationCount: number;
  scoreDistribution: Array<{ range: string; count: number }>;
  interviewTrend: Array<{ date: string; count: number }>;
}

export interface UserRow {
  id: number;
  username: string;
  email?: string | null;
  phone?: string | null;
  nickname?: string | null;
  status?: number | null;
  lastLoginTime?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface JobRow {
  id: number;
  title: string;
  company?: string | null;
  department?: string | null;
  location?: string | null;
  jobType?: string | null;
  skills?: string[];
  status?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface InterviewRow {
  id: string;
  userId?: number | null;
  username?: string | null;
  jobId?: number | null;
  jobTitle?: string | null;
  candidateName?: string | null;
  stage?: string | null;
  status?: number | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface QuestionRow {
  id: number;
  questionCode?: string | null;
  questionText: string;
  answerReference?: string | null;
  questionType?: string | null;
  difficulty?: string | null;
  skillArea?: string | null;
  status?: number | null;
  vectorSyncStatus?: string | null;
  vectorSyncError?: string | null;
  sourceType?: string | null;
  sourceBatchId?: number | null;
  tags?: string[];
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface QuestionImportBatch {
  id: number;
  batchNo: string;
  fileName: string;
  fileUrl?: string | null;
  status: string;
  totalCount: number;
  successCount: number;
  failedCount: number;
  errorMessage?: string | null;
  importedBy?: number | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AuditLogRow {
  id: number;
  adminUserId?: number | null;
  module: string;
  operation: string;
  targetType?: string | null;
  targetId?: string | null;
  requestUri?: string | null;
  requestMethod?: string | null;
  result?: string | null;
  errorMessage?: string | null;
  durationMs?: number | null;
  createdAt?: string | null;
}

export interface AiTraceRow {
  id: string;
  requestId?: string | null;
  userId?: number | null;
  username?: string | null;
  sessionId?: string | null;
  pythonSessionId?: string | null;
  businessType: string;
  entrypoint?: string | null;
  status: string;
  errorCode?: string | null;
  errorMessage?: string | null;
  totalTokens?: number | null;
  llmCallCount?: number | null;
  stepCount?: number | null;
  failedLlmCalls?: number | null;
  fallbackUsed?: boolean | null;
  provider?: string | null;
  model?: string | null;
  providerPromptCacheTokenHitRate?: number | null;
  providerPromptCacheCallHitRate?: number | null;
  durationMs?: number | null;
  startedAt: string;
  endedAt?: string | null;
  createdAt?: string | null;
}

export interface AiTraceStep {
  id: string;
  traceId?: string | null;
  stepOrder?: number | null;
  stepType?: string | null;
  stepName?: string | null;
  status?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
  durationMs?: number | null;
  metadataJson?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
}

export interface AiLlmCall {
  id: string;
  traceId?: string | null;
  stepId?: string | null;
  callType?: string | null;
  provider?: string | null;
  model?: string | null;
  fallbackUsed?: boolean | null;
  fallbackFromModel?: string | null;
  status?: string | null;
  promptTokens?: number | null;
  completionTokens?: number | null;
  totalTokens?: number | null;
  tokenSource?: string | null;
  promptCacheHitTokens?: number | null;
  promptCacheMissTokens?: number | null;
  promptCacheHitRate?: number | null;
  cacheReportedByProvider?: boolean | null;
  latencyMs?: number | null;
  rawUsageJson?: string | null;
  metadataJson?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
  createdAt?: string | null;
}

export interface AiTraceDetail extends AiTraceRow {
  metadataJson?: string | null;
  steps: AiTraceStep[];
  llmCalls: AiLlmCall[];
}

export interface AiObservabilityStats {
  totalTraces?: number;
  traceCount?: number;
  totalLlmCalls: number;
  totalTokens: number;
  failedCalls?: number;
  failedLlmCalls?: number;
  llmFailureRate?: number | null;
  avgDurationMs?: number | null;
  averageLatencyMs?: number | null;
  totalPromptTokens?: number | null;
  totalCompletionTokens?: number | null;
  providerPromptCacheHitTokens?: number | null;
  providerPromptCacheMissTokens?: number | null;
  providerPromptCacheHitCalls?: number | null;
  providerCacheReportedCalls?: number | null;
  providerPromptCacheTokenHitRate?: number | null;
  providerPromptCacheCallHitRate?: number | null;
  providerCacheUnreportedCalls: number;
  highConsumptionCallTypes?: Array<{
    callType?: string | null;
    callCount?: number | null;
    totalTokens?: number | null;
  }>;
}

export type AiRawPayloadType = 'PROMPT' | 'RESPONSE';

export interface LlmCallRawPayload {
  callId: string;
  traceId?: string | null;
  accessType: AiRawPayloadType;
  promptText?: string | null;
  responseText?: string | null;
  rawText?: string | null;
}

export interface JobCreatePayload {
  title: string;
  company?: string;
  department?: string;
  location?: string;
  jobType?: string;
  description?: string;
  requirements?: string;
  skills: string[];
  status: number;
}

export interface QuestionCreatePayload {
  questionText: string;
  answerReference?: string;
  questionType: string;
  difficulty: string;
  skillArea?: string;
  status: number;
  tags: string[];
}
