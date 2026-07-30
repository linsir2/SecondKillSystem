import { useEffect, useRef, useState } from 'react';

export type CountdownPhase = 'before' | 'running' | 'ended';

export interface CountdownState {
  phase: CountdownPhase;
  remaining: number; // ms until target (>=0)
  parts: { days: number; hours: number; minutes: number; seconds: number };
}

function split(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  return {
    days: Math.floor(s / 86400),
    hours: Math.floor((s % 86400) / 3600),
    minutes: Math.floor((s % 3600) / 60),
    seconds: s % 60,
  };
}

const empty = { days: 0, hours: 0, minutes: 0, seconds: 0 };

/**
 * 倒计时 Hook。传入开始/结束时间（ISO 字符串或时间戳），返回当前阶段与剩余时间。
 * before: 距开始；running: 距结束；ended: 已结束。
 */
export function useCountdown(start?: string, end?: string): CountdownState {
  const startMs = start ? Date.parse(start) : NaN;
  const endMs = end ? Date.parse(end) : NaN;
  const validStart = Number.isFinite(startMs);
  const validEnd = Number.isFinite(endMs);

  const compute = (): CountdownState => {
    const now = Date.now();
    if (validStart && now < startMs) {
      return { phase: 'before', remaining: startMs - now, parts: split(startMs - now) };
    }
    if (validEnd && now < endMs) {
      return { phase: 'running', remaining: endMs - now, parts: split(endMs - now) };
    }
    return { phase: 'ended', remaining: 0, parts: empty };
  };

  const [state, setState] = useState<CountdownState>(compute);
  const raf = useRef<number | null>(null);

  useEffect(() => {
    const tick = () => {
      setState(compute());
      raf.current = window.setTimeout(tick, 1000);
    };
    raf.current = window.setTimeout(tick, 1000);
    return () => {
      if (raf.current) window.clearTimeout(raf.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [start, end]);

  return state;
}

export default useCountdown;
