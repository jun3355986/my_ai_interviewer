import { useEffect, useRef, useState, type ReactNode } from 'react';

/* ─── 图标（与设计稿同源的内联 SVG） ─── */
const iconPaths: Record<string, ReactNode> = {
  dashboard: <><path d="m3 11 9-7 9 7v9H3zM9 20v-6h6v6" /></>,
  workspace: <><path d="M4 4h16v16H4zM8 9h8M8 13h5" /></>,
  history: <><path d="M5 4h14v16H5zM8 8h8M8 12h8M8 16h5" /></>,
  report: <><path d="M5 19V9M12 19V5M19 19v-7" /></>,
  users: <><path d="M16 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 10a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM17 11h5M19.5 8.5v5" /></>,
  observability: <><path d="M3 12h4l2-6 4 12 2-6h6" /></>,
  settings: <><circle cx="12" cy="12" r="3" /><path d="M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.4 1A7 7 0 0 0 14.8 6L14.5 3h-5L9.2 6a7 7 0 0 0-1.7 1.1l-2.4-1-2 3.4 2 1.5a7 7 0 0 0 0 2l-2 1.5 2 3.4 2.4-1A7 7 0 0 0 9.2 18l.3 3h5l.3-3a7 7 0 0 0 1.7-1.1l2.4 1 2-3.4-2-1.5a7 7 0 0 0 .1-1z" /></>,
  audit: <><path d="M6 3h12v18H6zM9 8h6M9 12h6M9 16h4" /></>,
  plus: <><path d="M12 5v14M5 12h14" /></>,
  close: <><path d="m6 6 12 12M18 6 6 18" /></>,
  mic: <><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M5 11a7 7 0 0 0 14 0M12 18v3" /></>,
  upload: <><path d="M12 16V4M8 8l4-4 4 4M4 14v6h16v-6" /></>,
  bell: <><path d="M18 8a6 6 0 1 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /><circle cx="18" cy="5" r="2" fill="var(--danger)" stroke="var(--surface)" /></>,
  play: <><path d="M6 4l14 8-14 8z" /></>,
};

export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" style={{ width: size, height: size }}>
      {iconPaths[name]}
    </svg>
  );
}

/* ─── 按钮 / 徽章 / 进度条 ─── */
export function Btn({
  variant = 'secondary',
  busy,
  children,
  className = '',
  ...rest
}: {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  busy?: boolean;
  children: ReactNode;
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type="button" className={`btn btn-${variant} ${className}`} aria-busy={busy ? 'true' : undefined} disabled={busy || rest.disabled} {...rest}>
      {children}
    </button>
  );
}

export function Badge({ tone, children }: { tone?: 'success' | 'warn' | 'danger'; children: ReactNode }) {
  return <span className={`badge ${tone || ''}`.trim()}>{children}</span>;
}

export function Meter({ value }: { value: number }) {
  const pct = Math.max(0, Math.min(100, Math.round(value)));
  return (
    <div className="meter" role="presentation">
      <span style={{ width: `${pct}%` }} />
    </div>
  );
}

/* ─── Toast ─── */
type ToastPayload = { message: string; tone: 'info' | 'error' };
let toastEmitter: ((payload: ToastPayload) => void) | null = null;

export function toast(message: string, tone: 'info' | 'error' = 'info') {
  toastEmitter?.({ message, tone });
}

export function ToastHost() {
  const [current, setCurrent] = useState<ToastPayload | null>(null);
  const timer = useRef<number>(0);
  useEffect(() => {
    toastEmitter = (payload) => {
      setCurrent(payload);
      window.clearTimeout(timer.current);
      timer.current = window.setTimeout(() => setCurrent(null), 2800);
    };
    return () => {
      toastEmitter = null;
      window.clearTimeout(timer.current);
    };
  }, []);
  if (!current) {
    return null;
  }
  return (
    <div className={`toast show ${current.tone === 'error' ? 'error' : ''}`} role="status" aria-live="polite">
      {current.message}
    </div>
  );
}

/* ─── Dialog（Esc / 焦点陷阱 / 返回焦点） ─── */
const FOCUSABLE = 'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [href], [tabindex]:not([tabindex="-1"])';

export function Dialog({
  title,
  description,
  onClose,
  children,
  wide,
}: {
  title: string;
  description?: ReactNode;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  const backdropRef = useRef<HTMLDivElement | null>(null);
  const returnFocus = useRef<Element | null>(null);

  useEffect(() => {
    returnFocus.current = document.activeElement;
    const node = backdropRef.current;
    node?.querySelector<HTMLElement>(FOCUSABLE)?.focus();
    const onKeydown = (event: KeyboardEvent) => {
      if (!backdropRef.current) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = [...backdropRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)];
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeydown);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeydown);
      document.body.style.overflow = '';
      (returnFocus.current as HTMLElement | null)?.focus?.();
    };
  }, [onClose]);

  return (
    <div
      className="dialog-backdrop"
      ref={backdropRef}
      onClick={(event) => {
        if (event.target === backdropRef.current) onClose();
      }}
    >
      <section className={`dialog ${wide ? 'branch-dialog' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="dialog-head">
          <div>
            <h2>{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <button type="button" className="btn btn-ghost icon-btn" onClick={onClose} aria-label="关闭">
            <Icon name="close" />
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}

/* ─── 承诺式确认 / 输入弹窗（替代 window.confirm / prompt） ─── */
type ConfirmRequest = {
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  prompt?: { label: string; placeholder?: string; minLength?: number };
  resolve: (value: string | null | boolean) => void;
};
let confirmEmitter: ((request: ConfirmRequest) => void) | null = null;

/** 确认弹窗，resolve(true/false) */
export function confirmDialog(options: { title: string; message: ReactNode; confirmLabel?: string; danger?: boolean }) {
  return new Promise<boolean>((resolve) => {
    confirmEmitter?.({ ...options, resolve: (value) => resolve(Boolean(value)) });
  });
}

/** 输入弹窗，resolve(输入值 | null 取消) */
export function promptDialog(options: { title: string; message: ReactNode; label: string; placeholder?: string; minLength?: number }) {
  return new Promise<string | null>((resolve) => {
    confirmEmitter?.({ ...options, danger: false, resolve: (value) => resolve(typeof value === 'string' ? value : null) });
  });
}

export function ConfirmHost() {
  const [request, setRequest] = useState<ConfirmRequest | null>(null);
  const [value, setValue] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    confirmEmitter = (next) => {
      setValue('');
      setError('');
      setRequest(next);
    };
    return () => {
      confirmEmitter = null;
    };
  }, []);

  if (!request) {
    return null;
  }
  const isPrompt = Boolean(request.prompt);
  const close = (result: string | null | boolean) => {
    setRequest(null);
    request.resolve(result);
  };
  const submit = () => {
    if (isPrompt) {
      const min = request.prompt?.minLength ?? 1;
      if (value.trim().length < min) {
        setError(`至少输入 ${min} 个字符`);
        return;
      }
      close(value.trim());
      return;
    }
    close(true);
  };

  return (
    <Dialog
      title={request.title}
      description={request.message}
      onClose={() => close(isPrompt ? null : false)}
    >
      <form
        className="dialog-form"
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
      >
        {isPrompt && (
          <div className="field">
            <label>{request.prompt?.label}</label>
            <input
              className="input"
              value={value}
              placeholder={request.prompt?.placeholder}
              autoFocus
              onChange={(event) => {
                setValue(event.target.value);
                setError('');
              }}
            />
            {error && <p className="form-error">{error}</p>}
          </div>
        )}
        <div className="dialog-actions">
          <button type="button" className="btn btn-secondary" onClick={() => close(isPrompt ? null : false)}>
            取消
          </button>
          <button type="submit" className={`btn ${request.danger ? 'btn-danger' : 'btn-primary'}`}>
            {request.confirmLabel || '确认'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

/* ─── Switch ─── */
export function Switch({ checked, onChange, label }: { checked: boolean; onChange: (next: boolean) => void; label: string }) {
  return (
    <button
      type="button"
      className={`switch ${checked ? 'on' : ''}`}
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
    >
      <span />
    </button>
  );
}

/* ─── 分页 ─── */
export function Pagination({
  pageData,
  onPageChange,
}: {
  pageData: { current?: number; pages?: number; total?: number } | null;
  onPageChange: (page: number) => void;
}) {
  if (!pageData) {
    return null;
  }
  const current = pageData.current || 1;
  const pages = pageData.pages || 1;
  return (
    <div className="pagination">
      <span>
        共 {pageData.total ?? 0} 条，第 {current} / {pages || 1} 页
      </span>
      <div>
        <button type="button" className="btn btn-secondary" disabled={current <= 1} onClick={() => onPageChange(current - 1)}>
          上一页
        </button>
        <button type="button" className="btn btn-secondary" disabled={current >= pages} onClick={() => onPageChange(current + 1)}>
          下一页
        </button>
      </div>
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function LoadingBlock({ children }: { children: ReactNode }) {
  return <div className="loading-block">{children}</div>;
}
