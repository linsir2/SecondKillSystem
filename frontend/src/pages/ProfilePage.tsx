import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../components/Message';
import Button from '../components/Button';
import { Skeleton } from '../components/Loading';
import { IconAlert, IconLogout } from '../components/icons';
import { cn, roleLabel } from '../utils/format';
import type { UserInfoVO } from '../types';

export default function ProfilePage() {
  const { getMe, logout } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const [info, setInfo] = useState<UserInfoVO | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    getMe()
      .then((data) => {
        if (alive) setInfo(data);
      })
      .catch((err) => {
        if (alive) toast.error(err instanceof Error ? err.message : '获取个人信息失败');
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleLogout = () => {
    logout();
    toast.success('已退出登录');
    navigate('/auth/login');
  };

  const banned = info?.banStatus === 'BANNED';

  return (
    <div data-testid="profile-page">
      <div className="page-head">
        <div>
          <h1 className="title mt-0">个人中心</h1>
          <p className="subtitle">查看账号信息与安全设置</p>
        </div>
      </div>

      {loading || !info ? (
        <div className="card card-pad col gap-16">
          <div className="row center gap-16">
            <Skeleton h={56} w={56} r="50%" />
            <div className="col gap-8">
              <Skeleton h={22} w={160} />
              <Skeleton h={16} w={120} />
            </div>
          </div>
          <div className="divider" />
          <Skeleton h={16} w="60%" />
          <Skeleton h={16} w="50%" />
          <Skeleton h={16} w="70%" />
        </div>
      ) : (
        <div className="card card-pad col gap-16">
          <div className="row center gap-16 wrap">
            <span className="avatar" style={{ width: 56, height: 56, fontSize: 24 }}>
              {info.userName.charAt(0).toUpperCase()}
            </span>
            <div className="col gap-8">
              <div className="row center gap-8 wrap">
                <h2 className="mt-0">{info.userName}</h2>
                <span className="badge badge-brand">{roleLabel[info.role]}</span>
                <span
                  className={cn(
                    'badge',
                    info.banStatus === 'NORMAL' ? 'badge-success' : 'badge-danger',
                  )}
                >
                  {info.banStatus === 'NORMAL' ? '正常' : '已封禁'}
                </span>
              </div>
              <div className="muted-2" style={{ fontSize: 13 }}>
                ID: {info.userId}
              </div>
            </div>
          </div>

          {banned && (
            <div className="alert alert-danger">
              <IconAlert className="a-ico" />
              <div>您的账号已被封禁，无法参与秒杀。请联系管理员。</div>
            </div>
          )}

          <dl className="kvs">
            <dt>用户 ID</dt>
            <dd>{info.userId}</dd>
            <dt>用户名</dt>
            <dd>{info.userName}</dd>
            <dt>邮箱</dt>
            <dd>{info.email || '—'}</dd>
            <dt>角色</dt>
            <dd>{roleLabel[info.role]}</dd>
            <dt>账号状态</dt>
            <dd>{info.banStatus === 'NORMAL' ? '正常' : '已封禁'}</dd>
            <dt>注册信息</dt>
            <dd>—</dd>
          </dl>

          <hr className="divider" />

          <div className="row gap-12 wrap">
            <Button variant="ghost" onClick={() => navigate('/activity')}>
              返回活动广场
            </Button>
            <Button variant="danger" onClick={handleLogout}>
              <IconLogout width={15} height={15} /> 退出登录
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
