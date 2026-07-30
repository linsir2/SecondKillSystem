import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { useCountdown, type CountdownPhase } from '../hooks/useCountdown';
import { activityStatusMeta, orderStatusMeta, cn } from '../utils/format';
import { IconX } from './icons';

/* ---- Status badge for activity / order ---- */
export function ActivityStatusBadge({ status }: { status: string }) {
  const m = activityStatusMeta[status] ?? { label: status, cls: 'badge-draft' };
  return <span className={cn('badge', m.cls)}>{m.label}</span>;
}

export function OrderStatusBadge({ status }: { status: string }) {
  const m = orderStatusMeta[status] ?? { label: status, cls: 'badge-draft' };
  return <span className={cn('badge', m.cls)}>{m.label}</span>;
}

/* ---- Countdown view ---- */
const phaseLabel: Record<CountdownPhase, string> = {
  before: '距开始',
  running: '距结束',
  ended: '已结束',
};

export function Countdown({ start, end, hot, hero }: { start?: string; end?: string; hot?: boolean; hero?: boolean }) {
  const { phase, parts } = useCountdown(start, end);
  if (phase === 'ended') {
    return <span className={cn('badge', 'badge-ended', 'no-dot')}>已结束</span>;
  }
  const label = phaseLabel[phase];
  return (
    <span className={cn('countdown', hot && 'hot', hero && 'hero')}>
      <span className="countdown-label">{label}</span>
      {parts.days > 0 && (
        <>
          <span className="unit">{parts.days}</span>
          <span className="sep">天</span>
        </>
      )}
      <span className="unit">{String(parts.hours).padStart(2, '0')}</span>
      <span className="sep">:</span>
      <span className="unit">{String(parts.minutes).padStart(2, '0')}</span>
      <span className="sep">:</span>
      <span className="unit">{String(parts.seconds).padStart(2, '0')}</span>
    </span>
  );
}

/* ---- Empty state ---- */
export function EmptyState({ icon, title, desc, action }: { icon?: ReactNode; title: string; desc?: string; action?: ReactNode }) {
  return (
    <div className="empty">
      {icon ?? <div className="skeleton" style={{ width: 56, height: 56, borderRadius: '50%' }} />}
      <div className="et">{title}</div>
      {desc && <div>{desc}</div>}
      {action && <div className="mt-16">{action}</div>}
    </div>
  );
}

/* ---- Modal ---- */
export function Modal({ open, title, onClose, children, footer, width }: { open: boolean; title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; width?: number }) {
  useEffect(() => {
    if (!open) return;
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" style={width ? { maxWidth: width } : undefined} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="icon-btn" style={{ background: 'transparent', border: 'none', color: 'var(--muted)', width: 30, height: 30 }} onClick={onClose} aria-label="关闭">
            <IconX width={18} height={18} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

/* ---- Section title ---- */
export function SectionTitle({ title, desc, right }: { title: string; desc?: string; right?: ReactNode }) {
  return (
    <div className="between mb-16 wrap">
      <div>
        <h2>{title}</h2>
        {desc && <p className="muted mt-8">{desc}</p>}
      </div>
      {right}
    </div>
  );
}

/* ---- Hook: poll a value on an interval ---- */
export function usePoll<T>(fn: () => Promise<T>, deps: unknown[], intervalMs: number, enabled = true): T | null {
  const [val, setVal] = useState<T | null>(null);
  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    const run = () => fn().then((v) => { if (alive) setVal(v); }).catch(() => {});
    run();
    const id = window.setInterval(run, intervalMs);
    return () => { alive = false; window.clearInterval(id); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, intervalMs, enabled]);
  return val;
}
