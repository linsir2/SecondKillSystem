import { useAuth as useStoreAuth } from '../stores/authStore';

/**
 * 便捷封装：在 authStore 之上派生角色判断。
 */
export function useAuth() {
  const auth = useStoreAuth();
  const role = auth.user?.role;
  return {
    ...auth,
    isAdmin: role === 'admin',
    isMerchant: role === 'merchant',
    isUser: role === 'user',
  };
}

export default useAuth;
