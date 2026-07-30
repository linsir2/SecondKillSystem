import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listMessages, markAsRead } from '../api/message';
import { useToast } from '../components/Message';
import Button from '../components/Button';
import { Skeleton } from '../components/Loading';
import { EmptyState } from '../components/ui';
import { IconCheckCircle, IconBan, IconAlert, IconBolt, IconBell, IconRefresh } from '../components/icons';
import { relativeTime } from '../utils/format';
import type { MessageVO } from '../types';

function msgMeta(type: string): { Icon: typeof IconBell; color: string } {
  switch (type) {
    case 'approval_result':
      return { Icon: IconCheckCircle, color: 'var(--brand)' };
    case 'ban_info':
      return { Icon: IconBan, color: 'var(--danger)' };
    case 'sent_error':
      return { Icon: IconAlert, color: 'var(--warning)' };
    case 'welcome':
      return { Icon: IconBolt, color: 'var(--info)' };
    default:
      return { Icon: IconBell, color: 'var(--muted)' };
  }
}

export default function MessagePage() {
  const toast = useToast();
  const navigate = useNavigate();

  const [messages, setMessages] = useState<MessageVO[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listMessages(1, 50);
      setMessages(res);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '加载消息失败');
      setMessages([]);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleClick = async (msg: MessageVO) => {
    if (!msg.read) {
      try {
        await markAsRead(msg.messageId);
        setMessages((prev) =>
          prev.map((m) => (m.messageId === msg.messageId ? { ...m, read: true } : m)),
        );
      } catch (err) {
        toast.error(err instanceof Error ? err.message : '标记已读失败');
      }
    }
    if (msg.activityId) {
      navigate(`/activity/${msg.activityId}`);
    }
  };

  return (
    <div data-testid="message-page">
      <div className="page-head">
        <div>
          <h1 className="title mt-0">消息中心</h1>
          <p className="subtitle">审核结果、封禁通知与秒杀预告</p>
        </div>
        <Button variant="ghost" size="sm" onClick={load} loading={loading}>
          <IconRefresh width={15} height={15} /> 刷新
        </Button>
      </div>

      {loading ? (
        <div className="col" style={{ gap: 10 }}>
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="card card-pad row gap-16 center">
              <Skeleton h={26} w={26} r="50%" />
              <div className="grow col gap-4">
                <Skeleton h={16} w="70%" />
                <Skeleton h={12} w="28%" />
              </div>
            </div>
          ))}
        </div>
      ) : messages.length === 0 ? (
        <EmptyState icon={<IconBell />} title="暂无消息" desc="还没有收到任何通知" />
      ) : (
        <div className="col" style={{ gap: 10 }}>
          {messages.map((m) => {
            const { Icon, color } = msgMeta(m.type);
            return (
              <div
                key={m.messageId}
                className="card card-hover card-pad"
                role="button"
                tabIndex={0}
                style={{ cursor: 'pointer' }}
                onClick={() => handleClick(m)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    handleClick(m);
                  }
                }}
              >
                <div className="row gap-16 center">
                  <span style={{ color, flex: 'none', display: 'inline-flex' }}>
                    <Icon width={22} height={22} />
                  </span>
                  <div className="grow col gap-4">
                    <div className={m.read ? '' : 'strong'}>{m.content}</div>
                    <div className="muted-2" style={{ fontSize: 12 }}>
                      {relativeTime(m.createdAt)}
                    </div>
                  </div>
                  {!m.read && <span className="badge badge-brand">未读</span>}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
