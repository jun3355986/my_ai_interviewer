import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { adminApi } from '../api';
import type { InterviewRow, JobCreatePayload, JobRow, PageResult, UserRow } from '../types';
import { compactPayload, formatDate, splitList, userStatusText, userStatusTone } from '../utils';
import { Badge, Btn, Dialog, EmptyState, LoadingBlock, Pagination, confirmDialog, promptDialog, toast } from '../components/ui';

type ModuleKey = 'users' | 'jobs' | 'monitor';

const MODULES: Array<{ key: ModuleKey; label: string }> = [
  { key: 'users', label: '用户管理' },
  { key: 'jobs', label: '职位管理' },
  { key: 'monitor', label: '面试监控' },
];

export function OperationsView() {
  const [module, setModule] = useState<ModuleKey>('users');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [current, setCurrent] = useState(1);
  const [pageData, setPageData] = useState<PageResult<unknown> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [jobDialogOpen, setJobDialogOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const base = { current, size: 10 };
      const data =
        module === 'users'
          ? await adminApi.users({ ...base, username: query, status })
          : module === 'jobs'
            ? await adminApi.jobs({ ...base, title: query, status })
            : await adminApi.interviews({ ...base, stage: query, status });
      setPageData(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
      setPageData(null);
    } finally {
      setLoading(false);
    }
  }, [module, current, query, status]);

  useEffect(() => {
    void load();
  }, [load]);

  function switchModule(next: ModuleKey) {
    setModule(next);
    setQuery('');
    setStatus('');
    setCurrent(1);
    setPageData(null);
  }

  async function disableUser(user: UserRow) {
    const confirmed = await confirmDialog({
      title: '停用用户',
      message: `确认停用用户 ${user.username}？停用后该用户将无法登录。`,
      confirmLabel: '停用',
      danger: true,
    });
    if (!confirmed) return;
    try {
      await adminApi.disableUser(user.id);
      toast(`已停用用户 ${user.username}`);
      void load();
    } catch (err) {
      toast(err instanceof Error ? err.message : '停用失败', 'error');
    }
  }

  async function resetPassword(user: UserRow) {
    const next = await promptDialog({
      title: '重置密码',
      message: `为用户 ${user.username} 设置新密码（至少 6 位）。`,
      label: '新密码',
      placeholder: '输入新密码',
      minLength: 6,
    });
    if (!next) return;
    try {
      await adminApi.resetPassword(user.id, next);
      toast(`已重置 ${user.username} 的密码`);
    } catch (err) {
      toast(err instanceof Error ? err.message : '重置失败', 'error');
    }
  }

  return (
    <>
      <div className="page-head">
        <div className="page-head-copy">
          <h1>用户与职位</h1>
          <p>用户管理、职位管理和面试监控，统一筛选与分页规范。</p>
        </div>
        {module === 'jobs' && (
          <div className="head-actions">
            <Btn variant="primary" onClick={() => setJobDialogOpen(true)}>
              新建职位
            </Btn>
          </div>
        )}
      </div>

      <div className="module-tabs" role="tablist" aria-label="运营管理模块">
        {MODULES.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            aria-selected={module === item.key}
            className={module === item.key ? 'module-tab active' : 'module-tab'}
            onClick={() => switchModule(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {error && <div className="form-error" style={{ marginBottom: 'var(--space-3)' }}>{error}</div>}

      <form
        className={module === 'monitor' ? 'toolbar two-col' : 'toolbar'}
        onSubmit={(event) => {
          event.preventDefault();
          setCurrent(1);
          void load();
        }}
      >
        <div className="field">
          <label>{module === 'users' ? '搜索用户' : module === 'jobs' ? '搜索职位' : '按阶段搜索'}</label>
          <input
            className="input"
            value={query}
            placeholder={module === 'users' ? '用户名' : module === 'jobs' ? '职位名称' : '如 opening / project_qna'}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        {module !== 'monitor' && (
          <div className="field">
            <label>状态</label>
            <select className="select" value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="">全部状态</option>
              <option value="1">启用 / 开放</option>
              <option value="0">停用 / 关闭</option>
              <option value="2">已完成</option>
            </select>
          </div>
        )}
        <div className="field" style={{ alignContent: 'end' }}>
          <Btn type="submit">查询</Btn>
        </div>
      </form>

      <div className="card table-card">
        {loading && !pageData ? (
          <LoadingBlock>正在加载…</LoadingBlock>
        ) : (pageData?.records || []).length === 0 ? (
          <EmptyState>暂无数据，可以调整筛选条件或创建新记录。</EmptyState>
        ) : module === 'users' ? (
          <UsersTable rows={pageData as PageResult<UserRow>} onDisable={disableUser} onReset={resetPassword} />
        ) : module === 'jobs' ? (
          <JobsTable rows={pageData as PageResult<JobRow>} />
        ) : (
          <MonitorTable rows={pageData as PageResult<InterviewRow>} />
        )}
        <Pagination pageData={pageData} onPageChange={setCurrent} />
      </div>

      {jobDialogOpen && (
        <JobDialog
          onClose={() => setJobDialogOpen(false)}
          onSaved={() => {
            setJobDialogOpen(false);
            void load();
          }}
        />
      )}
    </>
  );
}

function UsersTable({
  rows,
  onDisable,
  onReset,
}: {
  rows: PageResult<UserRow>;
  onDisable: (user: UserRow) => void;
  onReset: (user: UserRow) => void;
}) {
  return (
    <table className="data-table">
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
        {rows.records.map((row) => (
          <tr key={row.id}>
            <td className="meta">{row.id}</td>
            <td>
              <span className="record-title">
                <strong>{row.username}</strong>
                <span>{row.nickname || '-'}</span>
              </span>
            </td>
            <td>{row.email || '-'}</td>
            <td>
              <Badge tone={userStatusTone(row.status)}>{userStatusText(row.status)}</Badge>
            </td>
            <td className="meta">{formatDate(row.lastLoginTime)}</td>
            <td className="table-actions">
              <button type="button" className="row-action" onClick={() => onReset(row)}>
                重置密码
              </button>
              {row.status === 1 && (
                <button type="button" className="row-action" onClick={() => onDisable(row)}>
                  停用
                </button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function JobsTable({ rows }: { rows: PageResult<JobRow> }) {
  return (
    <table className="data-table">
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
        {rows.records.map((row) => (
          <tr key={row.id}>
            <td className="meta">{row.id}</td>
            <td>
              <span className="record-title">
                <strong>{row.title}</strong>
                <span>{row.jobType || '-'}</span>
              </span>
            </td>
            <td>{row.company || '-'}</td>
            <td>{row.location || '-'}</td>
            <td>{(row.skills || []).join(' / ') || '-'}</td>
            <td>
              <Badge tone={userStatusTone(row.status)}>{userStatusText(row.status)}</Badge>
            </td>
            <td className="meta">{formatDate(row.updatedAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function MonitorTable({ rows }: { rows: PageResult<InterviewRow> }) {
  return (
    <table className="data-table">
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
        {rows.records.map((row) => (
          <tr key={row.id}>
            <td className="mono-cell">{row.id.slice(0, 12)}…</td>
            <td>{row.username || row.userId || '-'}</td>
            <td>{row.jobTitle || '-'}</td>
            <td className="meta">{row.stage || '-'}</td>
            <td>
              <Badge tone={userStatusTone(row.status)}>{userStatusText(row.status)}</Badge>
            </td>
            <td className="meta">{formatDate(row.startedAt)}</td>
            <td className="meta">{formatDate(row.finishedAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
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
      toast('职位已创建');
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog title="新建职位" description="职位会进入面试创建和简历分析的岗位选择列表。" onClose={onClose}>
      <form className="dialog-form" onSubmit={submit}>
        <div className="field">
          <label>职位名称</label>
          <input className="input" required placeholder="例如：FDE 工程师" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        </div>
        <div className="form-grid">
          <div className="field">
            <label>公司</label>
            <input className="input" placeholder="公司名称" value={form.company} onChange={(e) => setForm({ ...form, company: e.target.value })} />
          </div>
          <div className="field">
            <label>地点</label>
            <input className="input" placeholder="深圳 / 远程" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} />
          </div>
        </div>
        <div className="field">
          <label>技能要求（逗号分隔）</label>
          <input className="input" placeholder="RAG、Java、客户交付" value={form.skills} onChange={(e) => setForm({ ...form, skills: e.target.value })} />
        </div>
        <div className="field">
          <label>职位要求</label>
          <textarea className="textarea" placeholder="岗位要求描述，会作为面试生成的 job requirements" value={form.requirements} onChange={(e) => setForm({ ...form, requirements: e.target.value })} />
        </div>
        {error && <p className="form-error">{error}</p>}
        <div className="dialog-actions">
          <Btn onClick={onClose}>取消</Btn>
          <button type="submit" className="btn btn-primary" aria-busy={saving ? 'true' : undefined} disabled={saving}>
            {saving ? '保存中…' : '保存职位'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
