import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api';
import type { BranchTranscript, LineageTree } from '../types';
import { branchStatusText, formatDate, stageDisplay } from '../utils';
import { Badge, Btn, Dialog, EmptyState, LoadingBlock, Meter, toast } from '../components/ui';
import { uuid } from '../utils';
import type { ScreenKey } from '../components/AppShell';

export function ReplayView({
  lineageId,
  branchId,
  onBranchSwitch,
  onNavigate,
}: {
  lineageId: string | null;
  branchId: string | null;
  onBranchSwitch: (lineageId: string, branchId: string) => void;
  onNavigate: (screen: ScreenKey) => void;
}) {
  const [transcript, setTranscript] = useState<BranchTranscript | null>(null);
  const [tree, setTree] = useState<LineageTree | null>(null);
  const [loading, setLoading] = useState(false);
  const [branchDialogOpen, setBranchDialogOpen] = useState(false);
  const [forkTarget, setForkTarget] = useState<{ messageId: number; excerpt: string } | null>(null);
  const [forkAnswer, setForkAnswer] = useState('');
  const [forking, setForking] = useState(false);

  const load = useCallback(async () => {
    if (!lineageId || !branchId) return;
    setLoading(true);
    try {
      const [transcriptData, treeData] = await Promise.all([
        adminApi.portalBranchTranscript(branchId),
        adminApi.portalLineageTree(lineageId).catch(() => null),
      ]);
      setTranscript(transcriptData);
      setTree(treeData);
    } catch (err) {
      toast(err instanceof Error ? err.message : '回放加载失败', 'error');
    } finally {
      setLoading(false);
    }
  }, [lineageId, branchId]);

  useEffect(() => {
    setTranscript(null);
    setTree(null);
    if (lineageId && branchId) {
      void load();
    }
  }, [lineageId, branchId, load]);

  async function submitFork() {
    if (!transcript || !forkTarget) return;
    const answer = forkAnswer.trim();
    if (!answer) {
      toast('请输入新的回答', 'error');
      return;
    }
    const lastMessage = transcript.messages[transcript.messages.length - 1];
    setForking(true);
    try {
      const result = await adminApi.portalForkAttempt(transcript.branchId, {
        turnId: uuid(),
        triggerMessageId: forkTarget.messageId,
        candidateAnswer: answer,
        expectedFocusedBranchVersion: transcript.branchVersion,
        expectedFocusedTailMessageId: lastMessage?.id ?? null,
      });
      toast(`新分支「${result.branchId.slice(0, 8)}…」已创建，AI 正在生成新的回答`);
      setForkTarget(null);
      setForkAnswer('');
      onBranchSwitch(transcript.lineageId, result.branchId);
    } catch (err) {
      const message = err instanceof Error ? err.message : '分叉失败';
      if (message.includes('冲突') || message.includes('409')) {
        toast('分叉冲突：分支状态已变化，已刷新转录', 'error');
        void load();
      } else {
        toast(message, 'error');
      }
    } finally {
      setForking(false);
    }
  }

  if (!lineageId || !branchId) {
    return (
      <>
        <div className="page-head">
          <div className="page-head-copy">
            <h1>面试回放</h1>
            <p>查看当前分支的完整消息；可从符合条件的历史回答或待回答问题创建新分支。</p>
          </div>
        </div>
        <div className="card empty">
          <p style={{ marginBottom: 'var(--space-4)' }}>请先从面试记录选择一场面试。</p>
          <Btn variant="primary" onClick={() => onNavigate('history')}>前往面试记录</Btn>
        </div>
      </>
    );
  }

  const inheritedCount = (transcript?.messages || []).filter((message) => message.inherited).length;
  const forkableCount = (transcript?.messages || []).filter((message) => message.forkable).length;

  return (
    <>
      <Btn variant="ghost" className="flow-back" onClick={() => onNavigate('history')}>
        ← 返回面试记录
      </Btn>
      <div className="page-head">
        <div className="page-head-copy">
          <span className="meta">只读回放 · 分支 {branchId.slice(0, 12)}…</span>
          <h1>面试回放</h1>
          <p>查看当前分支的完整消息（含继承的祖先分支消息）；标记为可分叉的消息可创建新分支重新作答。</p>
        </div>
        <div className="head-actions">
          <Btn onClick={() => setBranchDialogOpen(true)}>选择面试分支</Btn>
        </div>
      </div>

      <div className="replay-layout">
        <div className="transcript">
          {loading && !transcript ? (
            <LoadingBlock>正在加载转录…</LoadingBlock>
          ) : (transcript?.messages || []).length === 0 ? (
            <div className="card empty">该分支还没有消息（可能 AI 正在生成首轮内容）。</div>
          ) : (
            transcript!.messages.map((message) => {
              const isUser = message.role === 'human';
              return (
                <article
                  key={message.id}
                  className={`${isUser ? 'transcript-message user' : 'transcript-message ai'}${message.inherited ? ' inherited' : ''}`}
                >
                  <span className="meta">
                    {isUser ? '候选人' : message.messageType === 'ai_feedback' ? '面试助手 · 反馈' : '面试助手'}
                    {' · '}
                    {message.inherited ? '继承消息' : '本分支'} · {formatDate(message.createdAt)}
                  </span>
                  <p>{message.content}</p>
                  {message.forkable && (
                    <button
                      type="button"
                      className="fork-button"
                      onClick={() => setForkTarget({ messageId: message.id, excerpt: message.content })}
                    >
                      {isUser ? '从此回答重新作答' : '从此处创建分支'}
                    </button>
                  )}
                </article>
              );
            })
          )}
        </div>

        <aside className="card branch-summary">
          <div className="branch-summary-head">
            <div>
              <span className="meta">当前查看</span>
              <h2 style={{ marginTop: 4 }}>{transcript?.branchLabel || '原始分支'}</h2>
            </div>
            <Badge tone={transcript?.status === 2 ? undefined : 'success'}>{branchStatusText(transcript?.status)}</Badge>
          </div>
          <p>{stageDisplay(transcript?.stage)} · 版本 v{transcript?.branchVersion ?? '-'}</p>
          <Meter value={Math.min(100, ((transcript?.messages.length || 0) / 30) * 100)} />
          <div className="branch-facts">
            <div className="branch-fact">
              <span>自有消息</span>
              <strong>{(transcript?.messages.length || 0) - inheritedCount}</strong>
            </div>
            <div className="branch-fact">
              <span>继承消息</span>
              <strong>{inheritedCount}</strong>
            </div>
            <div className="branch-fact">
              <span>可分叉点</span>
              <strong>{forkableCount}</strong>
            </div>
            <div className="branch-fact">
              <span>谱系分支</span>
              <strong>{tree?.nodes.length ?? '-'}</strong>
            </div>
          </div>
          <Btn onClick={() => setBranchDialogOpen(true)}>查看全部分支</Btn>
        </aside>
      </div>

      {branchDialogOpen && tree && (
        <Dialog
          title="选择面试分支"
          description="切换只改变当前查看分支，不会修改来源分支或触发模型调用。"
          onClose={() => setBranchDialogOpen(false)}
          wide
        >
          <div className="branch-list">
            {tree.nodes.map((node) => (
              <button
                type="button"
                key={node.branchId}
                className={node.branchId === branchId ? 'branch-card selected' : 'branch-card'}
                onClick={() => {
                  setBranchDialogOpen(false);
                  if (node.branchId !== branchId) {
                    onBranchSwitch(lineageId, node.branchId);
                  }
                }}
              >
                <span className="branch-card-head">
                  <strong>{node.branchLabel}</strong>
                  <Badge tone={node.status === 2 ? undefined : 'success'}>{branchStatusText(node.status)}</Badge>
                </span>
                <Meter value={node.progress ?? 0} />
                <p>
                  {stageDisplay(node.stage)} · {node.progress ?? 0}% · 自有 {node.ownedAssessmentCount ?? 0} / 继承 {node.inheritedAssessmentCount ?? 0}
                  {node.completedScore != null ? ` · 评分 ${node.completedScore}` : ''}
                </p>
                <p>最新活动：{formatDate(node.latestBusinessActivityAt)}</p>
                {node.evaluationSummary && <p>{node.evaluationSummary}</p>}
                {node.recoverableTurnId && (
                  <p className="warn-text">存在可恢复轮次（{node.recoverableTurnStatus}），可在工作台重试。</p>
                )}
              </button>
            ))}
          </div>
        </Dialog>
      )}

      {forkTarget && (
        <Dialog
          title="从历史消息创建分支"
          description={`分叉点：${forkTarget.excerpt.slice(0, 60)}${forkTarget.excerpt.length > 60 ? '…' : ''}`}
          onClose={() => {
            setForkTarget(null);
            setForkAnswer('');
          }}
        >
          <form
            className="dialog-form"
            onSubmit={(event) => {
              event.preventDefault();
              void submitFork();
            }}
          >
            <div className="field">
              <label>新的回答</label>
              <textarea
                className="textarea"
                required
                autoFocus
                placeholder="重新组织这一次回答；提交后才会真正创建分支"
                value={forkAnswer}
                onChange={(event) => setForkAnswer(event.target.value)}
              />
            </div>
            <div className="dialog-actions">
              <Btn
                onClick={() => {
                  setForkTarget(null);
                  setForkAnswer('');
                }}
              >
                取消
              </Btn>
              <button type="submit" className="btn btn-primary" aria-busy={forking ? 'true' : undefined} disabled={forking}>
                {forking ? '创建中…' : '提交并创建分支'}
              </button>
            </div>
          </form>
        </Dialog>
      )}
    </>
  );
}
