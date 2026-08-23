import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { adminApi } from '../api';
import type {
  AiObservabilityStats,
  AiRawPayloadType,
  AiTraceDetail,
  AiTraceRow,
  PageResult,
} from '../types';
import { compactId, formatDate, formatDuration, formatNumber, formatPercent, providerModel, statNumber, statRate, textStatusTone } from '../utils';
import { Badge, Btn, EmptyState, LoadingBlock, Pagination } from '../components/ui';

const STATUS_OPTIONS = [
  { value: 'SUCCESS', label: 'Success' },
  { value: 'ERROR', label: 'Error' },
  { value: 'RUNNING', label: 'Running' },
];

const CALL_TYPE_OPTIONS = [
  { value: 'generate_opening', label: 'Generate opening' },
  { value: 'ask_self_introduction', label: 'Ask self introduction' },
  { value: 'generate_project_questions', label: 'Generate project questions' },
  { value: 'evaluate_answer', label: 'Evaluate answer' },
  { value: 'generate_followup_question', label: 'Generate follow-up question' },
  { value: 'conclude_interview', label: 'Conclude interview' },
  { value: 'ask', label: 'Ask' },
];

export function ObservabilityView() {
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [provider, setProvider] = useState('');
  const [model, setModel] = useState('');
  const [callType, setCallType] = useState('');
  const [current, setCurrent] = useState(1);
  const [pageData, setPageData] = useState<PageResult<AiTraceRow> | null>(null);
  const [stats, setStats] = useState<AiObservabilityStats | null>(null);
  const [selectedTraceId, setSelectedTraceId] = useState<string | null>(null);
  const [traceDetail, setTraceDetail] = useState<AiTraceDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [rawPayloads, setRawPayloads] = useState<Record<string, Partial<Record<AiRawPayloadType, string>>>>({});
  const [rawLoadingKey, setRawLoadingKey] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const loadList = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params = { current, size: 10, requestId: query, status, provider, model, callType };
      const [traces, statsData] = await Promise.all([
        adminApi.aiTraces(params),
        adminApi.aiObservabilityStats(params),
      ]);
      setPageData(traces);
      setStats(statsData);
      const nextTraceId = traces.records.find((row) => row.id === selectedTraceId)?.id || traces.records[0]?.id || null;
      if (nextTraceId) {
        await loadDetail(nextTraceId);
      } else {
        setSelectedTraceId(null);
        setTraceDetail(null);
        setRawPayloads({});
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current, query, status, provider, model, callType]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  async function loadDetail(traceId: string) {
    setSelectedTraceId(traceId);
    setDetailLoading(true);
    setRawPayloads({});
    try {
      setTraceDetail(await adminApi.aiTraceDetail(traceId));
    } catch (err) {
      setError(err instanceof Error ? err.message : '链路详情加载失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function revealRaw(callId: string, type: AiRawPayloadType) {
    const key = `${callId}:${type}`;
    setRawLoadingKey(key);
    try {
      const payload = await adminApi.aiLlmCallRaw(callId, type);
      setRawPayloads((currentData) => ({
        ...currentData,
        [callId]: {
          ...(currentData[callId] || {}),
          [type]: payload.rawText || payload.promptText || payload.responseText || '',
        },
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '原文读取失败');
    } finally {
      setRawLoadingKey('');
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    setCurrent(1);
    void loadList();
  }

  const failedCalls = statNumber(stats, 'failedCalls', 'failedLlmCalls');
  const totalCalls = statNumber(stats, 'totalLlmCalls');
  const failureRate = statRate(stats, 'llmFailureRate') ?? (totalCalls > 0 ? failedCalls / totalCalls : 0);
  const averageLatency = statRate(stats, 'avgDurationMs', 'averageLatencyMs');

  const metricCards = [
    { label: 'Traces', value: formatNumber(statNumber(stats, 'totalTraces', 'traceCount')), note: '调用链总数' },
    { label: 'LLM Calls', value: formatNumber(totalCalls), note: '调用总数' },
    { label: 'Total Tokens', value: formatNumber(statNumber(stats, 'totalTokens')), note: '累计 Token' },
    { label: 'Failure Rate', value: formatPercent(failureRate), note: '失败比例' },
    { label: 'Avg Duration', value: formatDuration(averageLatency), note: '平均耗时' },
    { label: 'Cache Token Hit', value: formatPercent(statRate(stats, 'providerPromptCacheTokenHitRate')), note: '缓存 Token 命中率' },
    { label: 'Cache Call Hit', value: formatPercent(statRate(stats, 'providerPromptCacheCallHitRate')), note: '缓存调用命中率' },
    { label: 'Cache Unreported', value: formatNumber(statNumber(stats, 'providerCacheUnreportedCalls')), note: '未上报调用' },
  ];

  return (
    <>
      <div className="page-head">
        <div className="page-head-copy">
          <h1>AI 观测</h1>
          <p>覆盖 Trace、LLM 调用、Token、失败率、延迟和 Provider Cache，可按权限查看原始请求与响应。</p>
        </div>
      </div>

      <form className="toolbar trace-filter" onSubmit={submit}>
        <div className="field">
          <label>Request ID</label>
          <input className="input" value={query} placeholder="按 Request ID 搜索" onChange={(e) => setQuery(e.target.value)} />
        </div>
        <div className="field">
          <label>状态</label>
          <select className="select" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">全部状态</option>
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>Provider</label>
          <input className="input" value={provider} placeholder="Provider" onChange={(e) => setProvider(e.target.value)} />
        </div>
        <div className="field">
          <label>Model</label>
          <input className="input" value={model} placeholder="Model" onChange={(e) => setModel(e.target.value)} />
        </div>
        <div className="field">
          <label>调用类型</label>
          <select className="select" value={callType} onChange={(e) => setCallType(e.target.value)}>
            <option value="">全部类型</option>
            {CALL_TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
        <div className="field" style={{ alignContent: 'end' }}>
          <Btn type="submit">查询</Btn>
        </div>
      </form>

      {error && <p className="form-error" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}

      <div className="trace-metrics">
        {metricCards.map((card) => (
          <article className="card metric-card compact" key={card.label}>
            <span>{card.label}</span>
            <strong>{card.value}</strong>
            <small>{card.note}</small>
          </article>
        ))}
      </div>

      <div className="trace-grid">
        <div className="card trace-list">
          {loading && !pageData ? (
            <LoadingBlock>正在加载调用链…</LoadingBlock>
          ) : (pageData?.records || []).length === 0 ? (
            <EmptyState>暂无 AI 调用链路，可以调整筛选条件。</EmptyState>
          ) : (
            pageData!.records.map((row) => (
              <button
                type="button"
                key={row.id}
                className={selectedTraceId === row.id ? 'trace-row active' : 'trace-row'}
                onClick={() => void loadDetail(row.id)}
              >
                <span className="trace-row-head">
                  <strong>{compactId(row.id)}</strong>
                  <Badge tone={textStatusTone(row.status)}>{row.status || '-'}</Badge>
                </span>
                <span className="meta">
                  {providerModel(row)} · {formatNumber(row.totalTokens)} tokens · {formatDuration(row.durationMs)}
                </span>
              </button>
            ))
          )}
          <Pagination pageData={pageData} onPageChange={setCurrent} />
        </div>

        <section className="card trace-detail" aria-busy={detailLoading ? 'true' : undefined}>
          {detailLoading && !traceDetail ? (
            <LoadingBlock>正在加载链路详情…</LoadingBlock>
          ) : !traceDetail ? (
            <EmptyState>请选择一条 AI 调用链路。</EmptyState>
          ) : (
            <>
              <div className="branch-summary-head">
                <div>
                  <span className="meta">Trace 详情</span>
                  <h2 style={{ marginTop: 4 }}>{compactId(traceDetail.id)}</h2>
                </div>
                <Badge tone={textStatusTone(traceDetail.status)}>{traceDetail.status}</Badge>
              </div>
              <div className="call-metrics">
                <span>业务 <strong>{traceDetail.businessType}</strong></span>
                <span>耗时 <strong>{formatDuration(traceDetail.durationMs)}</strong></span>
                <span>Session <strong>{compactId(traceDetail.sessionId || traceDetail.pythonSessionId)}</strong></span>
              </div>
              {traceDetail.errorMessage && <p className="form-error">{traceDetail.errorMessage}</p>}

              <div className="trace-step">
                <strong>步骤时间线</strong>
                {traceDetail.steps.length === 0 ? (
                  <p className="muted" style={{ fontSize: 'var(--text-xs)', marginTop: 4 }}>暂无步骤记录。</p>
                ) : (
                  <ol className="timeline">
                    {traceDetail.steps.map((step) => (
                      <li key={step.id}>
                        <Badge tone={textStatusTone(step.status)}>{step.status || '-'}</Badge>
                        <div>
                          <strong>{step.stepName || step.stepType || `Step ${step.stepOrder || ''}`}</strong>
                          <small>{step.stepType || '-'} · {formatDuration(step.durationMs)} · {formatDate(step.startedAt)}</small>
                          {step.errorMessage && <em>{step.errorMessage}</em>}
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
              </div>

              <div className="trace-step">
                <strong>关联 LLM 调用</strong>
                {traceDetail.llmCalls.length === 0 ? (
                  <p className="muted" style={{ fontSize: 'var(--text-xs)', marginTop: 4 }}>暂无 LLM 调用。</p>
                ) : (
                  <div className="llm-call-list">
                    {traceDetail.llmCalls.map((call) => {
                      const promptRaw = rawPayloads[call.id]?.PROMPT;
                      const responseRaw = rawPayloads[call.id]?.RESPONSE;
                      return (
                        <article className="llm-call-card" key={call.id}>
                          <header>
                            <div>
                              <strong>{providerModel(call)}</strong>
                              <small>{call.callType || '-'} · {compactId(call.id)}</small>
                            </div>
                            <Badge tone={textStatusTone(call.status)}>{call.status || '-'}</Badge>
                          </header>
                          <div className="call-metrics">
                            <span>Tokens <strong>{formatNumber(call.totalTokens)}</strong></span>
                            <span>Prompt <strong>{formatNumber(call.promptTokens)}</strong></span>
                            <span>Completion <strong>{formatNumber(call.completionTokens)}</strong></span>
                            <span>Latency <strong>{formatDuration(call.latencyMs)}</strong></span>
                            <span>Cache Hit <strong>{formatPercent(call.promptCacheHitRate)}</strong></span>
                          </div>
                          {call.fallbackUsed && (
                            <p className="fallback-note">Fallback from {call.fallbackFromModel || '-'}</p>
                          )}
                          {call.errorMessage && <p className="form-error">{call.errorMessage}</p>}
                          <div className="raw-actions">
                            <Btn
                              busy={rawLoadingKey === `${call.id}:PROMPT`}
                              disabled={rawLoadingKey === `${call.id}:PROMPT`}
                              onClick={() => void revealRaw(call.id, 'PROMPT')}
                            >
                              查看 Prompt 原文
                            </Btn>
                            <Btn
                              busy={rawLoadingKey === `${call.id}:RESPONSE`}
                              disabled={rawLoadingKey === `${call.id}:RESPONSE`}
                              onClick={() => void revealRaw(call.id, 'RESPONSE')}
                            >
                              查看响应原文
                            </Btn>
                          </div>
                          {promptRaw != null && <pre className="code-block">{promptRaw || 'EMPTY PROMPT'}</pre>}
                          {responseRaw != null && <pre className="code-block">{responseRaw || 'EMPTY RESPONSE'}</pre>}
                        </article>
                      );
                    })}
                  </div>
                )}
              </div>
            </>
          )}
        </section>
      </div>
    </>
  );
}
