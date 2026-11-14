import { useCallback, useEffect, useRef, useState } from 'react';
import { QueueApiError, queueApi, type QueueScope, type SessionResponse } from './api/queueApi';
import { estimateWaitSeconds, pushSample, type PositionSample } from './waitEstimate';

/**
 * 대기 세션 상태 머신(WUI-001/002).
 *
 * 흐름: join(POST, 쿠키가 있으면 서버가 기존 세션 복구) → SSE(/events) 로 status 수신 → 종단 상태면 안내.
 * SSE 가 열려 있는 동안 서버가 heartbeat 를 대신 수행한다(D-API-002). SSE 가 끊기면 EventSource 가 재접속하며,
 * 영구 실패(onerror 후 CLOSED)면 폴링(GET + POST heartbeat)으로 내려간다.
 * 세션 ID 는 HttpOnly 쿠키에만 있으므로 이 훅은 세션 ID 를 모른다.
 */
export type Phase =
  | { kind: 'joining' }
  | { kind: 'waiting'; session: SessionResponse; estimateSeconds?: number; transport: 'sse' | 'polling' }
  | { kind: 'admitted'; session: SessionResponse }
  | { kind: 'ended'; reason: 'expired' | 'cancelled' | 'gone' | 'reentryDenied' }
  | { kind: 'error'; code: string; retryAfterSeconds?: number };

export const POLL_INTERVAL_MS = 5_000;

export function useWaitingSession(scope: QueueScope): { phase: Phase; leave: () => Promise<void>; rejoin: () => void } {
  const [phase, setPhase] = useState<Phase>({ kind: 'joining' });
  const [attempt, setAttempt] = useState(0);
  const samples = useRef<PositionSample[]>([]);
  const transport = useRef<'sse' | 'polling'>('sse');
  // 현재 열린 SSE 연결. 대기가 끝나면(입장·만료·취소) 서버도 닫지만, EventSource 자동 재접속을 막기 위해 여기서도 닫는다.
  const stream = useRef<EventSource | undefined>(undefined);

  // 서버 응답 하나를 화면 상태로 바꾼다. 종단 상태 판정은 서버 status 가 기준이다.
  const apply = useCallback((session: SessionResponse) => {
    if (session.status !== 'WAITING' || session.reentryDenied) {
      stream.current?.close();
      stream.current = undefined;
    }
    if (session.reentryDenied) return setPhase({ kind: 'ended', reason: 'reentryDenied' });
    switch (session.status) {
      case 'ADMITTED':
        return setPhase({ kind: 'admitted', session });
      case 'EXPIRED':
        return setPhase({ kind: 'ended', reason: 'expired' });
      case 'CANCELLED':
        return setPhase({ kind: 'ended', reason: 'cancelled' });
      default: {
        if (session.position !== undefined) {
          samples.current = pushSample(samples.current, { at: Date.now(), position: session.position });
        }
        return setPhase({ kind: 'waiting', session, estimateSeconds: estimateWaitSeconds(samples.current), transport: transport.current });
      }
    }
  }, []);

  const fail = useCallback((e: unknown) => {
    if (e instanceof QueueApiError) {
      // 세션이 사라졌으면(보존 기간 경과 등) 오류가 아니라 "만료" 안내다.
      if (e.code === 'QUE-SESSION-002' || e.code === 'QUE-SESSION-001') return setPhase({ kind: 'ended', reason: 'gone' });
      return setPhase({ kind: 'error', code: e.code, retryAfterSeconds: e.retryAfterSeconds });
    }
    setPhase({ kind: 'error', code: 'NETWORK' });
  }, []);

  useEffect(() => {
    let disposed = false;
    let source: EventSource | undefined;
    let pollTimer: ReturnType<typeof setInterval> | undefined;
    samples.current = [];
    transport.current = 'sse';
    setPhase({ kind: 'joining' });

    const startPolling = () => {
      if (disposed || pollTimer) return;
      transport.current = 'polling';
      pollTimer = setInterval(() => {
        queueApi.heartbeat(scope).then(apply).catch(fail);
      }, POLL_INTERVAL_MS);
    };

    const openStream = () => {
      if (typeof EventSource === 'undefined') return startPolling();
      source = new EventSource(queueApi.eventsUrl(scope), { withCredentials: true });
      stream.current = source;
      source.addEventListener('status', (ev) => apply(JSON.parse((ev as MessageEvent<string>).data) as SessionResponse));
      source.addEventListener('gone', () => {
        source?.close();
        setPhase({ kind: 'ended', reason: 'gone' });
      });
      // CONNECTING 이면 브라우저가 재접속 중(timeout 후 정상). CLOSED 는 영구 실패(401/404 등) → 폴링으로 전환.
      source.onerror = () => {
        if (source?.readyState === EventSource.CLOSED) startPolling();
      };
    };

    queueApi
      .join(scope)
      .then((session) => {
        if (disposed) return;
        apply(session);
        if (session.status === 'WAITING' && !session.reentryDenied) openStream();
      })
      .catch((e) => !disposed && fail(e));

    return () => {
      disposed = true;
      source?.close();
      stream.current = undefined;
      if (pollTimer) clearInterval(pollTimer);
    };
  }, [scope.siteId, scope.queueId, attempt, apply, fail]);

  const leave = useCallback(async () => {
    try {
      apply(await queueApi.leave(scope));
    } catch (e) {
      fail(e);
    }
  }, [scope, apply, fail]);

  const rejoin = useCallback(() => setAttempt((n) => n + 1), []);

  return { phase, leave, rejoin };
}
