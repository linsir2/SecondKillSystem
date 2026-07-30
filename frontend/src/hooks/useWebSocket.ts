import { useEffect, useRef, useState, useCallback } from 'react';
import { getAccessToken } from '../utils/token';

interface UseWebSocketOptions {
  onMessage?: (data: string) => void;
  enabled?: boolean;
}

/**
 * 连接后端 /ws/notification 广播通道（活动审核通过后的秒杀预告）。
 * 自动带 token 鉴权、断线重连。返回连接状态与主动关闭句柄。
 */
export function useWebSocket({ onMessage, enabled = true }: UseWebSocketOptions = {}) {
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const retryRef = useRef<number | null>(null);
  const cbRef = useRef(onMessage);
  cbRef.current = onMessage;

  const connect = useCallback(() => {
    if (typeof WebSocket === 'undefined') return;
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const token = getAccessToken() ?? '';
    const url = `${proto}//${window.location.host}/ws/notification?token=${encodeURIComponent(token)}`;
    let ws: WebSocket;
    try {
      ws = new WebSocket(url);
    } catch {
      return;
    }
    wsRef.current = ws;
    ws.onopen = () => setConnected(true);
    ws.onclose = () => {
      setConnected(false);
      if (retryRef.current) window.clearTimeout(retryRef.current);
      retryRef.current = window.setTimeout(connect, 5000);
    };
    ws.onerror = () => {
      try { ws.close(); } catch { /* noop */ }
    };
    ws.onmessage = (ev) => {
      cbRef.current?.(ev.data as string);
    };
  }, []);

  useEffect(() => {
    if (!enabled) return;
    connect();
    return () => {
      if (retryRef.current) window.clearTimeout(retryRef.current);
      try { wsRef.current?.close(); } catch { /* noop */ }
    };
  }, [connect, enabled]);

  return { connected };
}

export default useWebSocket;
