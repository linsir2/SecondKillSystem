import { createBrowserRouter, Navigate } from 'react-router-dom';
import AuthGuard from '../components/AuthGuard';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import ActivityListPage from '../pages/ActivityListPage';

const router = createBrowserRouter([
  {
    path: '/auth/login',
    element: (
      <AuthGuard requireAuth={false}>
        <LoginPage />
      </AuthGuard>
    ),
  },
  {
    path: '/auth/register',
    element: (
      <AuthGuard requireAuth={false}>
        <RegisterPage />
      </AuthGuard>
    ),
  },
  {
    path: '/activity',
    element: (
      <AuthGuard requireAuth>
        <ActivityListPage />
      </AuthGuard>
    ),
  },
  {
    path: '/',
    element: <Navigate to="/activity" replace />,
  },
  {
    path: '*',
    element: <Navigate to="/auth/login" replace />,
  },
]);

export default router;
