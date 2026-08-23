import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api';
import type { AuditLogRow, PageResult } from '../types';
import { formatDate, textStatusTone } from '../utils';
import { Badge, Btn, EmptyState, LoadingBlock, Pagination } from '../components/ui';

export function AuditView() {
  const [module, setModule] = useState('');
  const [result, setResult] = useState('');
  const [current, setCurrent] = useState(1);
  const [pageData, setPageData] = useState<PageResult<AuditLogRow> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setPageData(await adminApi.auditLogs({ current, size: 10, module, result }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
      setPageData(null);
    } finally {
      setLoading(false);
    }
  }, [current, module, result]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <>
      <div className="page-head">
        <div className="page-head-copy">
          <h1>审计日志</h1>
          <p>记录后台管理操作的模块、动作、请求路径、结果、耗时和时间。</p>
        </div>
      </div>

      <form
        className="toolbar two-col"
        onSubmit={(event) => {
          event.preventDefault();
          setCurrent(1);
          void load();
        }}
      >
        <div className="field">
          <label>模块</label>
          <input className="input" value={module} placeholder="例如 AUTH、QUESTION、INTERVIEW_PORTAL" onChange={(e) => setModule(e.target.value)} />
        </div>
        <div className="field">
          <label>结果</label>
          <select className="select" value={result} onChange={(e) => setResult(e.target.value)}>
            <option value="">全部结果</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILED">FAILED</option>
          </select>
        </div>
        <div className="field" style={{ alignContent: 'end' }}>
          <Btn type="submit">查询</Btn>
        </div>
      </form>

      {error && <p className="form-error" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}

      <div className="card table-card">
        {loading && !pageData ? (
          <LoadingBlock>正在加载审计日志…</LoadingBlock>
        ) : (pageData?.records || []).length === 0 ? (
          <EmptyState>暂无审计日志。</EmptyState>
        ) : (
          <table className="data-table">
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
              {pageData!.records.map((row) => (
                <tr key={row.id}>
                  <td className="meta">{row.id}</td>
                  <td>{row.module}</td>
                  <td>{row.operation}</td>
                  <td className="mono-cell">{row.requestUri || '-'}</td>
                  <td>
                    <Badge tone={textStatusTone(row.result)}>{row.result || '-'}</Badge>
                  </td>
                  <td className="meta">{row.durationMs == null ? '-' : `${row.durationMs}ms`}</td>
                  <td className="meta">{formatDate(row.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination pageData={pageData} onPageChange={setCurrent} />
      </div>
    </>
  );
}
