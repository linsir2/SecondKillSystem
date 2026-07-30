import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { IconCheckCircle, IconXCircle, IconAlert, IconInfo, IconX } from './icons';

type ToastKind = 'success' | 'error' | 'warning' | 'info';

interface ToastItem {
  id: number;
  kind: ToastKind;
  title?: string;
  message: string;
}

interface ToastApi {
  success: (message: string, title?: string) => void;
  error: (message: string, title?: string) => void;
  warning: (message: string, title?: string) => void;
  info: (message: string, title?: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

let seq = 1;

const icons: Record<ToastKind, typeof IconCheckCircle> = {
  success: IconCheckCircle,
  error: IconXCircle,
  warning: IconAlert,
  info: IconInfo,
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);

  const remove = useCallback((id: number) => {
    setItems((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (kind: ToastKind, message: string, title?: string) => {
      const id = seq++;
      setItems((prev) => [...prev, { id, kind, message, title }]);
      window.setTimeout(() => remove(id), 4200);
    },
    [remove],
  );

  const api: ToastApi = {
    success: (m, t) => push('success', m, t),
    error: (m, t) => push('error', m, t),
    warning: (m, t) => push('warning', m, t),
    info: (m, t) => push('info', m, t),
  };

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-wrap">
        {items.map((t) => {
          const Ic = icons[t.kind];
          return (
            <div key={t.id} className={`toast ${t.kind}`} role="status">
              <Ic className="t-icon" />
              <div className="t-body">
                {t.title && <div className="t-title">{t.title}</div>}
                <div>{t.message}</div>
              </div>
              <button className="icon-btn" style={{ width: 22, height: 22, border: 'none', background: 'transparent', color: 'var(--muted)' }} onClick={() => remove(t.id)} aria-label="关闭">
                <IconX width={14} height={14} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}

export default useToast;
