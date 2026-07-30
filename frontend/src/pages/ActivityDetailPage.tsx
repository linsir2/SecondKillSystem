import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getActivityDetail } from '../api/activity';
import { executeSeckill } from '../api/seckill';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../components/Message';
import Button from '../components/Button';
import { Field, Input } from '../components/Input';
import { Skeleton } from '../components/Loading';
import { ActivityStatusBadge, Countdown, EmptyState, SectionTitle } from '../components/ui';
import { IconAlert, IconArrowLeft, IconBolt, IconCart, IconStore } from '../components/icons';
import { formatDateTime, formatPrice } from '../utils/format';
import type { ActivityVO, SeckillGoodsVO } from '../types';

export default function ActivityDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const { user } = useAuth();
  const [activity, setActivity] = useState<ActivityVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [seckillId, setSeckillId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    const activityId = Number(id);
    if (Number.isNaN(activityId)) {
      toast.error('活动 ID 无效');
      navigate('/activity');
      return;
    }
    setLoading(true);
    getActivityDetail(activityId)
      .then((data) => {
        if (!alive) return;
        setActivity(data);
        if (data.seckillGoodsList.length > 0) {
          setSeckillId(data.seckillGoodsList[0].seckillGoodsId);
        }
      })
      .catch((err) => {
        if (!alive) return;
        toast.error(err instanceof Error ? err.message : '加载活动详情失败');
        navigate('/activity');
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const selected = activity?.seckillGoodsList.find((g) => g.seckillGoodsId === seckillId) ?? null;
  const isUser = user?.role === 'user';
  const canSeckill = activity?.status === 'running' && isUser;

  const validate = (): string | undefined => {
    if (!selected) return '请选择秒杀商品';
    if (quantity < 1) return '购买数量至少为 1';
    if (quantity > selected.limitNum) return `超过限购数量 ${selected.limitNum}`;
    if (quantity > selected.stock) return '库存不足';
    return undefined;
  };

  const handleSeckill = async () => {
    if (!activity || !selected) return;
    const error = validate();
    if (error) {
      toast.warning(error);
      return;
    }
    setBusy(true);
    try {
      const res = await executeSeckill({
        activityId: activity.activityId,
        seckillGoodsId: selected.seckillGoodsId,
        buyCount: quantity,
      });
      toast.success('抢购成功，正在生成订单');
      navigate(`/seckill/flow?token=${encodeURIComponent(res.orderToken)}`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '抢购失败');
    } finally {
      setBusy(false);
    }
  };

  if (loading || !activity) {
    return (
      <div>
        <Skeleton h={22} w="60%" />
        <div style={{ marginTop: 12 }}>
          <Skeleton h={14} w="40%" />
        </div>
        <div className="grid grid-2 mt-24">
          <div className="card card-pad col gap-16">
            <Skeleton h={160} />
            <Skeleton h={14} w="80%" />
            <Skeleton h={14} w="60%" />
          </div>
          <div className="card card-pad col gap-16">
            <Skeleton h={50} />
            <Skeleton h={50} />
            <Skeleton h={50} />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div>
      <Link to="/activity" className="link-row center gap-6 mb-16">
        <IconArrowLeft width={16} height={16} />
        返回活动广场
      </Link>

      <div className="page-head">
        <div>
          <div className="row center gap-12 wrap">
            <h1 className="title mt-0">{activity.activityName}</h1>
            <ActivityStatusBadge status={activity.status} />
          </div>
          <p className="subtitle">
            <IconStore width={14} height={14} /> 商家 #{activity.merchantId} · {formatDateTime(activity.startTime)} ~{' '}
            {formatDateTime(activity.endTime)}
          </p>
        </div>
        <Countdown start={activity.startTime} end={activity.endTime} hero hot={activity.status === 'running'} />
      </div>

      {activity.description && (
        <div className="card card-pad mb-16">
          <div className="muted">{activity.description}</div>
        </div>
      )}

      <SectionTitle title="秒杀商品" desc="选择商品参与抢购" />

      {activity.seckillGoodsList.length === 0 ? (
        <EmptyState icon={<IconBolt width={48} height={48} />} title="暂无秒杀商品" desc="该活动尚未绑定商品" />
      ) : (
        <div className="grid grid-2">
          <div className="card card-pad">
            <div className="col gap-12">
              {activity.seckillGoodsList.map((g) => (
                <SeckillGoodsRow
                  key={g.seckillGoodsId}
                  goods={g}
                  selected={seckillId === g.seckillGoodsId}
                  onSelect={() => {
                    setSeckillId(g.seckillGoodsId);
                    setQuantity(1);
                  }}
                />
              ))}
            </div>
          </div>

          <div className="card card-pad">
            {selected ? (
              <div className="col gap-16">
                <div className="between wrap gap-12">
                  <div>
                    <div className="strong" style={{ fontSize: 18 }}>
                      {selected.goodsName}
                    </div>
                    <div className="muted">限购 {selected.limitNum} 件 · 剩余 {selected.stock} 件</div>
                  </div>
                  <div className="price" style={{ fontSize: 28 }}>
                    {formatPrice(selected.seckillPrice)}
                  </div>
                </div>

                {canSeckill ? (
                  <>
                    <Field label="购买数量">
                      <Input
                        type="number"
                        min={1}
                        max={selected.limitNum}
                        value={quantity}
                        onChange={(e) => setQuantity(Math.max(1, Number(e.target.value) || 1))}
                      />
                    </Field>
                    <div className="alert alert-info">
                      <IconAlert className="a-ico" />
                      <div>秒杀成功后请在 1 分钟内完成支付，超时订单将自动取消。</div>
                    </div>
                    <Button variant="primary" size="lg" block loading={busy} onClick={handleSeckill}>
                      <IconCart width={18} height={18} /> 立即抢购
                    </Button>
                  </>
                ) : (
                  <div className="alert alert-warning">
                    <IconAlert className="a-ico" />
                    <div>
                      {activity.status === 'ended'
                        ? '活动已结束，无法参与秒杀。'
                        : activity.status === 'running'
                          ? '仅用户角色可参与秒杀。'
                          : '活动尚未开始，请耐心等待。'}
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <EmptyState icon={<IconBolt />} title="请选择商品" desc="点击左侧商品查看详情并参与秒杀" />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function SeckillGoodsRow({
  goods,
  selected,
  onSelect,
}: {
  goods: SeckillGoodsVO;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      className={`sg-row ${selected ? 'active' : ''}`}
      onClick={onSelect}
      style={{
        width: '100%',
        textAlign: 'left',
        padding: '14px 16px',
        borderRadius: 'var(--r-sm)',
        border: `1px solid ${selected ? 'var(--brand)' : 'var(--border)'}`,
        background: selected ? 'var(--brand-soft)' : 'var(--surface-2)',
        cursor: 'pointer',
      }}
    >
      <div className="between wrap gap-8">
        <div className="strong">{goods.goodsName}</div>
        <div className="price">{formatPrice(goods.seckillPrice)}</div>
      </div>
      <div className="between wrap gap-8 muted" style={{ fontSize: 13, marginTop: 4 }}>
        <span>限购 {goods.limitNum} 件</span>
        <span>剩余 {goods.stock} 件</span>
      </div>
    </button>
  );
}
