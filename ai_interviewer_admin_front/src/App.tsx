import { useCallback, useEffect, useState } from 'react';
import { adminApi, clearSession, getToken, readProfile } from './api';
import type { AdminProfile, DashboardOverview } from './types';
import { AppShell, SCREEN_LABELS, type ScreenKey } from './components/AppShell';
import { ConfirmHost, ToastHost } from './components/ui';
import { LoginView } from './views/LoginView';
import { DashboardView } from './views/DashboardView';
import { OperationsView } from './views/OperationsView';
import { ObservabilityView } from './views/ObservabilityView';
import { AuditView } from './views/AuditView';
import { SettingsView } from './views/SettingsView';
import { UploadView } from './views/UploadView';
import { WorkspaceView } from './views/WorkspaceView';
import { HistoryView } from './views/HistoryView';
import { ReplayView } from './views/ReplayView';
import { ReportView } from './views/ReportView';

export default function App() {
  const [profile, setProfile] = useState<AdminProfile | null>(() => readProfile());
  const [screen, setScreen] = useState<ScreenKey>('dashboard');
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [serviceOnline, setServiceOnline] = useState(true);

  /* 面试上下文：工作台 / 回放 / 报告共享 */
  const [activeLineageId, setActiveLineageId] = useState<string | null>(null);
  const [activeBranchId, setActiveBranchId] = useState<string | null>(null);
  const [reportSessionId, setReportSessionId] = useState<string | null>(null);

  const isLoggedIn = Boolean(getToken());

  const loadOverview = useCallback(async () => {
    setOverviewLoading(true);
    try {
      setOverview(await adminApi.dashboard());
      setServiceOnline(true);
    } catch {
      setServiceOnline(false);
    } finally {
      setOverviewLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isLoggedIn && screen === 'dashboard') {
      void loadOverview();
    }
  }, [isLoggedIn, screen, loadOverview]);

  function handleLogin(nextProfile: AdminProfile) {
    setProfile(nextProfile);
    setScreen('dashboard');
    void loadOverview();
  }

  function logout() {
    clearSession();
    setProfile(null);
    setOverview(null);
    setScreen('dashboard');
    setActiveLineageId(null);
    setActiveBranchId(null);
    setReportSessionId(null);
  }

  function navigate(next: ScreenKey) {
    setScreen(next);
    window.scrollTo({ top: 0 });
  }

  function openInterview(lineageId: string, branchId: string) {
    setActiveLineageId(lineageId);
    setActiveBranchId(branchId);
    navigate('workspace');
  }

  function openReplay(lineageId: string, branchId: string) {
    setActiveLineageId(lineageId);
    setActiveBranchId(branchId);
    navigate('replay');
  }

  function openReport(sessionId: string) {
    setReportSessionId(sessionId);
    navigate('report');
  }

  if (!isLoggedIn || !profile) {
    return (
      <>
        <LoginView onLogin={handleLogin} />
        <ToastHost />
        <ConfirmHost />
      </>
    );
  }

  return (
    <>
      <AppShell
        screen={screen}
        screenLabel={SCREEN_LABELS[screen]}
        profileName={profile.nickname || profile.username || 'Admin'}
        serviceOnline={serviceOnline}
        onNavigate={navigate}
        onStartInterview={() => navigate('upload')}
        onLogout={logout}
      >
        {screen === 'dashboard' && (
          <DashboardView overview={overview} loadingOverview={overviewLoading} onNavigate={navigate} />
        )}
        {screen === 'upload' && (
          <UploadView onNavigate={navigate} onInterviewStarted={openInterview} />
        )}
        {screen === 'workspace' && (
          <WorkspaceView
            lineageId={activeLineageId}
            branchId={activeBranchId}
            onOpenReport={openReport}
            onNavigate={navigate}
          />
        )}
        {screen === 'history' && (
          <HistoryView onResume={openInterview} onReplay={openReplay} onOpenReport={openReport} onNavigate={navigate} />
        )}
        {screen === 'replay' && (
          <ReplayView lineageId={activeLineageId} branchId={activeBranchId} onBranchSwitch={openReplay} onNavigate={navigate} />
        )}
        {screen === 'report' && <ReportView sessionId={reportSessionId} onNavigate={navigate} />}
        {screen === 'operations' && <OperationsView />}
        {screen === 'observability' && <ObservabilityView />}
        {screen === 'settings' && <SettingsView />}
        {screen === 'audit' && <AuditView />}
      </AppShell>
      <ToastHost />
      <ConfirmHost />
    </>
  );
}
