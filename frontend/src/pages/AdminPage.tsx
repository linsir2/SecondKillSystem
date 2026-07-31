import { useCallback, useEffect, useState } from 'react';
import { listActivities, approveActivity, rejectActivity } from '../api/activity';
import { banUser, unbanUser } from '../api/admin';
import { useToast } from '../components/Message';
import Button from '../components/Button';
import { Field, Input, Textarea } from '../components/Input';
import { Skeleton } from '../components/Loading';
import { Modal, SectionTitle, EmptyState, ActivityStatusBadge } from '../components/ui';
import { IconShield, IconCheck, IconX, IconCheckCircle, IconRefresh, IconBan, IconInfo } from '../components/icons';
import { formatDateTime } from '../utils/format';
import type { ActivityVO } from '../types';

const RECENT_STATUSES = ['preheating', 'running', 'ended'];

export default function AdminPage() {
  const toast = useToast();

  const [activities, setActivities] = useState<ActivityVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [rejectTarget, setRejectTarget] = useState<ActivityVO | null>(null);
  const [reason, setReason] = useState('');

  const [userIdInput, setUserIdInput] = useState('');
  const [userBusy, setUserBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listActivities(1, 50);
      setActivities(res.records);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '加载活动列表失败');
      setActivities([]);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const pending = activities.filter((a) => a.status === 'pending');
  const recent = activities.filter((a) => RECENT_STATUSES.includes(a.status)).slice(0, 10);

  const handleApprove = async (id: string) => {
    setBusyId(id);
    try {
      await approveActivity(id);
      toast.success('已通过');
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '审核操作失败');
    } finally {
      setBusyId(null);
    }
  };

  const openReject = (a: ActivityVO) => {
    setRejectTarget(a);
    setReason('');
  };

  const closeReject = () => {
    setRejectTarget(null);
    setReason('');
  };

  const handleReject = async () => {
    if (!rejectTarget) return;
    if (!reason.trim()) {
      toast.warning('请填写驳回理由');
      return;
    }
    const id = rejectTarget.activityId;
    setBusyId(id);
    try {
      await rejectActivity(id, reason.trim());
      toast.success('已驳回');
      closeReject();
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '驳回操作失败');
    } finally {
      setBusyId(null);
    }
  };

  const operateUser = async (action: 'ban' | 'unban') => {
    if (!userIdInput.trim()) {
      toast.warning('请输入用户 ID');
      return;
    }
    const id = userIdInput.trim();
    if (!/^\d+$/.test(id)) {
      toast.warning('用户 ID 必须为数字');
      return;
    }
    setUserBusy(true);
    try {
      if (action === 'ban') {
        await banUser(id);
        toast.success('已封禁该用户');
      } else {
        await unbanUser(id);
        toast.success('已解封该用户');
      }
      setUserIdInput('');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '操作失败');
    } finally {
      setUserBusy(false);
    }
  };

  return (
    <div data-testid="admin-page" className="col">
      <div className="page-head">
        <div className="row center gap-12">
          <span className="avatar" style={{ width: 40, height: 40 }}>
            <IconShield width={18} height={18} />
          </span>
          <div>
            <h1 className="title mt-0">管理后台</h1>
            <p className="subtitle">活动审核与用户封禁管理</p>
          </div>
        </div>
      </div>

      {/* ─── Section A: 活动审核 ─── */}
      <section className="mb-24">
        <SectionTitle
          title="活动审核"
          desc="审核商家提交的秒杀活动申请"
          right={
            <Button variant="ghost" size="sm" onClick={load} loading={loading}>
              <IconRefresh width={15} height={15} /> 刷新
            </Button>
          }
        />

        {loading ? (
          <div className="grid grid-cards">
            {[0, 1, 2].map((i) => (
              <div key={i} className="card card-pad col gap-16">
                <Skeleton h={20} w="60%" />
                <Skeleton h={14} w="85%" />
                <Skeleton h={14} w="70%" />
                <Skeleton h={32} w="45%" />
              </div>
            ))}
          </div>
        ) : pending.length === 0 ? (
          <EmptyState
            icon={<IconCheckCircle />}
            title="暂无待审核活动"
            desc="所有活动申请均已处理完毕"
          />
        ) : (
          <div className="grid grid-cards">
            {pending.map((a) => (
              <div key={a.activityId} className="card card-pad col">
                <div className="between wrap gap-8">
                  <h3 className="mt-0">{a.activityName}</h3>
                  <ActivityStatusBadge status={a.status} />
                </div>
                <div className="muted">
                  商家 #{a.merchantId} · {formatDateTime(a.startTime)} ~ {formatDateTime(a.endTime)}
                </div>
                {a.description && <div className="muted-2 truncate">{a.description}</div>}
                {a.seckillGoodsList.length > 0 && (
                  <div className="row wrap gap-8">
                    {a.seckillGoodsList.map((g) => (
                      <span key={g.seckillGoodsId} className="tag-soft">
                        {g.goodsName}
                      </span>
                    ))}
                  </div>
                )}
                <div className="row gap-8" style={{ marginTop: 'auto', paddingTop: 4 }}>
                  <Button
                    variant="success"
                    size="sm"
                    loading={busyId === a.activityId}
                    onClick={() => handleApprove(a.activityId)}
                  >
                    <IconCheck width={14} height={14} /> 通过
                  </Button>
                  <Button variant="danger" size="sm" onClick={() => openReject(a)}>
                    <IconX width={14} height={14} /> 驳回
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}

        {recent.length > 0 && (
          <div className="card mt-24">
            <table className="table">
              <thead>
                <tr>
                  <th>活动名称</th>
                  <th>商家</th>
                  <th>状态</th>
                  <th>开始时间</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((a) => (
                  <tr key={a.activityId}>
                    <td className="strong">{a.activityName}</td>
                    <td className="muted">#{a.merchantId}</td>
                    <td>
                      <ActivityStatusBadge status={a.status} />
                    </td>
                    <td className="muted">{formatDateTime(a.startTime)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* ─── Section B: 用户封禁管理 ─── */}
      <section>
        <SectionTitle title="用户封禁管理" desc="输入用户 ID 进行封禁/解封" />

        <div className="card card-pad">
          <Field label="用户 ID" hint="输入需要操作的账号 ID（数字）">
            <Input
              type="number"
              value={userIdInput}
              placeholder="例如：1001"
              onChange={(e) => setUserIdInput(e.target.value)}
            />
          </Field>
          <div className="row gap-12">
            <Button variant="danger" loading={userBusy} onClick={() => operateUser('ban')}>
              <IconBan width={15} height={15} /> 封禁
            </Button>
            <Button variant="ghost" loading={userBusy} onClick={() => operateUser('unban')}>
              解封
            </Button>
          </div>

          <div className="alert alert-info mt-16">
            <IconInfo className="a-ico" />
            <div>演示提示：可封禁的已知账号 ID — 用户 1001 / 商家 1002（管理员 1003 请勿封禁自己）</div>
          </div>
        </div>
      </section>

      <Modal
        open={!!rejectTarget}
        title="驳回活动"
        onClose={closeReject}
        footer={
          <div className="row gap-8">
            <Button variant="ghost" onClick={closeReject}>
              取消
            </Button>
            <Button
              variant="danger"
              loading={!!rejectTarget && busyId === rejectTarget.activityId}
              onClick={handleReject}
            >
              确认驳回
            </Button>
          </div>
        }
      >
        {rejectTarget && (
          <div className="col gap-12">
            <div className="muted">
              即将驳回活动「<span className="strong">{rejectTarget.activityName}</span>」，请填写驳回理由，商家将会收到通知。
            </div>
            <Field label="驳回理由" required>
              <Textarea
                rows={4}
                value={reason}
                placeholder="请说明驳回原因，例如：商品库存不足、价格异常等"
                onChange={(e) => setReason(e.target.value)}
              />
            </Field>
          </div>
        )}
      </Modal>
    </div>
  );
}
