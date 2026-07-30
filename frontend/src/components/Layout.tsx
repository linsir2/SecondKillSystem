import { useState } from 'react';
import { NavLink, Outlet, useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useToast } from './Message';
import { useWebSocket } from '../hooks/useWebSocket';
import { countUnread } from '../api/message';
import { getStoredUserInfo } from '../utils/token';
import { roleLabel, cn } from '../utils/format';
import { usePoll } from './ui';
import { IconBell, IconBolt, IconLogout, IconUser, IconStore, IconBox, IconShield, IconPlus, IconList, IconChevronDown } from './icons';
import type { UserRole } from '../types';

interface NavItem { to: string; label: string; icon: typeof IconBolt; roles?: UserRole[]; }

const NAV: NavItem[] = [
  { to: '/activity', label: '活动广场', icon: IconList },
  { to: '/activity/create', label: '创建活动', icon: IconPlus, roles: ['merchant'] },
  { to: '/goods', label: '商品管理', icon: IconBox, roles: ['merchant'] },
  { to: '/admin', label: '管理后台', icon: IconShield, roles: ['admin'] },
];

export default function Layout() {
  const { user, logout, isAuthenticated } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  const stored = getStoredUserInfo();
  const role = user?.role ?? stored?.role as UserRole | undefined;

  // 未读消息轮询（已登录才查）
  const unread = usePoll(
    () => countUnread(),
    [],
    20000,
    isAuthenticated,
  );

  // WebSocket 广播：活动审核通过预告
  useWebSocket({
    enabled: isAuthenticated,
    onMessage: (data) => {
      try {
        const msg = JSON.parse(data);
        toast.info(msg.content ?? msg.message ?? '收到一条秒杀预告', '秒杀预告');
      } catch {
        toast.info(data, '秒杀预告');
      }
    },
  });

  const handleLogout = () => {
    logout();
    toast.success('已安全退出');
    navigate('/auth/login', { replace: true });
  };

  const visibleNav = NAV.filter((n) => !n.roles || (role && n.roles.includes(role)));
  const initial = (user?.userName ?? stored?.userName ?? '?').charAt(0).toUpperCase();

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-header-inner">
          <Link to="/activity" className="brand">
            <span className="brand-logo"><IconBolt width={16} height={16} /></span>
            秒<b>杀</b>系统
          </Link>

          <nav className="app-nav">
            {visibleNav.map((n) => {
              const Ic = n.icon;
              return (
                <NavLink
                  key={n.to}
                  to={n.to}
                  end={n.to === '/activity'}
                  className={({ isActive }) => cn('nav-link', isActive && 'active')}
                >
                  <Ic width={15} height={15} style={{ marginRight: 6, verticalAlign: '-2px' }} />
                  {n.label}
                </NavLink>
              );
            })}
          </nav>

          <div className="app-header-right">
            <Link to="/messages" className="icon-btn" aria-label="消息中心" title="消息中心">
              <IconBell />
              {!!unread && unread > 0 && <span className="badge-dot">{unread > 99 ? '99+' : unread}</span>}
            </Link>

            <div className="menu">
              <button className="user-chip" onClick={() => setMenuOpen((v) => !v)}>
                <span className="avatar">{initial}</span>
                <span className="nowrap" style={{ maxWidth: 110 }} title={user?.userName ?? stored?.userName}>
                  {user?.userName ?? stored?.userName}
                </span>
                {role && <span className="tag-soft" style={{ background: 'rgba(255,255,255,0.14)', color: '#fff' }}>{roleLabel[role]}</span>}
                <IconChevronDown width={14} height={14} style={{ opacity: 0.7 }} />
              </button>
              {menuOpen && (
                <div className="menu-panel" onClick={() => setMenuOpen(false)}>
                  <div className="menu-head">
                    <div className="name">{user?.userName ?? stored?.userName}</div>
                    <div className="sub">{role ? roleLabel[role] : ''}</div>
                  </div>
                  <Link to="/profile" className="menu-item"><IconUser /> 个人中心</Link>
                  <Link to="/messages" className="menu-item"><IconBell /> 消息中心</Link>
                  {role === 'merchant' && <Link to="/goods" className="menu-item"><IconStore /> 我的商品</Link>}
                  <div className="menu-sep" />
                  <button className="menu-item danger" onClick={handleLogout}><IconLogout /> 退出登录</button>
                </div>
              )}
            </div>
          </div>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      <div className="footer-note">SecondKillSystem · 高并发秒杀系统 Demo</div>
    </div>
  );
}
