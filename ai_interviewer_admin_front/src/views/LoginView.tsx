import { useState, type FormEvent } from 'react';
import { adminApi, saveSession } from '../api';
import type { AdminProfile } from '../types';

export function LoginView({ onLogin }: { onLogin: (profile: AdminProfile) => void }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (loading) return;
    setLoading(true);
    setError('');
    try {
      const login = await adminApi.login(username, password);
      saveSession(login);
      onLogin(login.admin);
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-shell">
      <section className="login-card">
        <span className="meta">AI INTERVIEWER · ADMIN</span>
        <h1>重新进入面试工作区</h1>
        <p>登录后继续管理候选人、职位、题库、面试记录与 AI 调用链，也可以直接发起一场真实面试。</p>
        <form className="login-form" onSubmit={submit} noValidate>
          <div className="field">
            <label htmlFor="loginUsername">账号</label>
            <input
              id="loginUsername"
              className="input"
              value={username}
              autoComplete="username"
              onChange={(event) => setUsername(event.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="loginPassword">密码</label>
            <input
              id="loginPassword"
              className="input"
              type="password"
              value={password}
              autoComplete="current-password"
              placeholder="输入管理员密码"
              aria-invalid={error ? 'true' : undefined}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
            {error && <p className="form-error">{error}</p>}
          </div>
          <button type="submit" className="btn btn-primary" aria-busy={loading ? 'true' : undefined} disabled={loading}>
            {loading ? '正在验证…' : '登录后台'}
          </button>
        </form>
      </section>
    </main>
  );
}
