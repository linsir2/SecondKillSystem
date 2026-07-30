import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import { Field, Input } from '../components/Input';
import { useToast } from '../components/Message';
import {
  IconArrowRight,
  IconBolt,
  IconCheckCircle,
  IconClock,
  IconEye,
  IconEyeOff,
  IconLock,
  IconMail,
  IconStore,
  IconUser,
} from '../components/icons';
import { useAuth } from '../hooks/useAuth';
import { cn } from '../utils/format';
import type { UserRole } from '../types';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function RegisterPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { register } = useAuth();

  const [userName, setUserName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<UserRole>('user');
  const [errors, setErrors] = useState<{ userName?: string; email?: string; password?: string }>({});
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const validateUserName = (value: string): string | undefined => {
    if (!value.trim()) return '请输入用户名';
    return undefined;
  };

  const validateEmail = (value: string): string | undefined => {
    const v = value.trim();
    if (!v) return '请输入邮箱';
    if (!EMAIL_RE.test(v)) return '邮箱格式不正确';
    return undefined;
  };

  const validatePassword = (value: string): string | undefined => {
    if (!value) return '请输入密码';
    if (value.length < 6) return '密码至少 6 位';
    return undefined;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const next = {
      userName: validateUserName(userName),
      email: validateEmail(email),
      password: validatePassword(password),
    };
    setErrors(next);
    if (next.userName || next.email || next.password) return;
    setLoading(true);
    try {
      await register({ userName: userName.trim(), email: email.trim(), password, role });
      toast.success('注册成功');
      navigate('/activity', { replace: true });
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '注册失败');
    } finally {
      setLoading(false);
    }
  };

  const roleOptions: { value: UserRole; title: string; desc: string; icon: typeof IconUser }[] = [
    { value: 'user', title: '用户', desc: '参与抢购', icon: IconUser },
    { value: 'merchant', title: '商家', desc: '发布秒杀活动', icon: IconStore },
  ];

  return (
    <div className="auth-shell" data-testid="register-page">
      <aside className="auth-aside">
        <div className="stack gap-24">
          <div className="brand">
            <span className="brand-logo">
              <IconBolt width={18} height={18} />
            </span>
            <span>秒杀系统</span>
          </div>
          <div className="stack gap-12">
            <h1>
              一键入驻 <b>开启秒杀</b>
            </h1>
            <p>注册账号即可参与限时抢购，或以商家身份发布秒杀活动，与千万用户一起拼手速。</p>
          </div>
          <div className="feat">
            <div className="feat-item">
              <span className="feat-ico">
                <IconBolt width={17} height={17} />
              </span>
              <div>
                <div style={{ color: '#fff', fontWeight: 600, fontSize: 15 }}>Redis + Lua 原子扣减</div>
                <div style={{ color: 'rgba(255,255,255,0.66)', fontSize: 13, marginTop: 2 }}>
                  单线程串行化扣减库存，从根上杜绝超卖。
                </div>
              </div>
            </div>
            <div className="feat-item">
              <span className="feat-ico">
                <IconCheckCircle width={17} height={17} />
              </span>
              <div>
                <div style={{ color: '#fff', fontWeight: 600, fontSize: 15 }}>RocketMQ 削峰排队</div>
                <div style={{ color: 'rgba(255,255,255,0.66)', fontSize: 13, marginTop: 2 }}>
                  异步消息削峰填谷，保护后端从容建单。
                </div>
              </div>
            </div>
            <div className="feat-item">
              <span className="feat-ico">
                <IconClock width={17} height={17} />
              </span>
              <div>
                <div style={{ color: '#fff', fontWeight: 600, fontSize: 15 }}>1 分钟超时自动关单</div>
                <div style={{ color: 'rgba(255,255,255,0.66)', fontSize: 13, marginTop: 2 }}>
                  延迟队列兜底回滚，释放占坑库存。
                </div>
              </div>
            </div>
          </div>
        </div>
        <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: 13 }}>高并发 · 高可用 · 最终一致</div>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <h2>创建账号</h2>
          <p className="muted" style={{ margin: '6px 0 22px' }}>
            注册后即可登录并参与秒杀
          </p>

          <form onSubmit={handleSubmit} noValidate>
            <Field label="用户名" required error={errors.userName}>
              <Input
                autoComplete="username"
                placeholder="请输入用户名"
                value={userName}
                invalid={!!errors.userName}
                onChange={(e) => {
                  setUserName(e.target.value);
                  if (errors.userName) setErrors((p) => ({ ...p, userName: undefined }));
                }}
                onBlur={() => setErrors((p) => ({ ...p, userName: validateUserName(userName) }))}
              />
            </Field>

            <Field label="邮箱" required error={errors.email}>
              <Input
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                lead={<IconMail width={17} height={17} />}
                value={email}
                invalid={!!errors.email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  if (errors.email) setErrors((p) => ({ ...p, email: undefined }));
                }}
                onBlur={() => setErrors((p) => ({ ...p, email: validateEmail(email) }))}
              />
            </Field>

            <Field label="密码" required error={errors.password}>
              <div style={{ position: 'relative' }}>
                <Input
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  placeholder="请输入密码（至少 6 位）"
                  lead={<IconLock width={17} height={17} />}
                  value={password}
                  invalid={!!errors.password}
                  style={{ paddingRight: 40 }}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (errors.password) setErrors((p) => ({ ...p, password: undefined }));
                  }}
                  onBlur={() => setErrors((p) => ({ ...p, password: validatePassword(password) }))}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? '隐藏密码' : '显示密码'}
                  style={{
                    position: 'absolute',
                    right: 6,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: 30,
                    height: 30,
                    border: 'none',
                    background: 'transparent',
                    color: 'var(--muted)',
                    cursor: 'pointer',
                    borderRadius: 6,
                  }}
                >
                  {showPassword ? <IconEyeOff width={17} height={17} /> : <IconEye width={17} height={17} />}
                </button>
              </div>
            </Field>

            <Field label="角色" required hint="管理员账号不可自助注册">
              <div className="role-pick">
                {roleOptions.map((opt) => {
                  const Ic = opt.icon;
                  return (
                    <button
                      key={opt.value}
                      type="button"
                      className={cn('role-opt', role === opt.value && 'active')}
                      onClick={() => setRole(opt.value)}
                    >
                      <Ic
                        width={20}
                        height={20}
                        style={{ color: 'var(--brand)', marginBottom: 6, display: 'block', marginInline: 'auto' }}
                      />
                      <div className="rt">{opt.title}</div>
                      <div className="rd">{opt.desc}</div>
                    </button>
                  );
                })}
              </div>
            </Field>

            <Button type="submit" variant="primary" size="lg" block loading={loading}>
              注册并登录
              <IconArrowRight width={18} height={18} style={{ marginLeft: 6 }} />
            </Button>
          </form>

          <div className="auth-foot">
            已有账号？
            <Link to="/auth/login" style={{ marginLeft: 4 }}>
              去登录
            </Link>
          </div>
        </div>
      </main>
    </div>
  );
}
