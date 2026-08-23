import { useEffect, useState } from 'react';
import { adminApi } from '../api';
import type { DashboardOverview, InterviewRow, PageResult } from '../types';
import { formatRelative } from '../utils';
import { Badge, Icon, LoadingBlock } from '../components/ui';
import type { ScreenKey } from '../components/AppShell';

export function DashboardView({
  overview,
  loadingOverview,
  onNavigate,
}: {
  overview: DashboardOverview | null;
  loadingOverview: boolean;
  onNavigate: (screen: ScreenKey) => void;
}) {
  const [recentInterviews, setRecentInterviews] = useState<PageResult<InterviewRow> | null>(null);

  useEffect(() => {
    adminApi
      .interviews({ current: 1, size: 3 })
      .then(setRecentInterviews)
      .catch(() => setRecentInterviews(null));
  }, []);

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
    <>
      <div className="page-head">
        <div className="page-head-copy">
          <span className="meta">运营数据来自管理后台</span>
          <h1>运营总览</h1>
          <p>查看平台运营指标、近 14 天面试趋势与分数分布，或从一次真实面试开始。</p>
        </div>
      </div>

      <div className="welcome-grid">
        <article className="card start-card">
          <div>
            <span className="meta">下一步</span>
            <h2 style={{ fontSize: 'var(--text-2xl)', marginTop: 'var(--space-2)' }}>开始新面试</h2>
            <p>上传 PDF 简历，结合岗位要求生成有针对性的项目题、技术题和追问；面试过程持久化，可回放、分叉与评估。</p>
          </div>
          <button type="button" className="btn btn-primary" onClick={() => onNavigate('upload')}>
            上传简历并开始
          </button>
        </article>
        <aside className="card activity-card">
          <div className="chart-head" style={{ marginBottom: 0 }}>
            <div>
              <h2>最近活动</h2>
              <p>最近 3 场面试会话</p>
            </div>
            <button type="button" className="row-action" onClick={() => onNavigate('history')}>
              查看全部
            </button>
          </div>
          <div className="activity-list">
            {(recentInterviews?.records || []).map((row) => (
              <div className="activity-row" key={row.id}>
                <span className="activity-icon">
                  <Icon name="workspace" size={18} />
                </span>
                <span>
                  <strong>{row.candidateName || row.jobTitle || row.id.slice(0, 12)}</strong>
                  <span>
                    {row.jobTitle || '未绑定职位'} · {formatRelative(row.startedAt || row.createdAt)}
                  </span>
                </span>
                <Badge tone={row.status === 2 ? undefined : 'success'}>{row.status === 2 ? '已完成' : '进行中'}</Badge>
              </div>
            ))}
            {!recentInterviews && <div className="muted" style={{ fontSize: 'var(--text-sm)' }}>正在加载最近活动…</div>}
            {recentInterviews && recentInterviews.records.length === 0 && (
              <div className="muted" style={{ fontSize: 'var(--text-sm)' }}>暂无面试活动。</div>
            )}
          </div>
        </aside>
      </div>

      {loadingOverview && !overview ? (
        <LoadingBlock>正在加载运营概览…</LoadingBlock>
      ) : (
        <>
          <div className="metric-grid">
            {cards.map((card) => (
              <article className="card metric-card" key={card.label}>
                <span>{card.label}</span>
                <strong>{card.value}</strong>
                <small>来自 dashboard/overview</small>
              </article>
            ))}
          </div>

          <div className="dashboard-charts">
            <article className="card">
              <div className="chart-head">
                <div>
                  <h2>近 14 天面试趋势</h2>
                  <p>来自管理端 DashboardOverview</p>
                </div>
                <span className="meta">interviewTrend</span>
              </div>
              {trend.length === 0 ? (
                <div className="muted" style={{ fontSize: 'var(--text-sm)' }}>暂无趋势数据。</div>
              ) : (
                <div className="bar-chart" role="img" aria-label="近十四天面试趋势">
                  {trend.map((item) => (
                    <div className="bar" key={item.date} title={`${item.date}: ${item.count} 场`}>
                      <span style={{ height: `${Math.max(8, (item.count / maxTrend) * 100)}%` }} />
                      <small>{item.date.slice(5)}</small>
                    </div>
                  ))}
                </div>
              )}
            </article>
            <article className="card">
              <div className="chart-head">
                <div>
                  <h2>分数分布</h2>
                  <p>运营报告视角</p>
                </div>
              </div>
              <div className="score-bars">
                {(overview?.scoreDistribution || []).map((item) => (
                  <div className="score-bar" key={item.range}>
                    <span>{item.range}</span>
                    <div className="meter">
                      <span style={{ width: `${Math.max(3, (item.count / maxScore) * 100)}%` }} />
                    </div>
                    <span className="num">{item.count}</span>
                  </div>
                ))}
                {(overview?.scoreDistribution || []).length === 0 && (
                  <div className="muted" style={{ fontSize: 'var(--text-sm)' }}>暂无评分数据。</div>
                )}
              </div>
            </article>
          </div>
        </>
      )}
    </>
  );
}
