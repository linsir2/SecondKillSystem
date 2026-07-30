import {
  createContext,
  useContext,
  useState,
  useCallback,
  useEffect,
  type ReactNode,
} from 'react';
import {
  getAccessToken,
  getRefreshToken,
  setTokens,
  removeTokens,
  saveUserInfo,
  getStoredUserInfo,
  removeUserInfo,
} from '../utils/token';
import { login as loginApi, register as registerApi, logout as logoutApi } from '../api/auth';
import { getMe as getMeApi } from '../api/user';
import type { UserRole, BanStatus, LoginRequest, RegisterRequest, LoginVO, UserInfoVO } from '../types';

interface UserInfo {
  userId: number;
  userName: string;
  role: UserRole;
  email?: string;
  banStatus?: BanStatus;
}

interface AuthContextType {
  user: UserInfo | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  getMe: () => Promise<UserInfoVO>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);

  // 初始化：从 localStorage 恢复登录态
  useEffect(() => {
    const token = getAccessToken();
    const storedUser = getStoredUserInfo();
    if (token && storedUser) {
      setAccessToken(token);
      setUser({
        userId: storedUser.userId,
        userName: storedUser.userName,
        role: storedUser.role as UserRole,
      });
    }
  }, []);

  const handleLoginResult = useCallback((result: LoginVO) => {
    setTokens(result.accessToken, result.refreshToken);
    saveUserInfo({
      userId: result.userId,
      userName: result.userName,
      role: result.role,
    });
    setAccessToken(result.accessToken);
    setUser({
      userId: result.userId,
      userName: result.userName,
      role: result.role,
    });
  }, []);

  const login = useCallback(
    async (data: LoginRequest) => {
      const result = await loginApi(data);
      handleLoginResult(result);
    },
    [handleLoginResult],
  );

  const register = useCallback(
    async (data: RegisterRequest) => {
      const result = await registerApi(data);
      handleLoginResult(result);
    },
    [handleLoginResult],
  );

  const getMe = useCallback(async () => {
    const info = await getMeApi();
    setUser((prev) => ({
      ...prev!,
      userId: info.userId,
      userName: info.userName,
      role: info.role,
      email: info.email,
      banStatus: info.banStatus,
    }));
    return info;
  }, []);

  const logout = useCallback(() => {
    // Fire-and-forget: 通知后端失效 token，不等待
    const rt = getRefreshToken();
    if (rt) {
      logoutApi(rt).catch(() => {});
    }
    removeTokens();
    removeUserInfo();
    setAccessToken(null);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated: !!user && !!accessToken,
        login,
        register,
        logout,
        getMe,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
