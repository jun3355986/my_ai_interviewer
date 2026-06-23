import { type FormEvent, type ReactNode, useEffect, useRef, useState } from 'react';
import { adminApi, clearSession, getToken, readProfile, saveSession } from './api';
import type {
  AdminProfile,
  AiLlmCall,
  AiObservabilityStats,
  AiRawPayloadType,
  AiTraceDetail,
  AiTraceRow,
  AuditLogRow,
  DashboardOverview,
  InterviewRow,
  JobCreatePayload,
  JobRow,
  PageResult,
  QuestionCreatePayload,
  QuestionImportBatch,
  QuestionRow,
  UserRow,
} from './types';

type ViewKey = 'dashboard' | 'users' | 'jobs' | 'interviews' | 'questions' | 'audit' | 'aiObservability';

const views: Array<{ key: ViewKey; label: string; eyebrow: string }> = [
  { key: 'dashboard', label: '运营总览', eyebrow: 'Overview' },
  { key: 'users', label: '用户管理', eyebrow: 'Users' },
  { key: 'jobs', label: '职位管理', eyebrow: 'Jobs' },
  { key: 'interviews', label: '面试监控', eyebrow: 'Interviews' },
  { key: 'questions', label: '题库管理', eyebrow: 'Question Bank' },
  { key: 'aiObservability', label: 'AI 观测', eyebrow: 'AI Observability' },
  { key: 'audit', label: '审计日志', eyebrow: 'Audit' },
];

const aiObservabilityStatusOptions = [
  { value: 'SUCCESS', label: 'Success' },
  { value: 'ERROR', label: 'Error' },
  { value: 'RUNNING', label: 'Running' },
];

const aiObservabilityCallTypeOptions = [
  { value: 'generate_opening', label: 'Generate opening' },
  { value: 'ask_self_introduction', label: 'Ask self introduction' },
  { value: 'generate_project_questions', label: 'Generate project questions' },
  { value: 'evaluate_answer', label: 'Evaluate answer' },
  { value: 'generate_followup_question', label: 'Generate follow-up question' },
  { value: 'conclude_interview', label: 'Conclude interview' },
  { value: 'ask', label: 'Ask' },
];

function formatDate(value?: string | null) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

function statusText(value?: number | null) {
  if (value === 1) {
    return '启用';
  }
  if (value === 0) {
    return '停用';
  }
  if (value === 2) {
    return '已完成';
  }
  return value == null ? '-' : String(value);
}

function questionStatusText(value?: number | null) {
  if (value === 1) {
    return '已上架';
  }
  if (value === 0) {
    return '已下架';
  }
  if (value === 2) {
    return '待审核';
  }
  if (value === 3) {
    return '已驳回';
  }
  return value == null ? '-' : String(value);
}

function questionStatusTone(value?: number | null) {
  if (value === 1) {
    return 'good';
  }
  if (value === 2) {
    return 'warn';
  }
  if (value === 3) {
    return 'danger';
  }
  return 'muted';
}

function statusTone(value?: number | null) {
  if (value === 1) {
    return 'good';
  }
  if (value === 0) {
    return 'muted';
  }
  if (value === 2) {
    return 'info';
  }
  return 'warn';
}

function textStatusTone(value?: string | null) {
  const normalized = (value || '').toUpperCase();
  if (['SUCCESS', 'COMPLETED', 'OK'].includes(normalized)) {
    return 'good';
  }
  if (['FAILED', 'ERROR', 'TIMEOUT'].includes(normalized)) {
    return 'danger';
  }
  if (['RUNNING', 'PROCESSING', 'PENDING'].includes(normalized)) {
    return 'info';
  }
  return 'muted';
}

function formatNumber(value?: number | null) {
  return value == null ? '-' : Intl.NumberFormat('zh-CN').format(value);
}

function formatDuration(value?: number | null) {
  return value == null ? '-' : `${Intl.NumberFormat('zh-CN').format(Math.round(value))}ms`;
}

function formatPercent(value?: number | null) {
  if (value == null) {
    return '-';
  }
  return `${(value * 100).toFixed(2)}%`;
}

function compactId(value?: string | null) {
  if (!value) {
    return '-';
  }
  return value.length > 14 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value;
}

function providerModel(call?: Pick<AiLlmCall, 'provider' | 'model'> | null) {
  if (!call?.provider && !call?.model) {
    return '-';
  }
  return `${call.provider || '-'} / ${call.model || '-'}`;
}

function statNumber(stats: AiObservabilityStats | null, frontendKey: keyof AiObservabilityStats, backendKey?: keyof AiObservabilityStats) {
  if (!stats) {
    return 0;
  }
  const direct = stats[frontendKey];
  const fallback = backendKey ? stats[backendKey] : undefined;
  const value = typeof direct === 'number' ? direct : typeof fallback === 'number' ? fallback : 0;
  return value;
}

function statRate(stats: AiObservabilityStats | null, frontendKey: keyof AiObservabilityStats, backendKey?: keyof AiObservabilityStats) {
  if (!stats) {
    return null;
  }
  const direct = stats[frontendKey];
  const fallback = backendKey ? stats[backendKey] : undefined;
  return typeof direct === 'number' ? direct : typeof fallback === 'number' ? fallback : null;
}

function splitList(value: string) {
  return value
    .split(/[,\n，]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function compactPayload<T extends object>(payload: T): T {
  const next = { ...payload } as Record<string, unknown>;
  Object.keys(next).forEach((key) => {
    if (next[key] === '') {
      delete next[key];
    }
  });
  return next as T;
}

export default function App() {
  const [profile, setProfile] = useState<AdminProfile | null>(() => readProfile());
  const [activeView, setActiveView] = useState<ViewKey>('dashboard');
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [pageData, setPageData] = useState<PageResult<unknown> | null>(null);
  const [importBatches, setImportBatches] = useState<PageResult<QuestionImportBatch> | null>(null);
  const [aiStats, setAiStats] = useState<AiObservabilityStats | null>(null);
  const [selectedTraceId, setSelectedTraceId] = useState<string | null>(null);
  const [traceDetail, setTraceDetail] = useState<AiTraceDetail | null>(null);
  const [traceDetailLoading, setTraceDetailLoading] = useState(false);
  const [aiProvider, setAiProvider] = useState('');
  const [aiModel, setAiModel] = useState('');
  const [aiCallType, setAiCallType] = useState('');
  const [rawPayloads, setRawPayloads] = useState<Record<string, Partial<Record<AiRawPayloadType, string>>>>({});
  const [rawLoadingKey, setRawLoadingKey] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [jobDialogOpen, setJobDialogOpen] = useState(false);
  const [questionDialogOpen, setQuestionDialogOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const isLoggedIn = Boolean(getToken());

  useEffect(() => {
    if (!isLoggedIn) {
      return;
    }
    void loadCurrentView();
  }, [isLoggedIn, activeView, currentPage, reloadKey]);

  function switchView(view: ViewKey) {
    setActiveView(view);
    setQuery('');
    setStatus('');
    setAiProvider('');
    setAiModel('');
    setAiCallType('');
    setCurrentPage(1);
    setPageData(null);
    setAiStats(null);
    setSelectedTraceId(null);
    setTraceDetail(null);
    setRawPayloads({});
    setError('');
  }

  async function loadCurrentView() {
    setLoading(true);
    setError('');
    try {
      if (activeView === 'dashboard') {
        setOverview(await adminApi.dashboard());
        setPageData(null);
        setImportBatches(null);
        setAiStats(null);
      } else if (activeView === 'aiObservability') {
        const params = aiObservabilityParams();
        const [traces, stats] = await Promise.all([
          adminApi.aiTraces(params),
          adminApi.aiObservabilityStats(params),
        ]);
        setPageData(traces);
        setAiStats(stats);
        setImportBatches(null);
        const nextTraceId = traces.records.find((row) => row.id === selectedTraceId)?.id || traces.records[0]?.id || null;
        if (nextTraceId) {
          await loadAiTraceDetail(nextTraceId);
        } else {
          setSelectedTraceId(null);
          setTraceDetail(null);
          setRawPayloads({});
        }
      } else {
        if (activeView === 'questions') {
          const [questions, imports] = await Promise.all([
            loadPage(activeView),
            adminApi.questionImports({ current: 1, size: 5 }),
          ]);
          setPageData(questions);
          setImportBatches(imports);
        } else {
          setPageData(await loadPage(activeView));
          setImportBatches(null);
        }
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : '加载失败';
      setError(message);
      if (message.includes('未认证') || message.includes('Unauthorized')) {
        clearSession();
        setProfile(null);
      }
    } finally {
      setLoading(false);
    }
  }

  function loadPage(view: Exclude<ViewKey, 'dashboard' | 'aiObservability'>) {
    const baseParams = { current: currentPage, size: 10 };
    if (view === 'users') {
      return adminApi.users({ ...baseParams, username: query, status });
    }
    if (view === 'jobs') {
      return adminApi.jobs({ ...baseParams, title: query, status });
    }
    if (view === 'interviews') {
      return adminApi.interviews({ ...baseParams, stage: query, status });
    }
    if (view === 'questions') {
      return adminApi.questions({ ...baseParams, keyword: query, status });
    }
    return adminApi.auditLogs({ ...baseParams, module: query });
  }

  function aiObservabilityParams() {
    const baseParams = { current: currentPage, size: 10 };
    return {
      ...baseParams,
      requestId: query,
      status,
      provider: aiProvider,
      model: aiModel,
      callType: aiCallType,
    };
  }

  async function loadAiTraceDetail(traceId: string) {
    setSelectedTraceId(traceId);
    setTraceDetailLoading(true);
    setRawPayloads({});
    try {
      setTraceDetail(await adminApi.aiTraceDetail(traceId));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 调用链路详情加载失败');
    } finally {
      setTraceDetailLoading(false);
    }
  }

  async function revealAiRawPayload(callId: string, type: AiRawPayloadType) {
    const key = `${callId}:${type}`;
    setRawLoadingKey(key);
    try {
      const payload = await adminApi.aiLlmCallRaw(callId, type);
      setRawPayloads((current) => ({
        ...current,
        [callId]: {
          ...(current[callId] || {}),
          [type]: payload.rawText || payload.promptText || payload.responseText || '',
        },
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '原文读取失败');
    } finally {
      setRawLoadingKey('');
    }
  }

  async function handleLogin(login: { profile: AdminProfile }) {
    setProfile(login.profile);
    setActiveView('dashboard');
    setReloadKey((value) => value + 1);
  }

  function logout() {
    clearSession();
    setProfile(null);
    setOverview(null);
    setPageData(null);
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setCurrentPage(1);
    setReloadKey((value) => value + 1);
  }

  async function disableUser(user: UserRow) {
    if (!window.confirm(`确认停用用户 ${user.username}？`)) {
      return;
    }
    await adminApi.disableUser(user.id);
    setReloadKey((value) => value + 1);
  }

  async function resetPassword(user: UserRow) {
    const next = window.prompt(`请输入 ${user.username} 的新密码，至少 6 位`);
    if (!next) {
      return;
    }
    await adminApi.resetPassword(user.id, next);
    window.alert('密码已重置');
  }

  async function syncQuestions() {
    await adminApi.syncQuestions();
    window.alert('题库向量同步任务已触发');
    setReloadKey((value) => value + 1);
  }

  async function importQuestions(file: File | null) {
    if (!file) {
      return;
    }
    await adminApi.importQuestions(file);
    window.alert('题库文件已导入，默认进入待审核状态');
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
    setReloadKey((value) => value + 1);
  }

  async function runQuestionAction(row: QuestionRow, action: 'approve' | 'reject' | 'publish' | 'unpublish' | 'delete') {
    const labels = {
      approve: '审核通过',
      reject: '驳回',
      publish: '上架',
      unpublish: '下架',
      delete: '删除',
    };
    if (!window.confirm(`确认${labels[action]}题目 #${row.id}？`)) {
      return;
    }
    if (action === 'approve') {
      await adminApi.approveQuestion(row.id);
    } else if (action === 'reject') {
      await adminApi.rejectQuestion(row.id);
    } else if (action === 'publish') {
      await adminApi.publishQuestion(row.id);
    } else if (action === 'unpublish') {
      await adminApi.unpublishQuestion(row.id);
    } else {
      await adminApi.deleteQuestion(row.id);
    }
    setReloadKey((value) => value + 1);
  }

  if (!isLoggedIn) {
    return <LoginScreen onLogin={handleLogin} />;
  }

  const currentView = views.find((item) => item.key === activeView) || views[0];

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-card">
          <div className="brand-mark">AI</div>
          <div>
            <strong>Interview Admin</strong>
            <span>后台管理控制台</span>
          </div>
        </div>
        <nav className="nav-list">
          {views.map((item) => (
            <button
              key={item.key}
              type="button"
              className={activeView === item.key ? 'nav-item active' : 'nav-item'}
              onClick={() => switchView(item.key)}
            >
              <span>{item.eyebrow}</span>
              {item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span>Gateway</span>
          <code>/admin/**</code>
        </div>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <p>{currentView.eyebrow}</p>
            <h1>{currentView.label}</h1>
          </div>
          <div className="profile-pill">
            <span>{profile?.nickname || profile?.username || 'Admin'}</span>
            <button type="button" onClick={logout}>
              退出
            </button>
          </div>
        </header>

        {error && <div className="error-banner">{error}</div>}
        {activeView === 'dashboard' ? (
          <DashboardPanel overview={overview} loading={loading} />
        ) : activeView === 'aiObservability' ? (
          <AiObservabilityPanel
            stats={aiStats}
            pageData={pageData as PageResult<AiTraceRow> | null}
            loading={loading}
            query={query}
            status={status}
            provider={aiProvider}
            model={aiModel}
            callType={aiCallType}
            selectedTraceId={selectedTraceId}
            traceDetail={traceDetail}
            detailLoading={traceDetailLoading}
            rawPayloads={rawPayloads}
            rawLoadingKey={rawLoadingKey}
            onQueryChange={setQuery}
            onStatusChange={setStatus}
            onProviderChange={setAiProvider}
            onModelChange={setAiModel}
            onCallTypeChange={setAiCallType}
            onSubmit={submitSearch}
            onSelectTrace={(traceId) => void loadAiTraceDetail(traceId)}
            onRevealRaw={(callId, type) => void revealAiRawPayload(callId, type)}
            onPageChange={setCurrentPage}
          />
        ) : (
          <section className="panel">
            <div className="panel-header">
              <form className="filter-row" onSubmit={submitSearch}>
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder={filterPlaceholder(activeView)}
                />
                {activeView !== 'audit' && (
                  <select value={status} onChange={(event) => setStatus(event.target.value)}>
                    <option value="">全部状态</option>
                    {activeView === 'questions' ? (
                      <>
                        <option value="2">待审核</option>
                        <option value="1">已上架</option>
                        <option value="0">已下架</option>
                        <option value="3">已驳回</option>
                      </>
                    ) : (
                      <>
                        <option value="1">启用/进行中</option>
                        <option value="0">停用/关闭</option>
                        <option value="2">已完成</option>
                      </>
                    )}
                  </select>
                )}
                <button type="submit">查询</button>
              </form>
              <div className="action-row">
                {activeView === 'jobs' && <button onClick={() => setJobDialogOpen(true)}>新建职位</button>}
                {activeView === 'questions' && (
                  <>
                    <button onClick={() => setQuestionDialogOpen(true)}>新建题目</button>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept=".csv,.pdf,.md,.docx,.txt"
                      hidden
                      onChange={(event) => void importQuestions(event.target.files?.[0] || null)}
                    />
                    <button className="secondary" onClick={() => fileInputRef.current?.click()}>
                      导入题库
                    </button>
                    <button className="secondary" onClick={syncQuestions}>
                      向量同步
                    </button>
                  </>
                )}
              </div>
            </div>

            <DataTable
              view={activeView}
              pageData={pageData}
              loading={loading}
              onDisableUser={disableUser}
              onResetPassword={resetPassword}
              onQuestionAction={runQuestionAction}
            />
            {activeView === 'questions' && <QuestionImportBatchPanel pageData={importBatches} />}
            <Pagination pageData={pageData} onPageChange={setCurrentPage} />
          </section>
        )}
      </main>

      {jobDialogOpen && (
        <JobDialog
          onClose={() => setJobDialogOpen(false)}
          onSaved={() => {
            setJobDialogOpen(false);
            setReloadKey((value) => value + 1);
          }}
        />
      )}
      {questionDialogOpen && (
        <QuestionDialog
          onClose={() => setQuestionDialogOpen(false)}
          onSaved={() => {
            setQuestionDialogOpen(false);
            setReloadKey((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}

function LoginScreen({ onLogin }: { onLogin: (login: { profile: AdminProfile }) => void }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin123');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      const login = await adminApi.login(username, password);
      saveSession(login);
      onLogin({ profile: login.admin });
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-hero">
        <div className="orb orb-a" />
        <div className="orb orb-b" />
        <div className="login-card">
          <p className="eyebrow">AI Interviewer Admin</p>
          <h1>把面试系统后台握在一个清晰界面里</h1>
          <p className="subcopy">管理用户、职位、面试、题库和审计日志，所有请求统一进入 Gateway。</p>
          <form onSubmit={submit} className="login-form">
            <label>
              账号
              <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
            </label>
            <label>
              密码
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
              />
            </label>
            {error && <div className="form-error">{error}</div>}
            <button type="submit" disabled={loading}>
              {loading ? '登录中...' : '登录后台'}
            </button>
          </form>
        </div>
      </section>
    </main>
  );
}

function DashboardPanel({ overview, loading }: { overview: DashboardOverview | null; loading: boolean }) {
  if (loading && !overview) {
    return <div className="panel loading-block">正在加载运营概览...</div>;
  }

  const cards = [
    { label: '用户', value: overview?.userCount ?? 0 },
    { label: '职位', value: overview?.jobCount ?? 0 },
    { label: '简历', value: overview?.resumeCount ?? 0 },
    { label: '面试', value: overview?.interviewCount ?? 0 },
    { label: '评估', value: overview?.evaluationCount ?? 0 },
  ];
  const trend = overview?.interviewTrend?.slice(-14) || [];
  const maxTrend = Math.max(...trend.map((item) => item.count), 1);
  const maxScore = Math.max(...(overview?.scoreDistribution || []).map((item) => item.count), 1);

  return (
    <div className="dashboard-grid">
      <section className="metric-strip">
        {cards.map((card) => (
          <article key={card.label} className="metric-card">
            <span>{card.label}</span>
            <strong>{card.value}</strong>
          </article>
        ))}
      </section>
      <section className="panel chart-panel">
        <div className="panel-title">
          <p>Last 14 Days</p>
          <h2>面试趋势</h2>
        </div>
        <div className="trend-chart">
          {trend.map((item) => (
            <div key={item.date} className="trend-bar">
              <span style={{ height: `${Math.max(8, (item.count / maxTrend) * 100)}%` }} />
              <small>{item.date.slice(5)}</small>
            </div>
          ))}
        </div>
      </section>
      <section className="panel score-panel">
        <div className="panel-title">
          <p>Scores</p>
          <h2>分数分布</h2>
        </div>
        <div className="score-list">
          {(overview?.scoreDistribution || []).map((item) => (
            <div key={item.range} className="score-row">
              <span>{item.range}</span>
              <div>
                <i style={{ width: `${Math.max(3, (item.count / maxScore) * 100)}%` }} />
              </div>
              <strong>{item.count}</strong>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function AiObservabilityPanel({
  stats,
  pageData,
  loading,
  query,
  status,
  provider,
  model,
  callType,
  selectedTraceId,
  traceDetail,
  detailLoading,
  rawPayloads,
  rawLoadingKey,
  onQueryChange,
  onStatusChange,
  onProviderChange,
  onModelChange,
  onCallTypeChange,
  onSubmit,
  onSelectTrace,
  onRevealRaw,
  onPageChange,
}: {
  stats: AiObservabilityStats | null;
  pageData: PageResult<AiTraceRow> | null;
  loading: boolean;
  query: string;
  status: string;
  provider: string;
  model: string;
  callType: string;
  selectedTraceId: string | null;
  traceDetail: AiTraceDetail | null;
  detailLoading: boolean;
  rawPayloads: Record<string, Partial<Record<AiRawPayloadType, string>>>;
  rawLoadingKey: string;
  onQueryChange: (value: string) => void;
  onStatusChange: (value: string) => void;
  onProviderChange: (value: string) => void;
  onModelChange: (value: string) => void;
  onCallTypeChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  onSelectTrace: (traceId: string) => void;
  onRevealRaw: (callId: string, type: AiRawPayloadType) => void;
  onPageChange: (page: number) => void;
}) {
  const failedCalls = statNumber(stats, 'failedCalls', 'failedLlmCalls');
  const totalCalls = statNumber(stats, 'totalLlmCalls');
  const failureRate = statRate(stats, 'llmFailureRate') ?? (totalCalls > 0 ? failedCalls / totalCalls : 0);
  const averageLatency = statRate(stats, 'avgDurationMs', 'averageLatencyMs');

  return (
    <div className="ai-observability-view">
      <section className="panel">
        <div className="panel-header ai-filter-header">
          <form className="filter-row ai-filter-row" onSubmit={onSubmit}>
            <input value={query} onChange={(event) => onQueryChange(event.target.value)} placeholder="按 Request ID 搜索" />
            <select value={status} onChange={(event) => onStatusChange(event.target.value)}>
              <option value="">全部状态</option>
              {aiObservabilityStatusOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <input value={provider} onChange={(event) => onProviderChange(event.target.value)} placeholder="Provider" />
            <input value={model} onChange={(event) => onModelChange(event.target.value)} placeholder="Model" />
            <select value={callType} onChange={(event) => onCallTypeChange(event.target.value)}>
              <option value="">全部调用类型</option>
              {aiObservabilityCallTypeOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <button type="submit">查询</button>
          </form>
        </div>

        <div className="metric-strip ai-metrics">
          <article className="metric-card compact">
            <span>Traces</span>
            <strong>{formatNumber(statNumber(stats, 'totalTraces', 'traceCount'))}</strong>
          </article>
          <article className="metric-card compact">
            <span>LLM Calls</span>
            <strong>{formatNumber(totalCalls)}</strong>
          </article>
          <article className="metric-card compact">
            <span>Total Tokens</span>
            <strong>{formatNumber(statNumber(stats, 'totalTokens'))}</strong>
          </article>
          <article className="metric-card compact">
            <span>Failure Rate</span>
            <strong>{formatPercent(failureRate)}</strong>
          </article>
          <article className="metric-card compact">
            <span>Avg Duration</span>
            <strong>{formatDuration(averageLatency)}</strong>
          </article>
          <article className="metric-card compact">
            <span>Provider Cache Token Hit Rate</span>
            <strong>{formatPercent(statRate(stats, 'providerPromptCacheTokenHitRate'))}</strong>
          </article>
          <article className="metric-card compact">
            <span>Provider Cache Call Hit Ratio</span>
            <strong>{formatPercent(statRate(stats, 'providerPromptCacheCallHitRate'))}</strong>
          </article>
          <article className="metric-card compact">
            <span>Provider Cache Unreported Calls</span>
            <strong>{formatNumber(statNumber(stats, 'providerCacheUnreportedCalls'))}</strong>
          </article>
        </div>
      </section>

      <section className="ai-observability-grid">
        <div className="panel ai-list-panel">
          <div className="panel-title">
            <p>Traces</p>
            <h2>调用链路</h2>
          </div>
          <AiTraceTable
            pageData={pageData}
            loading={loading}
            selectedTraceId={selectedTraceId}
            onSelectTrace={onSelectTrace}
          />
          <Pagination pageData={pageData as PageResult<unknown> | null} onPageChange={onPageChange} />
        </div>
        <AiTraceDetailPanel
          traceDetail={traceDetail}
          detailLoading={detailLoading}
          rawPayloads={rawPayloads}
          rawLoadingKey={rawLoadingKey}
          onRevealRaw={onRevealRaw}
        />
      </section>
    </div>
  );
}

function AiTraceTable({
  pageData,
  loading,
  selectedTraceId,
  onSelectTrace,
}: {
  pageData: PageResult<AiTraceRow> | null;
  loading: boolean;
  selectedTraceId: string | null;
  onSelectTrace: (traceId: string) => void;
}) {
  if (loading && !pageData) {
    return <div className="loading-block">正在加载 AI 调用链路...</div>;
  }
  const records = pageData?.records || [];
  if (records.length === 0) {
    return <div className="empty-state">暂无 AI 调用链路，可以调整筛选条件。</div>;
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Trace</th>
          <th>Session</th>
          <th>业务</th>
          <th>状态</th>
          <th>Provider / Model</th>
          <th>Tokens</th>
          <th>Cache</th>
          <th>耗时</th>
          <th>开始时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        {records.map((row) => (
          <tr key={row.id} className={selectedTraceId === row.id ? 'selected-row' : undefined}>
            <td className="mono-cell">
              {compactId(row.id)}
              <small>{row.requestId || '-'}</small>
            </td>
            <td>{row.sessionId || row.pythonSessionId || '-'}</td>
            <td>
              <strong>{row.businessType || '-'}</strong>
              <small>{row.entrypoint || '-'}</small>
            </td>
            <td>
              <span className={`status ${textStatusTone(row.status)}`}>{row.status || '-'}</span>
              {row.fallbackUsed && <small className="warn-text">fallback</small>}
            </td>
            <td>{providerModel(row)}</td>
            <td>
              {formatNumber(row.totalTokens)}
              <small>{formatNumber(row.llmCallCount)} calls</small>
            </td>
            <td>
              <span>{formatPercent(row.providerPromptCacheTokenHitRate)}</span>
              <small>{formatPercent(row.providerPromptCacheCallHitRate)} calls</small>
            </td>
            <td>{formatDuration(row.durationMs)}</td>
            <td>{formatDate(row.startedAt)}</td>
            <td className="table-actions">
              <button type="button" onClick={() => onSelectTrace(row.id)}>
                查看
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function AiTraceDetailPanel({
  traceDetail,
  detailLoading,
  rawPayloads,
  rawLoadingKey,
  onRevealRaw,
}: {
  traceDetail: AiTraceDetail | null;
  detailLoading: boolean;
  rawPayloads: Record<string, Partial<Record<AiRawPayloadType, string>>>;
  rawLoadingKey: string;
  onRevealRaw: (callId: string, type: AiRawPayloadType) => void;
}) {
  if (detailLoading && !traceDetail) {
    return <section className="panel ai-detail-panel loading-block">正在加载链路详情...</section>;
  }
  if (!traceDetail) {
    return <section className="panel ai-detail-panel empty-state">请选择一条 AI 调用链路。</section>;
  }

  return (
    <section className="panel ai-detail-panel">
      <div className="panel-title">
        <p>Trace Detail</p>
        <h2>{compactId(traceDetail.id)}</h2>
      </div>
      <div className="trace-summary">
        <span className={`status ${textStatusTone(traceDetail.status)}`}>{traceDetail.status}</span>
        <strong>{traceDetail.businessType}</strong>
        <span>{traceDetail.sessionId || traceDetail.pythonSessionId || '-'}</span>
        <span>{formatDuration(traceDetail.durationMs)}</span>
      </div>
      {traceDetail.errorMessage && <div className="error-banner compact">{traceDetail.errorMessage}</div>}

      <section className="detail-section">
        <div className="panel-title compact-title">
          <p>Timeline</p>
          <h3>Step Timeline</h3>
        </div>
        {traceDetail.steps.length === 0 ? (
          <div className="empty-state compact">暂无步骤记录。</div>
        ) : (
          <ol className="timeline">
            {traceDetail.steps.map((step) => (
              <li key={step.id}>
                <span className={`status ${textStatusTone(step.status)}`}>{step.status || '-'}</span>
                <div>
                  <strong>{step.stepName || step.stepType || `Step ${step.stepOrder || ''}`}</strong>
                  <small>
                    {step.stepType || '-'} · {formatDuration(step.durationMs)} · {formatDate(step.startedAt)}
                  </small>
                  {step.errorMessage && <em>{step.errorMessage}</em>}
                </div>
              </li>
            ))}
          </ol>
        )}
      </section>

      <section className="detail-section">
        <div className="panel-title compact-title">
          <p>LLM Calls</p>
          <h3>关联调用</h3>
        </div>
        {traceDetail.llmCalls.length === 0 ? (
          <div className="empty-state compact">暂无 LLM 调用。</div>
        ) : (
          <div className="llm-call-list">
            {traceDetail.llmCalls.map((call) => {
              const promptRaw = rawPayloads[call.id]?.PROMPT;
              const responseRaw = rawPayloads[call.id]?.RESPONSE;
              return (
                <article key={call.id} className="llm-call-card">
                  <header>
                    <div>
                      <strong>{providerModel(call)}</strong>
                      <small>
                        {call.callType || '-'} · {compactId(call.id)}
                      </small>
                    </div>
                    <span className={`status ${textStatusTone(call.status)}`}>{call.status || '-'}</span>
                  </header>
                  <div className="call-metrics">
                    <span>
                      Tokens <strong>{formatNumber(call.totalTokens)}</strong>
                    </span>
                    <span>
                      Prompt <strong>{formatNumber(call.promptTokens)}</strong>
                    </span>
                    <span>
                      Completion <strong>{formatNumber(call.completionTokens)}</strong>
                    </span>
                    <span>
                      Latency <strong>{formatDuration(call.latencyMs)}</strong>
                    </span>
                    <span>
                      Cache Hit <strong>{formatPercent(call.promptCacheHitRate)}</strong>
                    </span>
                    <span>
                      Provider Cache <strong>{call.cacheReportedByProvider ? 'reported' : 'unreported'}</strong>
                    </span>
                  </div>
                  {call.fallbackUsed && (
                    <div className="fallback-note">
                      Fallback from <strong>{call.fallbackFromModel || '-'}</strong>
                    </div>
                  )}
                  {call.errorMessage && <div className="form-error compact">{call.errorMessage}</div>}
                  <div className="raw-actions">
                    <button
                      type="button"
                      className="secondary"
                      disabled={rawLoadingKey === `${call.id}:PROMPT`}
                      onClick={() => onRevealRaw(call.id, 'PROMPT')}
                    >
                      Reveal Prompt
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      disabled={rawLoadingKey === `${call.id}:RESPONSE`}
                      onClick={() => onRevealRaw(call.id, 'RESPONSE')}
                    >
                      Reveal Response
                    </button>
                  </div>
                  {promptRaw != null && <pre className="raw-block">{promptRaw || 'EMPTY PROMPT'}</pre>}
                  {responseRaw != null && <pre className="raw-block">{responseRaw || 'EMPTY RESPONSE'}</pre>}
                </article>
              );
            })}
          </div>
        )}
      </section>
    </section>
  );
}

function DataTable({
  view,
  pageData,
  loading,
  onDisableUser,
  onResetPassword,
  onQuestionAction,
}: {
  view: Exclude<ViewKey, 'dashboard' | 'aiObservability'>;
  pageData: PageResult<unknown> | null;
  loading: boolean;
  onDisableUser: (user: UserRow) => void;
  onResetPassword: (user: UserRow) => void;
  onQuestionAction: (question: QuestionRow, action: 'approve' | 'reject' | 'publish' | 'unpublish' | 'delete') => void;
}) {
  if (loading && !pageData) {
    return <div className="loading-block">正在加载数据...</div>;
  }
  const records = pageData?.records || [];
  if (records.length === 0) {
    return <div className="empty-state">暂无数据，可以调整筛选条件或创建新记录。</div>;
  }

  if (view === 'users') {
    return (
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>用户</th>
            <th>邮箱</th>
            <th>状态</th>
            <th>最后登录</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {(records as UserRow[]).map((row) => (
            <tr key={row.id}>
              <td>{row.id}</td>
              <td>
                <strong>{row.username}</strong>
                <small>{row.nickname || '-'}</small>
              </td>
              <td>{row.email || '-'}</td>
              <td>
                <span className={`status ${statusTone(row.status)}`}>{statusText(row.status)}</span>
              </td>
              <td>{formatDate(row.lastLoginTime)}</td>
              <td className="table-actions">
                <button onClick={() => onResetPassword(row)}>重置密码</button>
                {row.status === 1 && <button onClick={() => onDisableUser(row)}>停用</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  }

  if (view === 'jobs') {
    return (
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>职位</th>
            <th>公司</th>
            <th>地点</th>
            <th>技能</th>
            <th>状态</th>
            <th>更新时间</th>
          </tr>
        </thead>
        <tbody>
          {(records as JobRow[]).map((row) => (
            <tr key={row.id}>
              <td>{row.id}</td>
              <td>
                <strong>{row.title}</strong>
                <small>{row.jobType || '-'}</small>
              </td>
              <td>{row.company || '-'}</td>
              <td>{row.location || '-'}</td>
              <td>{(row.skills || []).join(' / ') || '-'}</td>
              <td>
                <span className={`status ${statusTone(row.status)}`}>{statusText(row.status)}</span>
              </td>
              <td>{formatDate(row.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  }

  if (view === 'interviews') {
    return (
      <table>
        <thead>
          <tr>
            <th>Session</th>
            <th>用户</th>
            <th>职位</th>
            <th>阶段</th>
            <th>状态</th>
            <th>开始时间</th>
            <th>结束时间</th>
          </tr>
        </thead>
        <tbody>
          {(records as InterviewRow[]).map((row) => (
            <tr key={row.id}>
              <td className="mono-cell">{row.id.slice(0, 10)}...</td>
              <td>{row.username || row.userId || '-'}</td>
              <td>{row.jobTitle || '-'}</td>
              <td>{row.stage || '-'}</td>
              <td>
                <span className={`status ${statusTone(row.status)}`}>{statusText(row.status)}</span>
              </td>
              <td>{formatDate(row.startedAt)}</td>
              <td>{formatDate(row.finishedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  }

  if (view === 'questions') {
    return (
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>题目</th>
            <th>类型</th>
            <th>难度</th>
            <th>技能域</th>
            <th>状态</th>
            <th>向量状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {(records as QuestionRow[]).map((row) => (
            <tr key={row.id}>
              <td>{row.id}</td>
              <td className="wide-cell">{row.questionText}</td>
              <td>{row.questionType || '-'}</td>
              <td>{row.difficulty || '-'}</td>
              <td>{row.skillArea || '-'}</td>
              <td>
                <span className={`status ${questionStatusTone(row.status)}`}>{questionStatusText(row.status)}</span>
              </td>
              <td>
                <span className="status info">{row.vectorSyncStatus || '-'}</span>
                {row.vectorSyncError && <small className="error-text">{row.vectorSyncError}</small>}
              </td>
              <td>{formatDate(row.updatedAt)}</td>
              <td className="table-actions">
                {row.status === 2 && <button onClick={() => onQuestionAction(row, 'approve')}>通过</button>}
                {row.status === 2 && <button onClick={() => onQuestionAction(row, 'reject')}>驳回</button>}
                {(row.status === 0 || row.status === 3) && <button onClick={() => onQuestionAction(row, 'publish')}>上架</button>}
                {row.status === 1 && <button onClick={() => onQuestionAction(row, 'unpublish')}>下架</button>}
                <button onClick={() => onQuestionAction(row, 'delete')}>删除</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  }

  return (
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>模块</th>
          <th>操作</th>
          <th>路径</th>
          <th>结果</th>
          <th>耗时</th>
          <th>时间</th>
        </tr>
      </thead>
      <tbody>
        {(records as AuditLogRow[]).map((row) => (
          <tr key={row.id}>
            <td>{row.id}</td>
            <td>{row.module}</td>
            <td>{row.operation}</td>
            <td className="wide-cell">{row.requestUri || '-'}</td>
            <td>
              <span className={`status ${row.result === 'SUCCESS' ? 'good' : 'warn'}`}>{row.result || '-'}</span>
            </td>
            <td>{row.durationMs == null ? '-' : `${row.durationMs}ms`}</td>
            <td>{formatDate(row.createdAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function QuestionImportBatchPanel({ pageData }: { pageData: PageResult<QuestionImportBatch> | null }) {
  const records = pageData?.records || [];
  return (
    <section className="sub-panel">
      <div className="panel-title">
        <p>Imports</p>
        <h2>最近导入批次</h2>
      </div>
      {records.length === 0 ? (
        <div className="empty-state compact">暂无导入批次。</div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>批次号</th>
              <th>文件</th>
              <th>状态</th>
              <th>成功/失败</th>
              <th>错误</th>
              <th>完成时间</th>
            </tr>
          </thead>
          <tbody>
            {records.map((row) => (
              <tr key={row.id}>
                <td className="mono-cell">{row.batchNo.slice(0, 14)}...</td>
                <td>{row.fileName}</td>
                <td>
                  <span className={`status ${row.status === 'SUCCESS' ? 'good' : row.status === 'PROCESSING' ? 'info' : 'warn'}`}>
                    {row.status}
                  </span>
                </td>
                <td>
                  {row.successCount}/{row.failedCount}
                  <small>共 {row.totalCount} 道</small>
                </td>
                <td className="wide-cell">{row.errorMessage || '-'}</td>
                <td>{formatDate(row.finishedAt || row.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function Pagination({
  pageData,
  onPageChange,
}: {
  pageData: PageResult<unknown> | null;
  onPageChange: (page: number) => void;
}) {
  if (!pageData) {
    return null;
  }
  const current = pageData.current || 1;
  const pages = pageData.pages || 1;
  return (
    <div className="pagination">
      <span>
        共 {pageData.total} 条，第 {current} / {pages || 1} 页
      </span>
      <div>
        <button disabled={current <= 1} onClick={() => onPageChange(current - 1)}>
          上一页
        </button>
        <button disabled={current >= pages} onClick={() => onPageChange(current + 1)}>
          下一页
        </button>
      </div>
    </div>
  );
}

function JobDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({
    title: '',
    company: '',
    department: '',
    location: '',
    jobType: '',
    description: '',
    requirements: '',
    skills: '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      const payload = compactPayload<JobCreatePayload>({
        title: form.title,
        company: form.company,
        department: form.department,
        location: form.location,
        jobType: form.jobType,
        description: form.description,
        requirements: form.requirements,
        skills: splitList(form.skills),
        status: 1,
      });
      await adminApi.createJob(payload);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog title="新建职位" onClose={onClose}>
      <form className="dialog-form" onSubmit={submit}>
        <input required placeholder="职位名称" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} />
        <input placeholder="公司" value={form.company} onChange={(event) => setForm({ ...form, company: event.target.value })} />
        <input placeholder="部门" value={form.department} onChange={(event) => setForm({ ...form, department: event.target.value })} />
        <input placeholder="地点" value={form.location} onChange={(event) => setForm({ ...form, location: event.target.value })} />
        <input placeholder="岗位类型" value={form.jobType} onChange={(event) => setForm({ ...form, jobType: event.target.value })} />
        <textarea placeholder="职位描述" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
        <textarea placeholder="任职要求" value={form.requirements} onChange={(event) => setForm({ ...form, requirements: event.target.value })} />
        <input placeholder="技能标签，逗号分隔" value={form.skills} onChange={(event) => setForm({ ...form, skills: event.target.value })} />
        {error && <div className="form-error">{error}</div>}
        <div className="dialog-actions">
          <button type="button" className="secondary" onClick={onClose}>
            取消
          </button>
          <button type="submit" disabled={saving}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

function QuestionDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({
    questionText: '',
    answerReference: '',
    questionType: 'TECHNICAL',
    difficulty: 'MEDIUM',
    skillArea: '',
    tags: '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      const payload = compactPayload<QuestionCreatePayload>({
        questionText: form.questionText,
        answerReference: form.answerReference,
        questionType: form.questionType,
        difficulty: form.difficulty,
        skillArea: form.skillArea,
        status: 1,
        tags: splitList(form.tags),
      });
      await adminApi.createQuestion(payload);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog title="新建题目" onClose={onClose}>
      <form className="dialog-form" onSubmit={submit}>
        <textarea
          required
          placeholder="题目内容"
          value={form.questionText}
          onChange={(event) => setForm({ ...form, questionText: event.target.value })}
        />
        <textarea
          placeholder="参考答案"
          value={form.answerReference}
          onChange={(event) => setForm({ ...form, answerReference: event.target.value })}
        />
        <div className="split-row">
          <input
            required
            placeholder="题型，如 TECHNICAL"
            value={form.questionType}
            onChange={(event) => setForm({ ...form, questionType: event.target.value })}
          />
          <input
            required
            placeholder="难度，如 MEDIUM"
            value={form.difficulty}
            onChange={(event) => setForm({ ...form, difficulty: event.target.value })}
          />
        </div>
        <input placeholder="技能域" value={form.skillArea} onChange={(event) => setForm({ ...form, skillArea: event.target.value })} />
        <input placeholder="标签，逗号分隔" value={form.tags} onChange={(event) => setForm({ ...form, tags: event.target.value })} />
        {error && <div className="form-error">{error}</div>}
        <div className="dialog-actions">
          <button type="button" className="secondary" onClick={onClose}>
            取消
          </button>
          <button type="submit" disabled={saving}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

function Dialog({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="dialog">
        <header>
          <h2>{title}</h2>
          <button type="button" onClick={onClose}>
            关闭
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}

function filterPlaceholder(view: ViewKey) {
  if (view === 'users') {
    return '按用户名搜索';
  }
  if (view === 'jobs') {
    return '按职位名称搜索';
  }
  if (view === 'interviews') {
    return '按阶段搜索，如 opening';
  }
  if (view === 'questions') {
    return '按题目关键词搜索';
  }
  return '按模块搜索，如 AUTH';
}
