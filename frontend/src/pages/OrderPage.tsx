import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getOrderStatus, cancelOrder } from '../api/order';
import { pay } from '../api/payment';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../components/Message';
import Button from '../components/Button';
import { LoadingCenter } from '../components/Loading';
import { EmptyState, OrderStatusBadge } from '../components/ui';
import { IconAlert, IconArrowLeft, IconCheckCircle, IconClock, IconWallet, IconXCircle } from '../components/icons';
import type { OrderStatusVO } from '../types';

const POLL_INTERVAL = 1500;
const MAX_POLL = 40;

export default function OrderPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { user } = useAuth();
  const token = params.get('token');

  const [status, setStatus] = useState<OrderStatusVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [paying, setPaying] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [pollCount, setPollCount] = useState(0);

  useEffect(() => {
    if (!token) {
      toast.error('缺少订单令牌');
      navigate('/activity');
      return;
    }

    let alive = true;
    let timer: number | null = null;
    let count = 0;

    const poll = async () => {
      if (!alive) return;
      try {
        const res = await getOrderStatus(token);
        if (!alive) return;
        setStatus(res);
        setPollCount(++count);
        if (res.status === 'UNPAID' || res.status === 'PAID' || res.status === 'CANCELLED') {
          setLoading(false);
          return;
        }
        if (count >= MAX_POLL) {
          setLoading(false);
          toast.warning('订单处理超时，请稍后到消息中心查看结果');
          return;
        }
        timer = window.setTimeout(poll, POLL_INTERVAL);
      } catch (err) {
        if (!alive) return;
        const msg = err instanceof Error ? err.message : '查询订单状态失败';
        setError(msg);
        setLoading(false);
      }
    };

    poll();

    return () => {
      alive = false;
      if (timer) window.clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const handlePay = async () => {
    if (!status?.orderNo || !user) return;
    setPaying(true);
    try {
      const res = await pay(status.orderNo, user.userId);
      if (res.success) {
        toast.success('支付成功');
        setStatus((prev) => (prev ? { ...prev, status: 'PAID' } : prev));
      } else {
        toast.error(res.message || '支付失败');
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '支付失败');
    } finally {
      setPaying(false);
    }
  };

  const handleCancel = async () => {
    if (!status?.orderNo) return;
    setCancelling(true);
    try {
      await cancelOrder(status.orderNo);
      toast.success('订单已取消');
      setStatus((prev) => (prev ? { ...prev, status: 'CANCELLED' } : prev));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '取消失败');
    } finally {
      setCancelling(false);
    }
  };

  if (!token) {
    return (
      <EmptyState
        icon={<IconAlert width={48} height={48} />}
        title="缺少订单信息"
        desc="请从活动详情页参与秒杀"
        action={
          <Button variant="primary" onClick={() => navigate('/activity')}>
            去活动广场
          </Button>
        }
      />
    );
  }

  if (loading && (!status || status.status === null)) {
    return (
      <div className="card card-pad" style={{ textAlign: 'center', padding: 80 }}>
        <LoadingCenter>
          <div>正在排队生成订单…（{pollCount}）</div>
        </LoadingCenter>
        <div className="muted mt-16">RocketMQ 异步削峰建单中，请稍候</div>
      </div>
    );
  }

  if (error || !status) {
    return (
      <div className="card card-pad col gap-16" style={{ textAlign: 'center', padding: 64 }}>
        <IconXCircle width={48} height={48} style={{ color: 'var(--danger)' }} />
        <div className="strong">订单查询失败</div>
        <div className="muted">{error || '无法获取订单状态，请稍后到消息中心查看结果'}</div>
        <Button variant="primary" onClick={() => navigate('/messages')}>
          去消息中心
        </Button>
      </div>
    );
  }

  const current = status.status;

  return (
    <div>
      <Link to="/activity" className="link-row center gap-6 mb-16">
        <IconArrowLeft width={16} height={16} />
        返回活动广场
      </Link>

      <div className="page-head">
        <div>
          <h1 className="title mt-0">订单详情</h1>
          <p className="subtitle">查看秒杀订单状态并完成支付</p>
        </div>
        {current && <OrderStatusBadge status={current} />}
      </div>

      {current === null ? (
        <div className="card card-pad col gap-16" style={{ textAlign: 'center', padding: 64 }}>
          <IconClock width={48} height={48} style={{ color: 'var(--warning)' }} />
          <div className="strong">订单仍在处理中</div>
          <div className="muted">已轮询 {pollCount} 次，异步建单可能稍有延迟，请稍后到消息中心查看结果</div>
          <Button variant="primary" onClick={() => navigate('/messages')}>
            去消息中心
          </Button>
        </div>
      ) : current === 'UNPAID' ? (
        <div className="card card-pad col gap-24" style={{ maxWidth: 480, margin: '0 auto' }}>
          <div className="col gap-8" style={{ textAlign: 'center' }}>
            <div
              className="avatar"
              style={{ width: 64, height: 64, margin: '0 auto', background: 'var(--warning-soft)', color: 'var(--warning)' }}
            >
              <IconClock width={28} height={28} />
            </div>
            <h2 className="mt-0">订单待支付</h2>
            <div className="muted">订单号：{status.orderNo}</div>
            <div className="muted">请在 1 分钟内完成支付，超时将自动取消</div>
          </div>

          <div className="alert alert-info">
            <IconAlert className="a-ico" />
            <div>此为演示支付，点击「立即支付」即可模拟支付成功。</div>
          </div>

          <div className="row gap-12">
            <Button variant="ghost" loading={cancelling} onClick={handleCancel}>
              取消订单
            </Button>
            <Button variant="primary" size="lg" block loading={paying} onClick={handlePay}>
              <IconWallet width={18} height={18} /> 立即支付
            </Button>
          </div>
        </div>
      ) : current === 'PAID' ? (
        <div className="card card-pad col gap-24" style={{ textAlign: 'center', maxWidth: 480, margin: '0 auto' }}>
          <div
            className="avatar"
            style={{ width: 72, height: 72, margin: '0 auto', background: 'var(--success-soft)', color: 'var(--success)' }}
          >
            <IconCheckCircle width={32} height={32} />
          </div>
          <div>
            <h2 className="mt-0">支付成功</h2>
            <div className="muted">订单号：{status.orderNo}</div>
          </div>
          <Button variant="primary" onClick={() => navigate('/activity')}>
            继续逛逛
          </Button>
        </div>
      ) : (
        <div className="card card-pad col gap-24" style={{ textAlign: 'center', maxWidth: 480, margin: '0 auto' }}>
          <div
            className="avatar"
            style={{ width: 72, height: 72, margin: '0 auto', background: 'var(--surface-3)', color: 'var(--muted)' }}
          >
            <IconXCircle width={32} height={32} />
          </div>
          <div>
            <h2 className="mt-0">订单已取消</h2>
            <div className="muted">订单号：{status.orderNo}</div>
          </div>
          <Button variant="primary" onClick={() => navigate('/activity')}>
            返回活动广场
          </Button>
        </div>
      )}
    </div>
  );
}
