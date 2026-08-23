import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { adminApi } from '../api';
import type {
  ModelConfigTestResult,
  ModelRuntimeConfig,
  PageResult,
  QuestionImportBatch,
  QuestionRow,
  SystemConfigItem,
} from '../types';
import { compactPayload, formatDate, questionStatusText, questionStatusTone, splitList } from '../utils';
import { Badge, Btn, Dialog, EmptyState, LoadingBlock, Meter, Pagination, Switch, confirmDialog, toast } from '../components/ui';

type SettingsTab = 'config' | 'questions' | 'model';

/* ─── 面试配置（真实持久化：strategy + system_config） ─── */

const BEHAVIOR_SWITCHES = [
  {
    key: 'interview.followup.enabled',
    title: '启用项目追问',
    description: '根据回答质量补充追问，不占独立项目题数量。（已持久化；消费端排期中）',
  },
  {
    key: 'interview.fork.enabled',
    title: '允许历史分支',
    description: '从可回答的历史消息创建新分支，原分支保持不变。（已持久化；消费端排期中）',
  },
  {
    key: 'interview.auto-report.enabled',
    title: '完成后自动生成报告',
    description: '面试结束后自动从持久化评估结果生成报告。（已持久化；消费端排期中）',
  },
];

function ConfigPanel() {
  const [durationMinutes, setDurationMinutes] = useState('45');
  const [projectTarget, setProjectTarget] = useState('5');
  const [technicalCount, setTechnicalCount] = useState('5');
  const [switches, setSwitches] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [configs] = await Promise.all([adminApi.systemConfigs('INTERVIEW')]);
      const byKey = new Map(configs.map((item) => [item.configKey, item.configValue]));
      const [strategy] = await Promise.all([adminApi.interviewStrategyDefault().catch(() => null)]);
      if (strategy?.durationMinutes) {
        setDurationMinutes(String(strategy.durationMinutes));
      }
      setProjectTarget(byKey.get('interview.project-questions.target') || '5');
      setTechnicalCount(byKey.get('interview.technical-questions.count') || '5');
      setSwitches({
        'interview.followup.enabled': byKey.get('interview.followup.enabled') !== 'false',
        'interview.fork.enabled': byKey.get('interview.fork.enabled') !== 'false',
        'interview.auto-report.enabled': byKey.get('interview.auto-report.enabled') !== 'false',
      });
    } catch {
      toast('面试配置加载失败，使用默认值展示', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function save() {
    setSaving(true);
    try {
      const strategy = await adminApi.interviewStrategyDefault().catch(() => null);
      if (strategy) {
        await adminApi.saveInterviewStrategyDefault({ ...strategy, durationMinutes: Number(durationMinutes) });
      }
      await adminApi.updateSystemConfig('interview.project-questions.target', {
        configValue: projectTarget,
        configGroup: 'INTERVIEW',
        description: '独立项目题目标数量（下一次发起面试生效）',
      });
      await adminApi.updateSystemConfig('interview.technical-questions.count', {
        configValue: technicalCount,
        configGroup: 'INTERVIEW',
        description: '技术题数量（已持久化；消费端排期中）',
      });
      for (const item of BEHAVIOR_SWITCHES) {
        await adminApi.updateSystemConfig(item.key, {
          configValue: String(switches[item.key] ?? true),
          configGroup: 'INTERVIEW',
          description: item.title,
        });
      }
      toast('面试配置已保存；项目题数将在下一次发起面试时生效');
    } catch (err) {
      toast(err instanceof Error ? err.message : '保存失败', 'error');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingBlock>正在加载面试配置…</LoadingBlock>;
  }

  return (
    <div className="card settings-panel active">
      <div className="setting-section">
        <div className="setting-section-head">
          <div>
            <h2>默认面试策略</h2>
            <p>新建面试时自动带入；项目题数由 interview 服务在下一次发起时读取生效。</p>
          </div>
          <Btn variant="primary" busy={saving} disabled={saving} onClick={() => void save()}>
            保存配置
          </Btn>
        </div>
        <div className="form-grid">
          <div className="field">
            <label>预计时长（分钟）</label>
            <select className="select" value={durationMinutes} onChange={(e) => setDurationMinutes(e.target.value)}>
              <option value="45">45 分钟</option>
              <option value="60">60 分钟</option>
              <option value="90">90 分钟</option>
            </select>
          </div>
          <div className="field">
            <label>独立项目题数量</label>
            <select className="select" value={projectTarget} onChange={(e) => setProjectTarget(e.target.value)}>
              {[3, 4, 5, 6].map((count) => (
                <option key={count} value={String(count)}>{count} 题</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>技术题数量</label>
            <select className="select" value={technicalCount} onChange={(e) => setTechnicalCount(e.target.value)}>
              {[3, 4, 5, 6, 8].map((count) => (
                <option key={count} value={String(count)}>{count} 题</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="setting-section">
        <h2>行为规则</h2>
        {BEHAVIOR_SWITCHES.map((item) => (
          <div className="switch-row" key={item.key}>
            <span className="switch-copy">
              <strong>{item.title}</strong>
              <span>{item.description}</span>
            </span>
            <Switch
              label={item.title}
              checked={switches[item.key] ?? true}
              onChange={(next) => setSwitches((current) => ({ ...current, [item.key]: next }))}
            />
          </div>
        ))}
      </div>
    </div>
  );
}

/* ─── 题库管理（真实 API：增删改查/导入/审核/上下架/向量同步/批次） ─── */

function QuestionsPanel() {
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [current, setCurrent] = useState(1);
  const [pageData, setPageData] = useState<PageResult<QuestionRow> | null>(null);
  const [batches, setBatches] = useState<PageResult<QuestionImportBatch> | null>(null);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [questions, imports] = await Promise.all([
        adminApi.questions({ current, size: 10, keyword: query, status }),
        adminApi.questionImports({ current: 1, size: 5 }),
      ]);
      setPageData(questions);
      setBatches(imports);
    } catch {
      toast('题库加载失败', 'error');
    } finally {
      setLoading(false);
    }
  }, [current, query, status]);

  useEffect(() => {
    void load();
  }, [load]);

  async function runQuestionAction(row: QuestionRow, action: 'approve' | 'reject' | 'publish' | 'unpublish' | 'delete') {
    const labels = { approve: '审核通过', reject: '驳回', publish: '上架', unpublish: '下架', delete: '删除' };
    const confirmed = await confirmDialog({
      title: labels[action],
      message: `确认${labels[action]}题目 #${row.id}？${action === 'delete' ? '该操作不可撤销。' : ''}`,
      confirmLabel: labels[action],
      danger: action === 'delete' || action === 'reject',
    });
    if (!confirmed) return;
    try {
      if (action === 'approve') await adminApi.approveQuestion(row.id);
      else if (action === 'reject') await adminApi.rejectQuestion(row.id);
      else if (action === 'publish') await adminApi.publishQuestion(row.id);
      else if (action === 'unpublish') await adminApi.unpublishQuestion(row.id);
      else await adminApi.deleteQuestion(row.id);
      toast(`已${labels[action]}题目 #${row.id}`);
      void load();
    } catch (err) {
      toast(err instanceof Error ? err.message : '操作失败', 'error');
    }
  }

  async function sync() {
    setSyncing(true);
    try {
      await adminApi.syncQuestions();
      toast('题库向量同步任务已触发');
      void load();
    } catch (err) {
      toast(err instanceof Error ? err.message : '同步失败', 'error');
    } finally {
      setSyncing(false);
    }
  }

  async function importFile(file: File | null) {
    if (!file) return;
    try {
      await adminApi.importQuestions(file);
      toast(`已导入 ${file.name}，默认进入待审核状态`);
      if (fileInputRef.current) fileInputRef.current.value = '';
      void load();
    } catch (err) {
      toast(err instanceof Error ? err.message : '导入失败', 'error');
    }
  }

  const records = pageData?.records || [];
  const countByStatus = (value: number) => records.filter((row) => row.status === value).length;

  return (
    <div className="card settings-panel">
      <div className="setting-section">
        <div className="setting-section-head">
          <div>
            <h2>题库管理</h2>
            <p>题目的创建、导入、审核、上下架与向量同步；变更会自动同步到 Python 向量库。</p>
          </div>
          <div className="head-actions question-actions-group">
            <Btn variant="primary" onClick={() => setDialogOpen(true)}>新增题目</Btn>
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv,.pdf,.md,.docx,.txt"
              hidden
              onChange={(event) => void importFile(event.target.files?.[0] || null)}
            />
            <Btn onClick={() => fileInputRef.current?.click()}>导入题库</Btn>
            <Btn busy={syncing} disabled={syncing} onClick={() => void sync()}>向量同步</Btn>
          </div>
        </div>
      </div>

      <div className="setting-section">
        <div className="question-filter-container">
          <div className="question-filter-title">
            <span className="filter-heading">检索与筛选</span>
            <span className="meta">当前页 {records.length} 道 · 待审核 {countByStatus(2)} · 已上架 {countByStatus(1)} · 已下架 {countByStatus(0)}</span>
          </div>
          <form
            className="question-toolbar"
            onSubmit={(event) => {
              event.preventDefault();
              setCurrent(1);
              void load();
            }}
          >
            <div className="field field-search">
              <label>搜索题目</label>
              <input className="input" value={query} placeholder="题目关键词、技能域或标签" onChange={(e) => setQuery(e.target.value)} />
            </div>
            <div className="field">
              <label>状态</label>
              <select className="select" value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="">全部状态</option>
                <option value="2">待审核</option>
                <option value="1">已上架</option>
                <option value="0">已下架</option>
                <option value="3">已驳回</option>
              </select>
            </div>
            <div className="field" style={{ alignContent: 'end' }}>
              <Btn type="submit">查询</Btn>
            </div>
          </form>
        </div>
      </div>

      <div className="setting-section">
        <div className="setting-section-head">
          <div>
            <h3>题目列表</h3>
            <p>支持按题目维度查看媒体、审核状态、向量同步进度与操作。</p>
          </div>
        </div>
        <div className="table-card question-table-card" style={{ border: 0, background: 'transparent', padding: 0 }}>
          {loading && !pageData ? (
            <LoadingBlock>正在加载题库…</LoadingBlock>
          ) : records.length === 0 ? (
            <EmptyState>暂无题目，可以调整筛选条件或新增题目。</EmptyState>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>题目</th>
                  <th>类型 / 难度</th>
                  <th>技能域</th>
                  <th>媒体</th>
                  <th>状态</th>
                  <th>向量状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <span className="record-title">
                        <strong>{row.questionText}</strong>
                        <span className="tag-list">{(row.tags || []).map((tag) => `#${tag}`).join(' ')}</span>
                      </span>
                    </td>
                    <td className="meta">{row.questionType || '-'} · {row.difficulty || '-'}</td>
                    <td className="meta">{row.skillArea || '-'}</td>
                    <td className="meta">{row.media?.length ? `${row.media.length} 张` : '—'}</td>
                    <td>
                      <Badge tone={questionStatusTone(row.status)}>{questionStatusText(row.status)}</Badge>
                    </td>
                    <td>
                      <span className="meta">{row.vectorSyncStatus || '—'}</span>
                      {row.vectorSyncError && <small className="error-text" style={{ display: 'block' }}>{row.vectorSyncError}</small>}
                    </td>
                    <td className="table-actions">
                      {row.status === 2 && (
                        <>
                          <button type="button" className="row-action" onClick={() => void runQuestionAction(row, 'approve')}>通过</button>
                          <button type="button" className="row-action" onClick={() => void runQuestionAction(row, 'reject')}>驳回</button>
                        </>
                      )}
                      {(row.status === 0 || row.status === 3) && (
                        <button type="button" className="row-action" onClick={() => void runQuestionAction(row, 'publish')}>上架</button>
                      )}
                      {row.status === 1 && (
                        <button type="button" className="row-action" onClick={() => void runQuestionAction(row, 'unpublish')}>下架</button>
                      )}
                      <button type="button" className="row-action" onClick={() => void runQuestionAction(row, 'delete')}>删除</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <Pagination pageData={pageData} onPageChange={setCurrent} />
        </div>
      </div>

      <div className="setting-section">
        <div className="setting-section-head">
          <div>
            <h3>最近导入批次</h3>
            <p>保留批次号、文件、成功/失败数量和错误信息。</p>
          </div>
        </div>
        <div className="table-card" style={{ border: 0, background: 'transparent', padding: 0 }}>
          {(batches?.records || []).length === 0 ? (
            <EmptyState>暂无导入批次。</EmptyState>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>批次号</th>
                  <th>文件</th>
                  <th>状态</th>
                  <th>成功 / 失败</th>
                  <th>错误</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                {batches!.records.map((row) => (
                  <tr key={row.id}>
                    <td className="mono-cell">{row.batchNo.slice(0, 16)}…</td>
                    <td>{row.fileName}</td>
                    <td>
                      <Badge tone={row.status === 'SUCCESS' ? 'success' : row.status === 'PROCESSING' ? undefined : 'warn'}>
                        {row.status}
                      </Badge>
                    </td>
                    <td className="meta">{row.successCount} / {row.failedCount} · 共 {row.totalCount} 道</td>
                    <td className="error-text" style={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}>{row.errorMessage || '—'}</td>
                    <td className="meta">{formatDate(row.finishedAt || row.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {dialogOpen && (
        <QuestionDialog
          onClose={() => setDialogOpen(false)}
          onSaved={() => {
            setDialogOpen(false);
            void load();
          }}
        />
      )}
    </div>
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
      const payload = compactPayload({
        questionText: form.questionText,
        answerReference: form.answerReference,
        questionType: form.questionType,
        difficulty: form.difficulty,
        skillArea: form.skillArea,
        status: 1,
        tags: splitList(form.tags),
      });
      await adminApi.createQuestion(payload);
      toast('题目已创建并进入向量同步');
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog title="新增题目" description="题目应对应明确能力和可观察证据。" onClose={onClose}>
      <form className="dialog-form" onSubmit={submit}>
        <div className="field">
          <label>题目内容</label>
          <textarea className="textarea" required placeholder="输入一个具体、可追问的问题" value={form.questionText} onChange={(e) => setForm({ ...form, questionText: e.target.value })} />
        </div>
        <div className="field">
          <label>参考答案</label>
          <textarea className="textarea" placeholder="用于评分与追问的参考（可选）" value={form.answerReference} onChange={(e) => setForm({ ...form, answerReference: e.target.value })} />
        </div>
        <div className="form-grid">
          <div className="field">
            <label>类型</label>
            <select className="select" value={form.questionType} onChange={(e) => setForm({ ...form, questionType: e.target.value })}>
              <option value="TECHNICAL">技术题</option>
              <option value="PROJECT">项目题</option>
              <option value="SCENARIO">场景题</option>
            </select>
          </div>
          <div className="field">
            <label>难度</label>
            <select className="select" value={form.difficulty} onChange={(e) => setForm({ ...form, difficulty: e.target.value })}>
              <option value="EASY">基础</option>
              <option value="MEDIUM">中等</option>
              <option value="HARD">较难</option>
            </select>
          </div>
        </div>
        <div className="form-grid">
          <div className="field">
            <label>技能域</label>
            <input className="input" placeholder="如 后端 / FDE" value={form.skillArea} onChange={(e) => setForm({ ...form, skillArea: e.target.value })} />
          </div>
          <div className="field">
            <label>标签（逗号分隔）</label>
            <input className="input" placeholder="可靠性, 可观测性" value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} />
          </div>
        </div>
        {error && <p className="form-error">{error}</p>}
        <div className="dialog-actions">
          <Btn onClick={onClose}>取消</Btn>
          <button type="submit" className="btn btn-primary" aria-busy={saving ? 'true' : undefined} disabled={saving}>
            {saving ? '保存中…' : '保存题目'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

/* ─── 模型与检索（透传 Python runtime-config，修改即时生效） ─── */

function ModelPanel() {
  const [config, setConfig] = useState<ModelRuntimeConfig | null>(null);
  const [chatModel, setChatModel] = useState('');
  const [fallbackModels, setFallbackModels] = useState('');
  const [embeddingModel, setEmbeddingModel] = useState('');
  const [embeddingDimension, setEmbeddingDimension] = useState('');
  const [vectorCollection, setVectorCollection] = useState('');
  const [retrievalTopK, setRetrievalTopK] = useState('');
  const [keywordFallback, setKeywordFallback] = useState(true);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<ModelConfigTestResult | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await adminApi.getModelConfig();
      setConfig(data);
      setChatModel(data.chat_model);
      setFallbackModels(data.chat_fallback_models.join(', '));
      setEmbeddingModel(data.embedding_model);
      setEmbeddingDimension(String(data.embedding_dimension));
      setVectorCollection(data.vector_collection);
      setRetrievalTopK(String(data.retrieval_top_k));
      setKeywordFallback(data.retrieval_keyword_fallback);
    } catch {
      toast('模型配置加载失败（Python 服务不可达？）', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function save() {
    setSaving(true);
    try {
      const result = await adminApi.updateModelConfig(
        {
          chat_model: chatModel.trim(),
          chat_fallback_models: splitList(fallbackModels),
          embedding_model: embeddingModel.trim(),
          embedding_dimension: Number(embeddingDimension),
          vector_collection: vectorCollection.trim(),
          retrieval_top_k: Number(retrievalTopK),
          retrieval_keyword_fallback: keywordFallback,
        },
        vectorCollection.trim() !== config?.vector_collection,
      );
      setConfig(result.config);
      toast('模型与检索配置已生效（下一次调用即使用新配置）');
    } catch (err) {
      toast(err instanceof Error ? err.message : '保存失败', 'error');
    } finally {
      setSaving(false);
    }
  }

  async function test() {
    setTesting(true);
    setTestResult(null);
    try {
      setTestResult(await adminApi.testModelConfig());
    } catch (err) {
      toast(err instanceof Error ? err.message : '测试失败', 'error');
    } finally {
      setTesting(false);
    }
  }

  if (loading) {
    return <LoadingBlock>正在加载模型配置…</LoadingBlock>;
  }

  return (
    <div className="card settings-panel">
      <div className="setting-section">
        <div className="setting-section-head">
          <div>
            <h2>模型与检索</h2>
            <p>配置直接写入 Python 服务运行时，保存后对下一次 LLM / 检索调用生效；进程重启后回到环境变量基线。</p>
          </div>
          <div className="head-actions">
            <Btn onClick={() => void test()} busy={testing} disabled={testing}>测试连接</Btn>
            <Btn variant="primary" onClick={() => void save()} busy={saving} disabled={saving}>保存配置</Btn>
          </div>
        </div>
        <div className="form-grid">
          <div className="field">
            <label>聊天模型</label>
            <input className="input" value={chatModel} onChange={(e) => setChatModel(e.target.value)} />
          </div>
          <div className="field">
            <label>回退模型链（逗号分隔）</label>
            <input className="input" value={fallbackModels} onChange={(e) => setFallbackModels(e.target.value)} />
          </div>
          <div className="field">
            <label>Embedding 模型</label>
            <input className="input" value={embeddingModel} onChange={(e) => setEmbeddingModel(e.target.value)} />
          </div>
          <div className="field">
            <label>Embedding 维度</label>
            <input className="input" value={embeddingDimension} onChange={(e) => setEmbeddingDimension(e.target.value)} />
          </div>
          <div className="field">
            <label>向量集合</label>
            <input className="input" value={vectorCollection} onChange={(e) => setVectorCollection(e.target.value)} />
          </div>
          <div className="field">
            <label>召回数量（top-k）</label>
            <select className="select" value={retrievalTopK} onChange={(e) => setRetrievalTopK(e.target.value)}>
              {[3, 5, 8, 10].map((value) => (
                <option key={value} value={String(value)}>{value} 条</option>
              ))}
            </select>
          </div>
        </div>
        {config && config.overridden_keys.length > 0 && (
          <p className="meta" style={{ marginTop: 'var(--space-3)' }}>
            当前运行时覆盖项：{config.overridden_keys.join(', ')}
          </p>
        )}
        <p className="muted" style={{ fontSize: 'var(--text-xs)', marginTop: 'var(--space-2)' }}>
          注意：不同 embedding 的向量集合语义上不可互换，切换集合前请确认目标集合与当前 embedding 匹配；保存时会要求确认。
        </p>
      </div>

      {testResult && (
        <div className="setting-section">
          <h3>连接测试结果</h3>
          <div className="evidence-list">
            <div className="evidence-item">
              <strong>Chat · {testResult.chat.model}</strong>
              <p>
                {testResult.chat.ok ? `连通正常，延迟 ${testResult.chat.latency_ms}ms` : `失败：${testResult.chat.error || '未知错误'}`}
              </p>
            </div>
            <div className="evidence-item">
              <strong>Embedding · {testResult.embedding.model}</strong>
              <p>
                {testResult.embedding.ok
                  ? `连通正常，维度 ${testResult.embedding.dimension ?? '-'}，延迟 ${testResult.embedding.latency_ms}ms`
                  : `失败：${testResult.embedding.error || '未知错误'}`}
              </p>
            </div>
          </div>
        </div>
      )}

      <div className="setting-section">
        <h2>检索行为</h2>
        <div className="switch-row">
          <span className="switch-copy">
            <strong>检索不可用时降级到关键词</strong>
            <span>保持面试流程可用，不自动切换未知向量模型；关闭后向量异常将直接报错。</span>
          </span>
          <Switch label="检索降级" checked={keywordFallback} onChange={setKeywordFallback} />
        </div>
      </div>
    </div>
  );
}

/* ─── SettingsView 壳 ─── */

const TABS: Array<{ key: SettingsTab; label: string }> = [
  { key: 'config', label: '面试配置' },
  { key: 'questions', label: '题库管理' },
  { key: 'model', label: '模型与检索' },
];

export function SettingsView() {
  const [tab, setTab] = useState<SettingsTab>('config');

  return (
    <>
      <div className="page-head">
        <div className="page-head-copy">
          <h1>设置与题库</h1>
          <p>面试策略、问题资产和模型配置放进同一套管理结构；面试配置与模型检索均真实持久化或直连生效。</p>
        </div>
      </div>
      <div className="settings-layout">
        <nav className="card settings-nav" aria-label="设置分类">
          {TABS.map((item) => (
            <button
              key={item.key}
              type="button"
              className={tab === item.key ? 'settings-tab active' : 'settings-tab'}
              onClick={() => setTab(item.key)}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <div>
          {tab === 'config' && <ConfigPanel />}
          {tab === 'questions' && <QuestionsPanel />}
          {tab === 'model' && <ModelPanel />}
        </div>
      </div>
    </>
  );
}
