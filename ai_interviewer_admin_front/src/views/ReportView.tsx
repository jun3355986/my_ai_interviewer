import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api';
import type { EvaluationDetail } from '../types';
import { formatDate, splitStrengths } from '../utils';
import { Badge, Btn, EmptyState, LoadingBlock, Meter, toast } from '../components/ui';
import type { ScreenKey } from '../components/AppShell';

export function ReportView({
  sessionId,
  onNavigate,
}: {
  sessionId: string | null;
  onNavigate: (screen: ScreenKey) => void;
}) {
  const [evaluation, setEvaluation] = useState<EvaluationDetail | null>(null);
  const [missing, setMissing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [exporting, setExporting] = useState(false);

  const load = useCallback(async () => {
    if (!sessionId) return;
    setLoading(true);
    setMissing(false);
    try {
      setEvaluation(await adminApi.evaluationBySession(sessionId));
    } catch {
      setEvaluation(null);
      setMissing(true);
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    setEvaluation(null);
    if (sessionId) {
      void load();
    } else {
      setMissing(false);
    }
  }, [sessionId, load]);

  async function generate() {
    if (!sessionId) return;
    setGenerating(true);
    try {
      const detail = await adminApi.generateEvaluation(sessionId);
      setEvaluation(detail);
      setMissing(false);
      toast('评估报告已生成');
    } catch (err) {
      toast(err instanceof Error ? err.message : '生成失败（面试可能尚未结束）', 'error');
    } finally {
      setGenerating(false);
    }
  }

  function exportReport() {
    if (!evaluation) return;
    setExporting(true);
    try {
      const lines = [
        `# 面试评估报告 · ${evaluation.username || evaluation.sessionId}`,
        '',
        `- 报告编号：${evaluation.id}`,
        `- 面试会话：${evaluation.sessionId}`,
        `- 岗位：${evaluation.jobTitle || '-'}`,
        `- 生成时间：${formatDate(evaluation.createdAt)}`,
        `- 综合评分：${evaluation.overallScore ?? '-'}`,
        '',
        '## 维度评分',
        `- 技术深度：${evaluation.technicalScore ?? '-'}`,
        `- 逻辑思维：${evaluation.logicScore ?? '-'}`,
        `- 经验匹配：${evaluation.experienceScore ?? '-'}`,
        `- 沟通表达：${evaluation.communicationScore ?? '-'}`,
        `- 平均分：${evaluation.averageScore ?? '-'}`,
        '',
        '## 总结',
        evaluation.summary || '—',
        '',
        '## 优势',
        ...(splitStrengths(evaluation.strengths).map((item) => `- ${item}`) || ['- 无']),
        '',
        '## 风险',
        ...(splitStrengths(evaluation.weaknesses).map((item) => `- ${item}`) || ['- 无']),
        '',
        '## 建议',
        evaluation.recommendation || '—',
        '',
        '## 详细反馈',
        evaluation.detailedFeedback || '—',
      ];
      const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `evaluation-${evaluation.sessionId.slice(0, 12)}.md`;
      anchor.click();
      URL.revokeObjectURL(url);
      toast('报告已导出为 Markdown');
    } finally {
      setExporting(false);
    }
  }

  const dimensions = [
    { label: '技术深度', value: evaluation?.technicalScore },
    { label: '逻辑思维', value: evaluation?.logicScore },
    { label: '经验匹配', value: evaluation?.experienceScore },
    { label: '沟通表达', value: evaluation?.communicationScore },
  ];
  const overall = evaluation?.overallScore;
  const ringPct = overall == null ? 0 : Math.max(0, Math.min(100, overall));

  return (
    <>
      <div className="page-head">
        <div className="page-head-copy">
          <h1>评估报告</h1>
          <p>分数、证据和建议放在同一阅读路径中；数据来自持久化评估（逐轮评分聚合）。</p>
        </div>
        <div className="head-actions">
          {evaluation && (
            <Btn busy={exporting} disabled={exporting} onClick={exportReport}>
              导出报告
            </Btn>
          )}
        </div>
      </div>

      {!sessionId ? (
        <div className="card empty">
          <p style={{ marginBottom: 'var(--space-4)' }}>从面试记录或工作台选择一场已完成面试查看报告。</p>
          <Btn variant="primary" onClick={() => onNavigate('history')}>前往面试记录</Btn>
        </div>
      ) : loading ? (
        <LoadingBlock>正在加载评估报告…</LoadingBlock>
      ) : missing || !evaluation ? (
        <div className="card empty">
          <p style={{ marginBottom: 'var(--space-4)' }}>该面试分支尚未生成评估报告。</p>
          <Btn variant="primary" busy={generating} disabled={generating} onClick={() => void generate()}>
            {generating ? '生成中…' : '生成评估报告'}
          </Btn>
        </div>
      ) : (
        <div className="report-grid">
          <div className="card">
            <div className="score-hero">
              <div
                className="score-ring"
                role="img"
                aria-label={`综合评分 ${overall ?? '-'} 分`}
                style={{ background: `conic-gradient(var(--accent) 0 ${ringPct}%, var(--surface) ${ringPct}% 100%)` }}
              >
                <div className="score-value">
                  <strong>{overall ?? '-'}</strong>
                  <span>综合评分</span>
                </div>
              </div>
              <div className="score-summary">
                <span className="badge">{evaluation.username || '候选人'}{evaluation.jobTitle ? ` · ${evaluation.jobTitle}` : ''}</span>
                <h2>评估报告 #{evaluation.id}</h2>
                <p>{evaluation.summary || '暂无总结。'}</p>
                <div className="branch-facts">
                  <div className="branch-fact">
                    <span>回答 / 总题数</span>
                    <strong>{evaluation.answeredQuestions ?? '-'} / {evaluation.totalQuestions ?? '-'}</strong>
                  </div>
                  <div className="branch-fact">
                    <span>平均分 / 时长</span>
                    <strong>{evaluation.averageScore ?? '-'} · {evaluation.durationMinutes ?? '-'} 分钟</strong>
                  </div>
                </div>
              </div>
            </div>
            <div className="dimension-list">
              {dimensions.map((dimension) => (
                <div className="dimension" key={dimension.label}>
                  <span>{dimension.label}</span>
                  <Meter value={dimension.value ?? 0} />
                  <span className="num">{dimension.value ?? '-'}</span>
                </div>
              ))}
            </div>
          </div>

          <aside className="card recommendation">
            <span className="meta">综合建议 · {formatDate(evaluation.createdAt)}</span>
            <h2>{evaluation.recommendation || '暂无明确建议'}</h2>
            <p>{evaluation.detailedFeedback || '暂无详细反馈。'}</p>
            <div className="evidence-list">
              <div className="evidence-item">
                <strong>优势证据</strong>
                {splitStrengths(evaluation.strengths).length > 0 ? (
                  <ul>
                    {splitStrengths(evaluation.strengths).map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                ) : (
                  <p>暂无记录。</p>
                )}
              </div>
              <div className="evidence-item">
                <strong>风险证据</strong>
                {splitStrengths(evaluation.weaknesses).length > 0 ? (
                  <ul>
                    {splitStrengths(evaluation.weaknesses).map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                ) : (
                  <p>暂无记录。</p>
                )}
              </div>
            </div>
            <p className="meta" style={{ marginTop: 'var(--space-4)' }}>
              会话 {evaluation.sessionId}
            </p>
          </aside>
        </div>
      )}
    </>
  );
}
