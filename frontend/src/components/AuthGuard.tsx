import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../stores/authStore';
import type { ReactNode } from 'react';
import type { UserRole } from '../types';

interface AuthGuardProps {
  children: ReactNode;
  requireAuth: boolean;
  roles?: UserRole[];
}

/**
 * 路由守卫：
 * - requireAuth=false：已登录则跳转 /activity（登录/注册页用）
 * - requireAuth=true：未登录跳转 /auth/login；传入 roles 时校验角色，不匹配跳转 /activity
 */
export default function AuthGuard({ children, requireAuth, roles }: AuthGuardProps) {
  const { isAuthenticated, isLoading, user } = useAuth();
  const location = useLocation();

  // 等待从 localStorage 恢复登录态，避免直接访问角色路由时被误判为未登录
  if (isLoading) {
    return (
      <div className="app-shell flex center" style={{ minHeight: '100vh' }}>
        <div className="spinner" />
      </div>
    );
  }

  if (requireAuth && !isAuthenticated) {
    return <Navigate to="/auth/login" replace state={{ from: location.pathname }} />;
  }

  if (!requireAuth && isAuthenticated) {
    return <Navigate to="/activity" replace />;
  }

  if (requireAuth && roles && user && !roles.includes(user.role)) {
    return <Navigate to="/activity" replace />;
  }

  return <>{children}</>;
}
