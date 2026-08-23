import type { ReactNode } from 'react';
import { Icon } from './ui';

export type ScreenKey =
  | 'dashboard'
  | 'upload'
  | 'workspace'
  | 'history'
  | 'replay'
  | 'report'
  | 'operations'
  | 'observability'
  | 'settings'
  | 'audit';

const WORKSPACE_NAV: Array<{ key: ScreenKey; label: string; icon: string }> = [
  { key: 'dashboard', label: '运营总览', icon: 'dashboard' },
  { key: 'workspace', label: '面试工作台', icon: 'workspace' },
  { key: 'history', label: '面试记录', icon: 'history' },
  { key: 'report', label: '评估报告', icon: 'report' },
];

const GOVERNANCE_NAV: Array<{ key: ScreenKey; label: string; icon: string }> = [
  { key: 'operations', label: '用户与职位', icon: 'users' },
  { key: 'observability', label: 'AI 观测', icon: 'observability' },
  { key: 'settings', label: '设置与题库', icon: 'settings' },
  { key: 'audit', label: '审计日志', icon: 'audit' },
];

const BOTTOM_NAV: Array<{ key: ScreenKey; label: string; icon: string }> = [
  { key: 'dashboard', label: '首页', icon: 'dashboard' },
  { key: 'workspace', label: '面试', icon: 'workspace' },
  { key: 'history', label: '历史', icon: 'history' },
  { key: 'settings', label: '设置', icon: 'settings' },
];

export const SCREEN_LABELS: Record<ScreenKey, string> = {
  dashboard: '运营总览',
  upload: '上传简历',
  workspace: '面试工作台',
  history: '面试记录',
  replay: '面试回放',
  report: '评估报告',
  operations: '用户与职位',
  observability: 'AI 观测',
  settings: '设置与题库',
  audit: '审计日志',
};

/** 流程子页在导航中的归属（保持父级高亮）。 */
export function navHighlight(screen: ScreenKey): ScreenKey {
  if (screen === 'upload') {
    return 'dashboard';
  }
  if (screen === 'replay') {
    return 'history';
  }
  return screen;
}

function NavButton({
  active,
  label,
  icon,
  onClick,
}: {
  active: boolean;
  label: string;
  icon: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={active ? 'nav-item active' : 'nav-item'}
      aria-current={active ? 'page' : undefined}
      onClick={onClick}
    >
      <Icon name={icon} />
      <span>{label}</span>
    </button>
  );
}

export function AppShell({
  screen,
  screenLabel,
  profileName,
  serviceOnline,
  onNavigate,
  onStartInterview,
  onLogout,
  children,
}: {
  screen: ScreenKey;
  screenLabel: string;
  profileName: string;
  serviceOnline: boolean;
  onNavigate: (screen: ScreenKey) => void;
  onStartInterview: () => void;
  onLogout: () => void;
  children: ReactNode;
}) {
  const highlight = navHighlight(screen);

  return (
    <>
      <div className="app">
        <aside className="sidebar">
          <div className="brand">
            <span className="brand-mark">AI</span>
            <span>面试助手</span>
          </div>
          <p className="nav-label">工作区</p>
          <nav className="side-nav" aria-label="主导航">
            {WORKSPACE_NAV.map((item) => (
              <NavButton
                key={item.key}
                active={highlight === item.key}
                label={item.label}
                icon={item.icon}
                onClick={() => onNavigate(item.key)}
              />
            ))}
          </nav>
          <p className="nav-label">管理与治理</p>
          <nav className="side-nav" aria-label="管理导航">
            {GOVERNANCE_NAV.map((item) => (
              <NavButton
                key={item.key}
                active={highlight === item.key}
                label={item.label}
                icon={item.icon}
                onClick={() => onNavigate(item.key)}
              />
            ))}
          </nav>
          <div className="sidebar-foot">
            <button type="button" className="profile" onClick={onLogout} title="退出登录" aria-label="退出登录">
              <span className="avatar">{profileName.slice(0, 1).toUpperCase()}</span>
              <span className="profile-copy">
                <strong>{profileName}</strong>
                <span>管理员工作区</span>
              </span>
            </button>
          </div>
        </aside>

        <div className="shell">
          <header className="topbar">
            <div className="mobile-brand">
              <span className="brand-mark">AI</span>
              <span>面试助手</span>
            </div>
            <div className="crumb">
              面试助手&nbsp; / &nbsp;<strong>{screenLabel}</strong>
            </div>
            <div className="top-actions">
              <span className="live-status">
                <span className={serviceOnline ? 'live-dot' : 'live-dot down'} />
                {serviceOnline ? '服务正常' : '服务异常'}
              </span>
              <button
                type="button"
                className="btn btn-ghost icon-btn"
                aria-label="开始新面试"
                onClick={onStartInterview}
              >
                <Icon name="plus" />
              </button>
            </div>
          </header>

          <main className="content">
            <section className="screen">{children}</section>
          </main>
        </div>
      </div>

      <nav className="bottom-nav" aria-label="移动端主导航">
        {BOTTOM_NAV.map((item) => (
          <button
            key={item.key}
            type="button"
            className={highlight === item.key ? 'bottom-item active' : 'bottom-item'}
            onClick={() => onNavigate(item.key)}
          >
            <Icon name={item.icon} size={19} />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>
    </>
  );
}
