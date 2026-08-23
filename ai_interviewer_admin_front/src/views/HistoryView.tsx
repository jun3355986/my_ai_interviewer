import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api';
import type { LineageSummary, PageResult } from '../types';
import { formatRelative, stageDisplay } from '../utils';
import { Badge, Btn, EmptyState, LoadingBlock, Meter, Pagination } from '../components/ui';
import type { ScreenKey } from '../components/AppShell';

export function HistoryView({
  onResume,
  onReplay,
  onOpenReport,
  onNavigate,
}: {
  onResume: (lineageId: string, branchId: string) => void;
  onReplay: (lineageId: string, branchId: string) => void;
  onOpenReport: (sessionId: string) => void;
  onNavigate: (screen: ScreenKey) => void;
}) {
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('all');
  const [sortBy, setSortBy] = useState('time');
  const [current, setCurrent] = useState(1);
  const [pageData, setPageData] = useState<PageResult<LineageSummary> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setPageData(await adminApi.portalLineages({ current, size: 10, keyword, status, sortBy }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
      setPageData(null);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, sortBy]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <>
      <div className="page-head">
        <div className="page-head-copy">
          <h1>面试记录</h1>
          <p>一个面试谱系只占一张卡片，集中呈现进度、最佳评分、分支数量、回放与继续入口。</p>
        </div>
        <div className="head-actions">
          <Btn variant="primary" onClick={() => onNavigate('upload')}>创建面试</Btn>
        </div>
      </div>

      <form
        className="toolbar"
        onSubmit={(event) => {
          event.preventDefault();
          setCurrent(1);
          void load();
        }}
      >
        <div className="field">
          <label>搜索</label>
          <input className="input" value={keyword} placeholder="候选人姓名" onChange={(e) => setKeyword(e.target.value)} />
        </div>
        <div className="field">
          <label>状态</label>
          <select className="select" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="all">全部状态</option>
            <option value="active">进行中</option>
            <option value="completed">已完成</option>
            <option value="ended">已结束</option>
          </select>
        </div>
        <div className="field">
          <label>排序</label>
          <select className="select" value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
            <option value="time">按最近活动</option>
            <option value="score">按最佳评分</option>
          </select>
        </div>
        <div className="field" style={{ alignContent: 'end' }}>
          <Btn type="submit">查询</Btn>
        </div>
      </form>

      {error && <p className="form-error" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}

      {loading && !pageData ? (
        <LoadingBlock>正在加载面试记录…</LoadingBlock>
      ) : (pageData?.records || []).length === 0 ? (
        <div className="card empty">
          <p style={{ marginBottom: 'var(--space-4)' }}>还没有面试记录，从上传简历开始第一场。</p>
          <Btn variant="primary" onClick={() => onNavigate('upload')}>开始新面试</Btn>
        </div>
      ) : (
        <div className="history-card-list">
          {pageData!.records.map((row) => (
            <article className="card history-card" key={row.lineageId}>
              <div className="history-card-top">
                <div className="record-title">
                  <strong>{row.candidateName || '未命名候选人'}{row.jobTitle ? ` · ${row.jobTitle}` : ''}</strong>
                  <span>谱系 {row.lineageId.slice(0, 16)}… · 最近活动 {formatRelative(row.latestActivityAt)}</span>
                </div>
                <Badge tone={row.focusedBranchStatus === 2 ? undefined : row.focusedBranchStatus === 3 ? 'warn' : 'success'}>
                  {row.focusedBranchStatus === 2 ? '已完成' : row.focusedBranchStatus === 3 ? '已取消' : '进行中'}
                </Badge>
              </div>
              <div className="history-meta">
                <span>{row.branchCount ?? 0} 个分支（活跃 {row.activeBranchCount ?? 0}）</span>
                <span>当前阶段 {stageDisplay(row.focusedBranchStage)}</span>
                <span>{row.bestCompletedScore != null ? `最佳评分 ${row.bestCompletedScore}` : '暂无评分'}</span>
              </div>
              <div className="history-progress">
                <Meter value={row.focusedBranchProgress ?? 0} />
                <strong className="meta">{row.focusedBranchProgress ?? 0}%</strong>
              </div>
              <div className="history-actions">
                <Btn onClick={() => onReplay(row.lineageId, row.focusedBranchId)}>面试回放</Btn>
                {row.focusedBranchStatus === 1 ? (
                  <Btn variant="primary" onClick={() => onResume(row.lineageId, row.focusedBranchId)}>继续面试</Btn>
                ) : (
                  <Btn variant="ghost" onClick={() => onOpenReport(row.focusedBranchId)}>查看报告</Btn>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      <Pagination pageData={pageData} onPageChange={setCurrent} />
    </>
  );
}
