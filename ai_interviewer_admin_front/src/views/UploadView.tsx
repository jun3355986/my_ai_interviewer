import { useCallback, useEffect, useRef, useState } from 'react';
import { adminApi } from '../api';
import type { JobRow, ResumeUploadResult } from '../types';
import { Badge, Btn, Icon, LoadingBlock, toast } from '../components/ui';
import { uuid } from '../utils';
import type { ScreenKey } from '../components/AppShell';

const MAX_BYTES = 10 * 1024 * 1024;

export function UploadView({
  onNavigate,
  onInterviewStarted,
}: {
  onNavigate: (screen: ScreenKey) => void;
  onInterviewStarted: (lineageId: string, branchId: string) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [parsed, setParsed] = useState<ResumeUploadResult | null>(null);
  const [parsing, setParsing] = useState(false);
  const [jobs, setJobs] = useState<JobRow[]>([]);
  const [jobId, setJobId] = useState<string>('');
  const [starting, setStarting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    adminApi
      .jobs({ current: 1, size: 50, status: '1' })
      .then((page) => {
        setJobs(page.records);
        if (page.records.length > 0) {
          setJobId(String(page.records[0].id));
        }
      })
      .catch(() => setJobs([]));
  }, []);

  const acceptFile = useCallback((next: File | null) => {
    if (!next) return;
    const isPdf = next.type === 'application/pdf' || next.name.toLowerCase().endsWith('.pdf');
    if (!isPdf) {
      toast('请选择 PDF 格式的简历', 'error');
      return;
    }
    if (next.size > MAX_BYTES) {
      toast('简历不能超过 10 MB', 'error');
      return;
    }
    setFile(next);
    setParsed(null);
  }, []);

  async function parseResume() {
    if (!file) return;
    setParsing(true);
    try {
      const result = await adminApi.uploadPortalResume(file);
      setParsed(result);
      toast(`简历解析完成：${result.name || result.filename}`);
    } catch (err) {
      toast(err instanceof Error ? err.message : '解析失败', 'error');
    } finally {
      setParsing(false);
    }
  }

  async function startInterview() {
    setStarting(true);
    try {
      const payload = {
        turnId: uuid(),
        resumeId: parsed?.resumeId ?? null,
        jobId: jobId ? Number(jobId) : null,
      };
      const result = await adminApi.portalStartAttempt(payload);
      toast('面试已创建，正在生成开场白…');
      onInterviewStarted(result.lineageId, result.branchId);
    } catch (err) {
      toast(err instanceof Error ? err.message : '发起面试失败', 'error');
    } finally {
      setStarting(false);
    }
  }

  async function skipResumeAndStart() {
    setStarting(true);
    try {
      const result = await adminApi.portalStartAttempt({
        turnId: uuid(),
        resumeId: null,
        jobId: jobId ? Number(jobId) : null,
      });
      toast('已跳过简历，将使用通用面试大纲');
      onInterviewStarted(result.lineageId, result.branchId);
    } catch (err) {
      toast(err instanceof Error ? err.message : '发起面试失败', 'error');
    } finally {
      setStarting(false);
    }
  }

  return (
    <>
      <Btn variant="ghost" className="flow-back" onClick={() => onNavigate('dashboard')}>
        ← 返回总览
      </Btn>
      <div className="page-head">
        <div className="page-head-copy">
          <span className="meta">新面试 · 步骤 1 / 2</span>
          <h1>上传简历</h1>
          <p>简历经 Python AI 解析后入库，面试官将根据教育背景、工作经历和目标岗位生成针对性问题。</p>
        </div>
      </div>
      <div className="upload-layout">
        <div>
          <label
            className={dragging ? 'upload-drop dragging' : 'upload-drop'}
            onDragOver={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={(event) => {
              event.preventDefault();
              setDragging(false);
            }}
            onDrop={(event) => {
              event.preventDefault();
              setDragging(false);
              acceptFile(event.dataTransfer.files?.[0] || null);
            }}
          >
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf,.pdf"
              onClick={(event) => event.stopPropagation()}
              onChange={(event) => acceptFile(event.target.files?.[0] || null)}
            />
            <span className="upload-drop-inner">
              <span className="upload-mark">
                <Icon name="upload" size={26} />
              </span>
              <strong>{file ? file.name : '点击选择 PDF 简历'}</strong>
              <p>{file ? `${(file.size / 1024).toFixed(1)} KB · 点击重新选择` : '也可以将文件拖放到这里'}</p>
              <span className="meta">PDF 格式 · 最大 10 MB</span>
            </span>
          </label>

          {parsing && <LoadingBlock>正在解析简历结构…</LoadingBlock>}

          {parsed && (
            <div className="card" style={{ marginTop: 'var(--space-4)' }}>
              <div className="chart-head" style={{ marginBottom: 'var(--space-3)' }}>
                <div>
                  <h2>解析结果</h2>
                  <p>已入库为简历 #{parsed.resumeId}，将作为本次面试的上下文。</p>
                </div>
                <Badge tone="success">解析成功</Badge>
              </div>
              <div className="branch-facts">
                <div className="branch-fact">
                  <span>候选人</span>
                  <strong>{parsed.name || '未识别'}</strong>
                </div>
                <div className="branch-fact">
                  <span>工作年限</span>
                  <strong>{parsed.workYears || '—'}</strong>
                </div>
                <div className="branch-fact">
                  <span>学历 / 院校</span>
                  <strong>{[parsed.education, parsed.university].filter(Boolean).join(' · ') || '—'}</strong>
                </div>
                <div className="branch-fact">
                  <span>技能 / 项目</span>
                  <strong>{parsed.skillCount ?? 0} 项 / {parsed.projectCount ?? 0} 个</strong>
                </div>
              </div>
              {parsed.preview && (
                <p className="muted" style={{ fontSize: 'var(--text-xs)', marginTop: 'var(--space-3)' }}>
                  {parsed.preview}
                </p>
              )}
            </div>
          )}
        </div>

        <aside className="upload-side">
          <article className="card">
            <h2>上传提示</h2>
            <ul className="tip-list">
              <li>确保简历内容清晰完整，包含教育背景和工作经历。</li>
              <li>AI 会分析简历，并结合岗位要求生成面试问题。</li>
              <li>简历信息只用于面试流程和评估报告。</li>
            </ul>
          </article>
          <article className="card">
            <h2>目标岗位</h2>
            <div className="field" style={{ marginTop: 'var(--space-4)' }}>
              <label htmlFor="uploadRole">选择岗位</label>
              <select id="uploadRole" className="select" value={jobId} onChange={(event) => setJobId(event.target.value)}>
                <option value="">不绑定岗位</option>
                {jobs.map((job) => (
                  <option key={job.id} value={String(job.id)}>
                    {job.title}
                    {job.company ? ` · ${job.company}` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="upload-actions">
              <Btn
                variant="primary"
                busy={starting}
                disabled={!file || parsing || Boolean(parsed)}
                onClick={() => void parseResume()}
              >
                {parsed ? '简历已就绪' : parsing ? '解析中…' : '分析简历并继续'}
              </Btn>
              <Btn
                variant="primary"
                busy={starting}
                disabled={starting || (!parsed && Boolean(file))}
                onClick={() => void startInterview()}
              >
                进入工作台开始面试
              </Btn>
              <Btn variant="ghost" busy={starting} disabled={starting} onClick={() => void skipResumeAndStart()}>
                跳过简历直接开始
              </Btn>
            </div>
          </article>
        </aside>
      </div>
    </>
  );
}
