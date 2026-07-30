import type { UserRole } from '../types';

export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ');
}

export function formatPrice(n: number | string | null | undefined): string {
  if (n === null || n === undefined || n === '') return '—';
  const num = typeof n === 'string' ? parseFloat(n) : n;
  if (Number.isNaN(num)) return '—';
  return `¥${num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function formatDateTime(s?: string | null): string {
  if (!s) return '—';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function relativeTime(s?: string | null): string {
  if (!s) return '';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  const diff = Date.now() - d.getTime();
  const abs = Math.abs(diff);
  const mins = Math.floor(abs / 60000);
  if (mins < 1) return '刚刚';
  if (mins < 60) return `${mins} 分钟前`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs} 小时前`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `${days} 天前`;
  return formatDateTime(s);
}

export const roleLabel: Record<UserRole, string> = {
  user: '用户',
  merchant: '商家',
  admin: '管理员',
};

export type ActivityStatus = 'draft' | 'pending' | 'preheating' | 'running' | 'ended';

export const activityStatusMeta: Record<string, { label: string; cls: string }> = {
  draft: { label: '草稿', cls: 'badge-draft' },
  pending: { label: '待审核', cls: 'badge-pending' },
  preheating: { label: '预热中', cls: 'badge-preheating' },
  running: { label: '进行中', cls: 'badge-running' },
  ended: { label: '已结束', cls: 'badge-ended' },
};

export const orderStatusMeta: Record<string, { label: string; cls: string }> = {
  UNPAID: { label: '待支付', cls: 'badge-pending' },
  PAID: { label: '已支付', cls: 'badge-success' },
  CANCELLED: { label: '已取消', cls: 'badge-ended' },
};

export function toLocalInputValue(date: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}`;
}

export function isoFromLocalInput(v: string): string {
  // datetime-local 值 "YYYY-MM-DDTHH:mm" → ISO 字符串（无时区后缀，与后端 LocalDateTime 对齐）
  return v;
}
