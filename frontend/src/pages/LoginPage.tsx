import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
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
} from '../components/icons';
import { useAuth } from '../hooks/useAuth';
import { isMockEnabled } from '../mock/server';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const demoAccounts = [
  { role: '用户', email: 'user@seckill.com', password: '12345678' },
  { role: '商家', email: 'merchant@seckill.com', password: '12345678' },
  { role: '管理员', email: 'admin@seckill.com', password: '12345678' },
];

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const { login } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const from = (location.state as { from?: string } | null)?.from ?? '/activity';

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
      email: validateEmail(email),
      password: validatePassword(password),
    };
    setErrors(next);
    if (next.email || next.password) return;
    setLoading(true);
    try {
      await login({ email: email.trim(), password });
      toast.success('登录成功');
      navigate(from, { replace: true });
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const fillDemo = (acc: { email: string; password: string }) => {
    setEmail(acc.email);
    setPassword(acc.password);
    setErrors({});
  };

  return (
    <div className="auth-shell" data-testid="login-page">
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
              极速秒杀 <b>抢到即赚到</b>
            </h1>
            <p>
              基于 Redis + Lua 原子扣减与 RocketMQ 削峰排队，在海量并发请求下依然稳如磐石，毫秒级响应、杜绝超卖。
            </p>
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
          <h2>欢迎回来</h2>
          <p className="muted" style={{ margin: '6px 0 22px' }}>
            登录后即可参与限时秒杀
          </p>

          <form onSubmit={handleSubmit} noValidate>
            <Field label="邮箱" required error={errors.email}>
              <Input
                type="email"
                autoComplete="email"
                autoFocus
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
                  autoComplete="current-password"
                  placeholder="请输入密码"
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

            <Button type="submit" variant="primary" size="lg" block loading={loading}>
              登录
              <IconArrowRight width={18} height={18} style={{ marginLeft: 6 }} />
            </Button>
          </form>

          {isMockEnabled() && (
            <div className="card card-pad" style={{ marginTop: 20 }}>
              <div className="between" style={{ marginBottom: 10 }}>
                <span className="strong" style={{ fontSize: 13 }}>
                  演示账号（点击填充）
                </span>
                <span className="badge badge-info no-dot">演示模式</span>
              </div>
              <div className="col" style={{ gap: 8 }}>
                {demoAccounts.map((acc) => (
                  <button
                    key={acc.email}
                    type="button"
                    onClick={() => fillDemo(acc)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '9px 12px',
                      border: '1px solid var(--border)',
                      borderRadius: 'var(--r-sm)',
                      background: 'var(--surface-2)',
                      cursor: 'pointer',
                      fontSize: 13,
                    }}
                  >
                    <span className="strong">{acc.role}</span>
                    <span className="muted">
                      {acc.email} · {acc.password}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="auth-foot">
            还没有账号？
            <Link to="/auth/register" style={{ marginLeft: 4 }}>
              立即注册
            </Link>
          </div>
        </div>
      </main>
    </div>
  );
}
