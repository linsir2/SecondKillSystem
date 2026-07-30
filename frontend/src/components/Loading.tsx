import type { ReactNode } from 'react';

export function Spinner({ size, light }: { size?: 'sm' | 'md' | 'lg'; light?: boolean }) {
  const cls = ['spinner', size === 'lg' ? 'lg' : size === 'sm' ? 'sm' : '', light ? 'light' : ''].filter(Boolean).join(' ');
  return <span className={cls} />;
}

export function LoadingCenter({ children }: { children?: ReactNode }) {
  return (
    <div className="loading-center">
      <Spinner size="lg" />
      {children && <span>{children}</span>}
    </div>
  );
}

export function LoadingOverlay({ children }: { children?: ReactNode }) {
  return (
    <div className="loading-overlay">
      <div className="loading-center">
        <Spinner size="lg" />
        {children && <span style={{ color: '#fff', fontWeight: 600 }}>{children}</span>}
      </div>
    </div>
  );
}

export function Skeleton({ h = 18, w = '100%', r }: { h?: number | string; w?: number | string; r?: number | string }) {
  return <div className="skeleton" style={{ height: h, width: w, borderRadius: r }} />;
}

export default Spinner;
