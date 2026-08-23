import { useCallback, useEffect, useRef, useState } from 'react';
import { adminApi } from '../api';
import type { BranchTranscript, TurnAttempt } from '../types';
import { STAGE_ORDER, stageDisplay } from '../utils';
import { Badge, Btn, EmptyState, Icon, LoadingBlock, Meter, toast } from '../components/ui';
import type { ScreenKey } from '../components/AppShell';

const POLL_INTERVAL_MS = 1500;

const STAGE_HINTS: Record<string, string> = {
  opening: '开场介绍完成后进入自我介绍环节。',
  self_introduction: '请提交候选人的自我介绍。',
  project_qna: '项目问答进行中；追问只补充证据，不占独立项目题数量。',
  technical_qna: '技术题环节，题目由题库检索与简历内容共同决定。',
  concluded: '面试已结束，可以生成评估报告。',
};

export function WorkspaceView({
  lineageId,
  branchId,
  onOpenReport,
  onNavigate,
}: {
  lineageId: string | null;
  branchId: string | null;
  onOpenReport: (sessionId: string) => void;
  onNavigate: (screen: ScreenKey) => void;
}) {
  const [transcript, setTranscript] = useState<BranchTranscript | null>(null);
  const [loading, setLoading] = useState(false);
  const [pendingTurn, setPendingTurn] = useState<TurnAttempt | null>(null);
  const [input, setInput] = useState('');
  const [mobilePane, setMobilePane] = useState<'outline' | 'conversation' | 'insights'>('conversation');
  const [generating, setGenerating] = useState(false);
  const feedRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);

  const active = Boolean(lineageId && branchId);

  const loadTranscript = useCallback(async () => {
    if (!branchId) return;
    setLoading(true);
    try {
      setTranscript(await adminApi.portalBranchTranscript(branchId));
    } catch (err) {
      toast(err instanceof Error ? err.message : '转录加载失败', 'error');
    } finally {
      setLoading(false);
    }
  }, [branchId]);

  useEffect(() => {
    setTranscript(null);
    setPendingTurn(null);
    setInput('');
    if (branchId) {
      void loadTranscript();
    }
  }, [branchId, loadTranscript]);

  useEffect(() => {
    if (feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight;
    }
  }, [transcript]);

  /* 轮询进行中的轮次（PROCESSING → 终态后刷新转录） */
  useEffect(() => {
    if (!pendingTurn || pendingTurn.status !== 'PROCESSING') {
      return;
    }
    const timer = window.setInterval(() => {
      adminApi
        .portalTurnAttempt(pendingTurn.turnId)
        .then((attempt) => {
          if (attempt.status === 'PROCESSING' || attempt.status === 'CANCEL_REQUESTED') {
            setPendingTurn(attempt);
            return;
          }
          setPendingTurn(null);
          if (attempt.status === 'COMPLETED') {
            toast('本轮回答已处理完成');
          } else {
            toast(`本轮处理未完成（${attempt.status}${attempt.errorCode ? ' · ' + attempt.errorCode : ''}）`, 'error');
          }
          void loadTranscript();
        })
        .catch(() => {
          /* 短暂网络异常时继续轮询，直到终态 */
        });
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [pendingTurn, loadTranscript]);

  async function submitAnswer() {
    if (!branchId || !transcript) return;
    const answer = input.trim();
    if (!answer) {
      inputRef.current?.focus();
      toast('请先输入候选人的回答', 'error');
      return;
    }
    const lastMessage = transcript.messages[transcript.messages.length - 1];
    try {
      const attempt = await adminApi.portalCreateTurnAttempt(branchId, {
        turnId: crypto.randomUUID(),
        candidateAnswer: answer,
        expectedBranchVersion: transcript.branchVersion,
        expectedTailMessageId: lastMessage?.id ?? null,
      });
      setPendingTurn(attempt);
      setInput('');
      toast('回答已提交，AI 面试官正在处理本轮…');
    } catch (err) {
      const message = err instanceof Error ? err.message : '提交失败';
      if (message.includes('冲突') || message.includes('conflict') || message.includes('409')) {
        toast('轮次冲突：面试状态已变化，正在刷新转录…', 'error');
      } else {
        toast(message, 'error');
      }
      void loadTranscript();
    }
  }

  async function retryPending() {
    if (!pendingTurn) return;
    try {
      const attempt = await adminApi.portalRetryTurnAttempt(pendingTurn.turnId);
      setPendingTurn(attempt);
      toast('已重试该轮处理');
    } catch (err) {
      toast(err instanceof Error ? err.message : '重试失败', 'error');
    }
  }

  async function generateReport() {
    if (!branchId) return;
    setGenerating(true);
    try {
      await adminApi.generateEvaluation(branchId);
      toast('评估报告已生成');
      onOpenReport(branchId);
    } catch (err) {
      toast(err instanceof Error ? err.message : '报告生成失败', 'error');
    } finally {
      setGenerating(false);
    }
  }

  if (!active) {
    return (
      <>
        <div className="page-head">
          <div className="page-head-copy">
            <h1>面试工作台</h1>
            <p>围绕当前问题组织对话、证据和阶段判断，减少跨页面来回切换。</p>
          </div>
        </div>
        <div className="card empty">
          <p style={{ marginBottom: 'var(--space-4)' }}>当前没有进行中的面试。</p>
          <Btn variant="primary" onClick={() => onNavigate('upload')}>
            上传简历开始新面试
          </Btn>
        </div>
      </>
    );
  }

  const stage = transcript?.stage || 'opening';
  const currentStageIndex = Math.max(0, STAGE_ORDER.indexOf(stage));
  const concluded = stage === 'concluded';
  const aiMessages = (transcript?.messages || []).filter((message) => message.role === 'ai');
  const lastAiQuestion = [...(transcript?.messages || [])].reverse().find((message) => message.messageType === 'ai_question');
  const progressPct = Math.round(((currentStageIndex + (concluded ? 0 : 1)) / STAGE_ORDER.length) * 100);

  return (
    <>
      <div className="page-head">
        <div className="page-head-copy">
          <h1>面试工作台</h1>
          <p>当前分支「{transcript?.branchLabel || '原始分支'}」· 阶段 {stageDisplay(stage)}；提交回答后自动推进到下一轮。</p>
        </div>
        <div className="head-actions">
          {pendingTurn?.status === 'FAILED' || pendingTurn?.status === 'INTERRUPTED' ? (
            <Btn onClick={() => void retryPending()}>重试失败轮次</Btn>
          ) : null}
          <Btn
            variant={concluded ? 'primary' : 'secondary'}
            busy={generating}
            disabled={generating || !transcript}
            onClick={() => void generateReport()}
          >
            {concluded ? '生成评估报告' : '结束并生成报告'}
          </Btn>
        </div>
      </div>

      <div className="mobile-pane-tabs" role="tablist" aria-label="工作台视图">
        {(['outline', 'conversation', 'insights'] as const).map((pane) => (
          <button
            key={pane}
            type="button"
            role="tab"
            aria-selected={mobilePane === pane}
            className={mobilePane === pane ? 'pane-tab active' : 'pane-tab'}
            onClick={() => setMobilePane(pane)}
          >
            {pane === 'outline' ? '大纲' : pane === 'conversation' ? '对话' : '观察'}
          </button>
        ))}
      </div>

      <div className="workspace-grid">
        <article className={mobilePane === 'outline' ? 'card workspace-panel mobile-active' : 'card workspace-panel'} data-pane="outline">
          <div className="panel-head">
            <h2>面试大纲</h2>
            <span className="meta">{String(currentStageIndex + 1).padStart(2, '0')} / {String(STAGE_ORDER.length).padStart(2, '0')}</span>
          </div>
          <div className="stage-list">
            {STAGE_ORDER.map((item, index) => (
              <button
                type="button"
                key={item}
                className={index === currentStageIndex ? 'stage-item current' : index < currentStageIndex ? 'stage-item done' : 'stage-item'}
              >
                <span className="stage-num">{String(index + 1).padStart(2, '0')}</span>
                <span className="stage-copy">
                  <strong>{stageDisplay(item)}</strong>
                  <span>{index < currentStageIndex ? '已完成' : index === currentStageIndex ? (concluded ? '当前阶段' : '当前问题') : '待开始'}</span>
                </span>
              </button>
            ))}
          </div>
          <p className="outline-note">
            追问只补充证据，不占用独立项目题数量；中途离开后可从面试记录恢复当前分支继续。
          </p>
        </article>

        <article className={mobilePane === 'conversation' ? 'card workspace-panel chat-card mobile-active' : 'card workspace-panel chat-card'} data-pane="conversation">
          <div className="panel-head">
            <div className="candidate">
              <span className="avatar">面</span>
              <span>
                <span className="candidate-name">{transcript?.branchLabel || '模拟面试'}</span>
                <span className="candidate-role">{stageDisplay(stage)} · 分支 v{transcript?.branchVersion ?? '-'}</span>
              </span>
            </div>
            <span>
              {pendingTurn?.status === 'PROCESSING' ? (
                <Badge tone="warn">AI 处理中…</Badge>
              ) : (
                <Badge tone={concluded ? undefined : 'success'}>{concluded ? '已完成' : '进行中'}</Badge>
              )}
            </span>
          </div>
          <div className="chat-feed" ref={feedRef} aria-live="polite">
            {loading && !transcript ? (
              <LoadingBlock>正在加载面试转录…</LoadingBlock>
            ) : (transcript?.messages || []).length === 0 ? (
              <EmptyState>开场白生成中，稍候片刻即可看到第一条消息。</EmptyState>
            ) : (
              transcript!.messages.map((message) => {
                if (message.role === 'system') {
                  return (
                    <div className="message ai" key={message.id}>
                      <span className="message-label">系统 · {message.inherited ? '继承' : '本分支'}</span>
                      <div className="bubble" style={{ fontStyle: 'italic' }}>{message.content}</div>
                    </div>
                  );
                }
                const isUser = message.role === 'human';
                return (
                  <div className={isUser ? 'message user' : 'message ai'} key={message.id}>
                    <span className="message-label">{isUser ? '候选人' : message.messageType === 'ai_feedback' ? '面试助手 · 反馈' : '面试助手'}{message.inherited ? ' · 继承' : ''}</span>
                    <div className="bubble">{message.content}</div>
                    {!isUser && message.stage && (
                      <span className="evidence-chip">{stageDisplay(message.stage)}</span>
                    )}
                  </div>
                );
              })
            )}
            {pendingTurn?.status === 'PROCESSING' && (
              <div className="message ai">
                <span className="message-label">面试助手</span>
                <div className="bubble" style={{ opacity: 0.7 }}>正在生成本轮回复…（提交后由服务端持久化处理，离开页面不会中断）</div>
              </div>
            )}
          </div>
          <form
            className="composer"
            onSubmit={(event) => {
              event.preventDefault();
              void submitAnswer();
            }}
          >
            <div className="composer-row">
              <button
                type="button"
                className="btn btn-secondary icon-btn"
                aria-label="语音输入（占位）"
                onClick={() => toast('语音输入尚未接入，请使用文本输入')}
              >
                <Icon name="mic" />
              </button>
              <textarea
                ref={inputRef}
                className="textarea"
                placeholder="输入候选人的回答…"
                aria-label="候选人回答"
                value={input}
                disabled={concluded || Boolean(pendingTurn)}
                onChange={(event) => setInput(event.target.value)}
              />
              <button type="submit" className="btn btn-primary" disabled={concluded || Boolean(pendingTurn)}>
                提交回答
              </button>
            </div>
            <p className="composer-help">
              {concluded
                ? '面试已结束，请生成评估报告。'
                : pendingTurn
                  ? '本轮处理中，完成后输入框自动恢复。'
                  : `提交后生成下一轮问题；当前等待回答的问题：${lastAiQuestion ? lastAiQuestion.content.slice(0, 40) + (lastAiQuestion.content.length > 40 ? '…' : '') : '—'}`}
            </p>
          </form>
        </article>

        <aside className={mobilePane === 'insights' ? 'card workspace-panel mobile-active' : 'card workspace-panel'} data-pane="insights">
          <div className="panel-head">
            <h2>实时观察</h2>
            <span className="meta">实时数据</span>
          </div>
          <div className="insight-list">
            <div className="insight">
              <div className="insight-top">
                <span>阶段进度</span>
                <span className="meta">{stageDisplay(stage)}</span>
              </div>
              <Meter value={progressPct} />
              <p>{currentStageIndex + 1} / {STAGE_ORDER.length} 个阶段；{STAGE_HINTS[stage] || ''}</p>
            </div>
            <div className="insight">
              <div className="insight-top">
                <span>消息规模</span>
                <span className="meta">{transcript?.messages.length ?? 0} 条</span>
              </div>
              <Meter value={Math.min(100, ((transcript?.messages.length || 0) / 30) * 100)} />
              <p>AI 消息 {aiMessages.length} 条；分支版本 v{transcript?.branchVersion ?? '-'}（每轮 +1，乐观锁依据）。</p>
            </div>
            <div className="insight">
              <div className="insight-top">
                <span>轮次状态</span>
                <span className="meta">{pendingTurn ? pendingTurn.status : '空闲'}</span>
              </div>
              <Meter value={pendingTurn ? 60 : 100} />
              <p>
                {pendingTurn
                  ? `turnId ${pendingTurn.turnId.slice(0, 8)}… 处理中，每 1.5s 轮询一次状态。`
                  : '可以提交下一轮回答；逐轮评分会持久化到评分记录。'}
              </p>
            </div>
          </div>
          <div className="note-box">
            <strong>下一步建议</strong>
            <p>
              {concluded
                ? '生成本分支的评估报告，或回到面试记录查看全部谱系。'
                : '回答尽量给出结论、依据与可验证结果，便于 AI 面试官追问细节。'}
            </p>
          </div>
        </aside>
      </div>
    </>
  );
}
