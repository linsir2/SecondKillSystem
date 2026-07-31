import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AuthGuard from '../components/AuthGuard';
import { AuthProvider } from '../stores/authStore';
import { setTokens, saveUserInfo } from '../utils/token';
import type { ReactNode } from 'react';

/* ─── 辅助 ─── */

function renderWithRouter(
  ui: ReactNode,
  { initialEntries = ['/'] }: { initialEntries?: string[] } = {},
) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter>
    </AuthProvider>,
  );
}

function guardRoutes() {
  return (
    <Routes>
      <Route
        path="/auth/login"
        element={
          <AuthGuard requireAuth={false}>
            <div data-testid="login-page" />
          </AuthGuard>
        }
      />
      <Route
        path="/auth/register"
        element={
          <AuthGuard requireAuth={false}>
            <div data-testid="register-page" />
          </AuthGuard>
        }
      />
      <Route
        path="/activity"
        element={
          <AuthGuard requireAuth>
            <div data-testid="activity-page" />
          </AuthGuard>
        }
      />
      <Route path="*" element={<div data-testid="unknown-page" />} />
    </Routes>
  );
}

/* ─── 测试 ─── */

describe('components / AuthGuard', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('未登录访问 /auth/login 渲染登录页', () => {
    renderWithRouter(guardRoutes(), { initialEntries: ['/auth/login'] });
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('未登录访问 /auth/register 渲染注册页', () => {
    renderWithRouter(guardRoutes(), { initialEntries: ['/auth/register'] });
    expect(screen.getByTestId('register-page')).toBeInTheDocument();
  });

  it('未登录访问 /activity 重定向到 /auth/login', async () => {
    renderWithRouter(guardRoutes(), { initialEntries: ['/activity'] });
    expect(await screen.findByTestId('login-page')).toBeInTheDocument();
  });

  it('已登录访问 /auth/login 重定向到 /activity', async () => {
    setTokens('at', 'rt');
    saveUserInfo({ userId: '1', userName: 'test', role: 'user' });
    renderWithRouter(guardRoutes(), { initialEntries: ['/auth/login'] });
    expect(await screen.findByTestId('activity-page')).toBeInTheDocument();
  });

  it('已登录访问 /activity 渲染活动页', () => {
    setTokens('at', 'rt');
    saveUserInfo({ userId: '1', userName: 'test', role: 'user' });
    renderWithRouter(guardRoutes(), { initialEntries: ['/activity'] });
    expect(screen.getByTestId('activity-page')).toBeInTheDocument();
  });

  // 未知路径重定向由 router/index.tsx 的 catch-all 路由处理，AuthGuard 不负责
});
