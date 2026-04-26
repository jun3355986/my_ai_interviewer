import { type FormEvent, type ReactNode, useEffect, useState } from 'react';
import { adminApi, clearSession, getToken, readProfile, saveSession } from './api';
import type {
  AdminProfile,
  AuditLogRow,
  DashboardOverview,
  InterviewRow,
  JobCreatePayload,
  JobRow,
  PageResult,
  QuestionCreatePayload,
  QuestionRow,
  UserRow,
} from './types';

type ViewKey = 'dashboard' | 'users' | 'jobs' | 'interviews' | 'questions' | 'audit';

const views: Array<{ key: ViewKey; label: string; eyebrow: string }> = [
  { key: 'dashboard', label: '运营总览', eyebrow: 'Overview' },
  { key: 'users', label: '用户管理', eyebrow: 'Users' },
  { key: 'jobs', label: '职位管理', eyebrow: 'Jobs' },
  { key: 'interviews', label: '面试监控', eyebrow: 'Interviews' },
  { key: 'questions', label: '题库管理', eyebrow: 'Question Bank' },
  { key: 'audit', label: '审计日志', eyebrow: 'Audit' },
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
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [jobDialogOpen, setJobDialogOpen] = useState(false);
  const [questionDialogOpen, setQuestionDialogOpen] = useState(false);

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
    setCurrentPage(1);
    setPageData(null);
    setError('');
  }

  async function loadCurrentView() {
    setLoading(true);
    setError('');
    try {
      if (activeView === 'dashboard') {
        setOverview(await adminApi.dashboard());
        setPageData(null);
      } else {
        setPageData(await loadPage(activeView));
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

  function loadPage(view: Exclude<ViewKey, 'dashboard'>) {
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
                    <option value="1">启用/进行中</option>
                    <option value="0">停用/关闭</option>
                    <option value="2">已完成</option>
                  </select>
                )}
                <button type="submit">查询</button>
              </form>
              <div className="action-row">
                {activeView === 'jobs' && <button onClick={() => setJobDialogOpen(true)}>新建职位</button>}
                {activeView === 'questions' && (
                  <>
                    <button onClick={() => setQuestionDialogOpen(true)}>新建题目</button>
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
            />
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

function DataTable({
  view,
  pageData,
  loading,
  onDisableUser,
  onResetPassword,
}: {
  view: Exclude<ViewKey, 'dashboard'>;
  pageData: PageResult<unknown> | null;
  loading: boolean;
  onDisableUser: (user: UserRow) => void;
  onResetPassword: (user: UserRow) => void;
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
            <th>向量状态</th>
            <th>更新时间</th>
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
                <span className="status info">{row.vectorSyncStatus || '-'}</span>
              </td>
              <td>{formatDate(row.updatedAt)}</td>
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
