import { Navigate } from 'react-router-dom';
import { useAuth } from '../stores/authStore';
import type { ReactNode } from 'react';

interface AuthGuardProps {
  children: ReactNode;
  requireAuth: boolean;
}

export default function AuthGuard({ children, requireAuth }: AuthGuardProps) {
  const { isAuthenticated } = useAuth();

  if (requireAuth && !isAuthenticated) {
    return <Navigate to="/auth/login" replace />;
  }

  if (!requireAuth && isAuthenticated) {
    return <Navigate to="/activity" replace />;
  }

  return <>{children}</>;
}
