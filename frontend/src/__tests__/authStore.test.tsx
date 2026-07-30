import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider, useAuth } from '../stores/authStore';
import { setTokens, saveUserInfo, getAccessToken, getRefreshToken, getStoredUserInfo } from '../utils/token';
import type { ReactNode } from 'react';

/* ─── 测试辅助 ─── */

function TestConsumer() {
  const auth = useAuth();
  return (
    <div>
      <span data-testid="isAuth">{String(auth.isAuthenticated)}</span>
      <span data-testid="userId">{auth.user?.userId ?? 'null'}</span>
      <span data-testid="userName">{auth.user?.userName ?? 'null'}</span>
      <span data-testid="role">{auth.user?.role ?? 'null'}</span>
      <span data-testid="email">{auth.user?.email ?? 'null'}</span>
      <span data-testid="banStatus">{auth.user?.banStatus ?? 'null'}</span>
      <button data-testid="btn-login" onClick={() => auth.login({ email: 'a@b.com', password: '12345678' }).catch(() => {})}>
        login
      </button>
      <button data-testid="btn-register" onClick={() => auth.register({ userName: 't', email: 'a@b.com', password: '12345678' }).catch(() => {})}>
        register
      </button>
      <button data-testid="btn-getMe" onClick={() => auth.getMe().catch(() => {})}>
        getMe
      </button>
      <button data-testid="btn-logout" onClick={() => auth.logout()}>
        logout
      </button>
    </div>
  );
}

function renderWithProvider(ui: ReactNode) {
  return render(<AuthProvider>{ui}</AuthProvider>);
}

const mockLoginVO = {
  accessToken: 'at_xxx',
  refreshToken: 'rt_xxx',
  userId: 1,
  userName: 'tester',
  role: 'user' as const,
};

const mockUserInfoVO = {
  userId: 1,
  userName: 'tester',
  email: 'tester@test.com',
  role: 'user' as const,
  banStatus: 'NORMAL' as const,
};

function mockFetchOk(data: unknown) {
  globalThis.fetch = vi.fn().mockResolvedValue({
    status: 200,
    json: () => Promise.resolve({ code: 200, message: 'success', data }),
  });
}

/* ─── 测试 ─── */

describe('stores / authStore', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  /* ─── 初始状态 ─── */

  it('初始状态 isAuthenticated 为 false', () => {
    renderWithProvider(<TestConsumer />);
    expect(screen.getByTestId('isAuth').textContent).toBe('false');
  });

  it('localStorage 有 token + userInfo 时恢复登录态', () => {
    setTokens('some-token', 'some-refresh');
    saveUserInfo({ userId: 5, userName: 'restored', role: 'merchant' });
    renderWithProvider(<TestConsumer />);
    expect(screen.getByTestId('isAuth').textContent).toBe('true');
    expect(screen.getByTestId('userId').textContent).toBe('5');
    expect(screen.getByTestId('userName').textContent).toBe('restored');
    expect(screen.getByTestId('role').textContent).toBe('merchant');
  });

  it('localStorage 只有 token 无 userInfo 时不恢复登录态', () => {
    setTokens('some-token', 'some-refresh');
    renderWithProvider(<TestConsumer />);
    expect(screen.getByTestId('isAuth').textContent).toBe('false');
  });

  /* ─── login ─── */

  it('login 成功后 isAuthenticated=true, user 信息正确', async () => {
    mockFetchOk(mockLoginVO);
    renderWithProvider(<TestConsumer />);

    await userEvent.click(screen.getByTestId('btn-login'));

    await waitFor(() => {
      expect(screen.getByTestId('isAuth').textContent).toBe('true');
      expect(screen.getByTestId('userId').textContent).toBe('1');
      expect(screen.getByTestId('userName').textContent).toBe('tester');
      expect(screen.getByTestId('role').textContent).toBe('user');
    });
  });

  it('register 成功后自动登录', async () => {
    mockFetchOk(mockLoginVO);
    renderWithProvider(<TestConsumer />);

    await userEvent.click(screen.getByTestId('btn-register'));

    await waitFor(() => {
      expect(screen.getByTestId('isAuth').textContent).toBe('true');
      expect(screen.getByTestId('userName').textContent).toBe('tester');
    });
  });

  it('login 失败时状态不改变', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 400,
      json: () => Promise.resolve({ code: 400, message: '邮箱或密码错误' }),
    });
    renderWithProvider(<TestConsumer />);

    await userEvent.click(screen.getByTestId('btn-login'));

    await waitFor(() => {
      expect(screen.getByTestId('isAuth').textContent).toBe('false');
      expect(screen.getByTestId('userId').textContent).toBe('null');
    });
  });

  /* ─── getMe ─── */

  it('getMe() 成功后 user 含 email、banStatus', async () => {
    mockFetchOk(mockLoginVO);
    renderWithProvider(<TestConsumer />);

    // 先登录
    await userEvent.click(screen.getByTestId('btn-login'));
    await waitFor(() => expect(screen.getByTestId('isAuth').textContent).toBe('true'));

    // getMe 返回完整信息
    mockFetchOk(mockUserInfoVO);
    await userEvent.click(screen.getByTestId('btn-getMe'));

    await waitFor(() => {
      expect(screen.getByTestId('email').textContent).toBe('tester@test.com');
      expect(screen.getByTestId('banStatus').textContent).toBe('NORMAL');
    });
  });

  it('getMe() 失败(401) 不崩溃，state 不脏', async () => {
    mockFetchOk(mockLoginVO);
    renderWithProvider(<TestConsumer />);

    await userEvent.click(screen.getByTestId('btn-login'));
    await waitFor(() => expect(screen.getByTestId('isAuth').textContent).toBe('true'));

    // getMe 返回 401
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 401,
      json: () => Promise.resolve({ code: 401, message: '未登录' }),
    });
    await userEvent.click(screen.getByTestId('btn-getMe'));

    await waitFor(() => {
      // state 不被污染
      expect(screen.getByTestId('isAuth').textContent).toBe('true');
      expect(screen.getByTestId('userId').textContent).toBe('1');
    });
  });

  it('getMe() 返回 BANNED 状态反映在 store 中', async () => {
    mockFetchOk(mockLoginVO);
    renderWithProvider(<TestConsumer />);

    await userEvent.click(screen.getByTestId('btn-login'));
    await waitFor(() => expect(screen.getByTestId('isAuth').textContent).toBe('true'));

    mockFetchOk({ ...mockUserInfoVO, banStatus: 'BANNED' });
    await userEvent.click(screen.getByTestId('btn-getMe'));

    await waitFor(() => {
      expect(screen.getByTestId('banStatus').textContent).toBe('BANNED');
      expect(screen.getByTestId('isAuth').textContent).toBe('true'); // ban 不自动登出
    });
  });

  /* ─── logout ─── */

  it('logout 后 user=null, token 清除, localStorage 无残留', async () => {
    mockFetchOk(mockLoginVO);
    renderWithProvider(<TestConsumer />);

    await userEvent.click(screen.getByTestId('btn-login'));
    await waitFor(() => expect(screen.getByTestId('isAuth').textContent).toBe('true'));

    await userEvent.click(screen.getByTestId('btn-logout'));

    expect(screen.getByTestId('isAuth').textContent).toBe('false');
    expect(screen.getByTestId('userId').textContent).toBe('null');
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
    expect(getStoredUserInfo()).toBeNull();
  });

  it('logout 时调用后端 API（fire-and-forget）', async () => {
    mockFetchOk(mockLoginVO);
    renderWithProvider(<TestConsumer />);

    await userEvent.click(screen.getByTestId('btn-login'));
    await waitFor(() => expect(screen.getByTestId('isAuth').textContent).toBe('true'));

    // logout 应该发出 POST /api/v1/auth/logout
    const mockLogoutFetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });
    globalThis.fetch = mockLogoutFetch;

    await userEvent.click(screen.getByTestId('btn-logout'));

    await waitFor(() => {
      expect(mockLogoutFetch).toHaveBeenCalledWith(
        '/api/v1/auth/logout',
        expect.objectContaining({ method: 'POST' }),
      );
    });
  });

  it('login 成功后立刻 logout，状态正确清除', async () => {
    mockFetchOk(mockLoginVO);
    renderWithProvider(<TestConsumer />);

    await userEvent.click(screen.getByTestId('btn-login'));
    await waitFor(() => expect(screen.getByTestId('isAuth').textContent).toBe('true'));

    await userEvent.click(screen.getByTestId('btn-logout'));

    expect(screen.getByTestId('isAuth').textContent).toBe('false');
    expect(screen.getByTestId('userId').textContent).toBe('null');
    expect(getAccessToken()).toBeNull();
  });
});
