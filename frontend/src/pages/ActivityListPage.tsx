import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listActivities, submitForReview } from '../api/activity';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../components/Message';
import Button from '../components/Button';
import { Skeleton } from '../components/Loading';
import { EmptyState, ActivityStatusBadge, Countdown, SectionTitle } from '../components/ui';
import { IconArrowRight, IconBolt, IconPlus, IconRefresh, IconStore } from '../components/icons';
import { formatDateTime, formatPrice } from '../utils/format';
import type { ActivityVO } from '../types';

export default function ActivityListPage() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [activities, setActivities] = useState<ActivityVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listActivities(1, 50);
      setActivities(res.records);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '加载活动失败');
      setActivities([]);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleSubmit = async (id: string) => {
    setBusyId(id);
    try {
      await submitForReview(id);
      toast.success('已提交审核');
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '提交失败');
    } finally {
      setBusyId(null);
    }
  };

  const isMerchant = user?.role === 'merchant';
  const isAdmin = user?.role === 'admin';

  return (
    <div data-testid="activity-page">
      <div className="page-head">
        <div>
          <h1 className="title mt-0">活动广场</h1>
          <p className="subtitle">
            {isMerchant ? '管理并提交你的秒杀活动' : isAdmin ? '全部活动一览' : '发现限时秒杀，抢到即赚到'}
          </p>
        </div>
        <div className="row gap-12">
          {isMerchant && (
            <Button variant="primary" onClick={() => navigate('/activity/create')}>
              <IconPlus width={16} height={16} />
              创建活动
            </Button>
          )}
          <Button variant="ghost" size="sm" onClick={load} loading={loading}>
            <IconRefresh width={15} height={15} /> 刷新
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="grid grid-cards">
          {[0, 1, 2].map((i) => (
            <div key={i} className="card card-pad col gap-16">
              <Skeleton h={22} w="70%" />
              <Skeleton h={14} w="50%" />
              <Skeleton h={14} w="60%" />
              <Skeleton h={36} w="40%" />
            </div>
          ))}
        </div>
      ) : activities.length === 0 ? (
        <EmptyState
          icon={<IconBolt width={48} height={48} />}
          title="暂无活动"
          desc={isMerchant ? '快去创建你的第一场秒杀活动吧' : '暂时没有可参与的秒杀活动，稍后再来看看'}
          action={
            isMerchant ? (
              <Button variant="primary" onClick={() => navigate('/activity/create')}>
                <IconPlus width={16} height={16} /> 创建活动
              </Button>
            ) : undefined
          }
        />
      ) : (
        <div className="grid grid-cards">
          {activities.map((a) => (
            <div key={a.activityId} className="card card-hover card-pad col">
              <div className="between wrap gap-8">
                <h3 className="mt-0">{a.activityName}</h3>
                <ActivityStatusBadge status={a.status} />
              </div>

              <div className="muted">
                {isAdmin || !isMerchant ? (
                  <span className="row center gap-6">
                    <IconStore width={14} height={14} /> 商家 #{a.merchantId}
                  </span>
                ) : null}
                <div>
                  {formatDateTime(a.startTime)} ~ {formatDateTime(a.endTime)}
                </div>
              </div>

              {a.description && <div className="muted-2 truncate">{a.description}</div>}

              {a.seckillGoodsList.length > 0 && (
                <div className="row wrap gap-8">
                  {a.seckillGoodsList.map((g) => (
                    <span key={g.seckillGoodsId} className="tag-soft">
                      {g.goodsName} · {formatPrice(g.seckillPrice)}
                    </span>
                  ))}
                </div>
              )}

              <div className="between wrap gap-12" style={{ marginTop: 'auto', paddingTop: 12 }}>
                <Countdown start={a.startTime} end={a.endTime} hot={a.status === 'running'} />

                {isMerchant ? (
                  <div className="row gap-8">
                    {a.status === 'draft' ? (
                      <Button
                        variant="primary"
                        size="sm"
                        loading={busyId === a.activityId}
                        onClick={() => handleSubmit(a.activityId)}
                      >
                        提交审核
                      </Button>
                    ) : (
                      <Button variant="ghost" size="sm" onClick={() => navigate(`/activity/${a.activityId}`)}>
                        查看 <IconArrowRight width={14} height={14} />
                      </Button>
                    )}
                  </div>
                ) : (
                  <Button variant="primary" size="sm" onClick={() => navigate(`/activity/${a.activityId}`)}>
                    查看详情 <IconArrowRight width={14} height={14} />
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {isMerchant && activities.length > 0 && (
        <div className="mt-24">
          <SectionTitle title="操作提示" desc="草稿状态的活动可提交审核；审核通过后进入预热/进行阶段" />
        </div>
      )}
    </div>
  );
}
