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

export interface QuestionMedia {
  id?: number;
  questionId?: number;
  mediaType?: string;
  mediaUrl: string;
  caption?: string | null;
  altText?: string | null;
  sortOrder?: number | null;
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
  media?: QuestionMedia[];
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
  media?: Array<{ type: string; url: string; caption?: string; alt?: string }>;
}

/* ─── 面试门户（透传 interview 微服务 durable 链路） ─── */

export interface LineageSummary {
  lineageId: string;
  rootSessionId: string;
  candidateName?: string | null;
  resumeId?: number | null;
  jobId?: number | null;
  jobTitle?: string | null;
  branchCount?: number | null;
  activeBranchCount?: number | null;
  completedBranchCount?: number | null;
  bestCompletedScore?: number | null;
  latestActivityAt?: string | null;
  focusedBranchId: string;
  focusedBranchStage?: string | null;
  focusedBranchStageDisplay?: string | null;
  focusedBranchStatus?: number | null;
  focusedBranchProgress?: number | null;
}

export interface LineageTreeNode {
  branchId: string;
  parentBranchId?: string | null;
  branchLabel: string;
  forkPointMessageId?: number | null;
  forkTriggerMessageId?: number | null;
  stage?: string | null;
  status?: number | null;
  branchVersion?: number | null;
  latestBusinessActivityAt?: string | null;
  progress?: number | null;
  ownedAssessmentCount?: number | null;
  inheritedAssessmentCount?: number | null;
  totalAssessmentCount?: number | null;
  completedScore?: number | null;
  evaluationSummary?: string | null;
  recoverableTurnId?: string | null;
  recoverableTurnStatus?: string | null;
  recoverableTurnErrorCode?: string | null;
}

export interface LineageTree {
  lineageId: string;
  rootBranchId: string;
  focusedBranchId: string;
  nodes: LineageTreeNode[];
}

export interface BranchMessage {
  id: number;
  owningBranchId: string;
  role: 'human' | 'ai' | 'system';
  messageType?: string | null;
  content: string;
  stage?: string | null;
  sequence?: number | null;
  expectsResponse?: boolean | null;
  deliveryStatus?: string | null;
  inherited?: boolean | null;
  forkable?: boolean | null;
  forkPointMessageId?: number | null;
  metadata?: Record<string, unknown> | null;
  createdAt?: string | null;
}

export interface BranchTranscript {
  lineageId: string;
  branchId: string;
  branchLabel: string;
  parentBranchId?: string | null;
  forkPointMessageId?: number | null;
  stage?: string | null;
  status?: number | null;
  branchVersion: number;
  messages: BranchMessage[];
}

export interface TurnAttempt {
  turnId: string;
  lineageId: string;
  branchId: string;
  expectedBranchVersion?: number | null;
  expectedTailMessageId?: number | null;
  candidateAnswer?: string | null;
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'INTERRUPTED' | 'CANCEL_REQUESTED' | 'CANCELLED' | 'DISCARDED';
  retryOfTurnId?: string | null;
  errorCode?: string | null;
  createdAt?: string | null;
  completedAt?: string | null;
  failedAt?: string | null;
  cancelledAt?: string | null;
  updatedAt?: string | null;
}

export interface StartAttemptResult {
  lineageId: string;
  branchId: string;
  attempt: TurnAttempt;
}

export interface ForkAttemptResult {
  branchId: string;
  attempt: TurnAttempt;
}

export interface ResumeUploadResult {
  resumeId: number;
  filename: string;
  name?: string | null;
  jobIntent?: string | null;
  workYears?: string | null;
  education?: string | null;
  university?: string | null;
  major?: string | null;
  skillCount?: number;
  projectCount?: number;
  preview?: string | null;
}

/* ─── 配置 ─── */

export interface SystemConfigItem {
  configKey: string;
  configValue: string | null;
  configGroup?: string | null;
  description?: string | null;
  editable?: boolean | null;
}

export interface InterviewStrategy {
  strategyCode?: string | null;
  strategyName?: string | null;
  jobType?: string | null;
  difficulty?: string | null;
  questionCount?: number | null;
  durationMinutes?: number | null;
  scoringRule?: {
    questionTypes?: string[];
    difficultyRatio?: Record<string, number>;
  } | null;
  enabled?: boolean | null;
}

export interface ModelRuntimeConfig {
  chat_model: string;
  chat_fallback_models: string[];
  embedding_model: string;
  embedding_dimension: number;
  vector_collection: string;
  retrieval_top_k: number;
  retrieval_keyword_fallback: boolean;
  overridden_keys: string[];
}

export interface ModelConfigTestResult {
  chat: { ok: boolean; model: string; latency_ms: number; error?: string };
  embedding: { ok: boolean; model: string; dimension?: number; latency_ms: number; error?: string };
  all_ok: boolean;
}

export type EvaluationDetail = {
  id: number;
  sessionId: string;
  userId?: number | null;
  username?: string | null;
  jobId?: number | null;
  jobTitle?: string | null;
  overallScore?: number | null;
  technicalScore?: number | null;
  communicationScore?: number | null;
  logicScore?: number | null;
  experienceScore?: number | null;
  summary?: string | null;
  strengths?: string | null;
  weaknesses?: string | null;
  recommendation?: string | null;
  detailedFeedback?: string | null;
  totalQuestions?: number | null;
  answeredQuestions?: number | null;
  averageScore?: number | null;
  durationMinutes?: number | null;
  createdAt?: string | null;
};
