import { createBrowserRouter, Navigate } from 'react-router-dom';
import AuthGuard from '../components/AuthGuard';
import Layout from '../components/Layout';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import ActivityListPage from '../pages/ActivityListPage';
import ActivityDetailPage from '../pages/ActivityDetailPage';
import CreateActivityPage from '../pages/CreateActivityPage';
import GoodsManagePage from '../pages/GoodsManagePage';
import AdminPage from '../pages/AdminPage';
import MessagePage from '../pages/MessagePage';
import ProfilePage from '../pages/ProfilePage';
import OrderPage from '../pages/OrderPage';

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
    element: <Layout />,
    children: [
      {
        path: '/activity',
        element: (
          <AuthGuard requireAuth>
            <ActivityListPage />
          </AuthGuard>
        ),
      },
      {
        path: '/activity/create',
        element: (
          <AuthGuard requireAuth roles={['merchant']}>
            <CreateActivityPage />
          </AuthGuard>
        ),
      },
      {
        path: '/activity/:id',
        element: (
          <AuthGuard requireAuth>
            <ActivityDetailPage />
          </AuthGuard>
        ),
      },
      {
        path: '/goods',
        element: (
          <AuthGuard requireAuth roles={['merchant']}>
            <GoodsManagePage />
          </AuthGuard>
        ),
      },
      {
        path: '/admin',
        element: (
          <AuthGuard requireAuth roles={['admin']}>
            <AdminPage />
          </AuthGuard>
        ),
      },
      {
        path: '/messages',
        element: (
          <AuthGuard requireAuth>
            <MessagePage />
          </AuthGuard>
        ),
      },
      {
        path: '/profile',
        element: (
          <AuthGuard requireAuth>
            <ProfilePage />
          </AuthGuard>
        ),
      },
      {
        path: '/seckill/flow',
        element: (
          <AuthGuard requireAuth>
            <OrderPage />
          </AuthGuard>
        ),
      },
    ],
  },
  { path: '/', element: <Navigate to="/activity" replace /> },
  { path: '*', element: <Navigate to="/activity" replace /> },
]);

export default router;
