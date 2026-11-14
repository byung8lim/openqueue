/**
 * Queue API 클라이언트 (docs/api/queue-api.md).
 * 세션은 서버가 내려주는 HttpOnly 쿠키로만 식별되므로(D-API-001) 이 코드는 세션 ID 를 다루지 않는다.
 * 모든 요청은 credentials: 'include' 로 쿠키를 함께 보낸다.
 */
export type SessionStatus = 'WAITING' | 'ADMITTED' | 'EXPIRED' | 'CANCELLED';

export interface SessionResponse {
  siteId: string;
  queueId: string;
  status: SessionStatus;
  position?: number;
  waitingCount: number;
  registeredAt: string;
  lastSeenAt: string;
  expiresAt: string;
  reentryDenied: boolean;
  serverTime: string;
}

export interface QueueScope {
  siteId: string;
  queueId: string;
}

/** 서버 ProblemDetail 의 code(QUE-*)를 보존하는 오류. 메시지에 사용자 입력·세션 정보를 넣지 않는다. */
export class QueueApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly retryAfterSeconds?: number,
  ) {
    super(`queue api error ${status} ${code}`);
    this.name = 'QueueApiError';
  }
}

export function sessionPath(scope: QueueScope): string {
  return `/queue/v1/sites/${encodeURIComponent(scope.siteId)}/queues/${encodeURIComponent(scope.queueId)}/session`;
}

async function call(method: string, url: string): Promise<SessionResponse> {
  const response = await fetch(url, {
    method,
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    // ProblemDetail 의 type 은 urn:openqueue:problem:<code 소문자>. 본문이 없거나 JSON 이 아니면 HTTP 상태만 사용한다.
    let code = `HTTP-${response.status}`;
    try {
      const problem = (await response.json()) as { type?: string };
      const suffix = problem.type?.split(':').pop();
      if (suffix) code = suffix.toUpperCase();
    } catch {
      /* 본문 없음 */
    }
    const retry = response.headers.get('Retry-After');
    throw new QueueApiError(response.status, code, retry ? Number(retry) : undefined);
  }
  return (await response.json()) as SessionResponse;
}

export const queueApi = {
  /** 대기 등록. 유효한 쿠키가 있으면 서버가 기존 세션을 복구한다(WUI-002). */
  join: (scope: QueueScope) => call('POST', sessionPath(scope)),
  status: (scope: QueueScope) => call('GET', sessionPath(scope)),
  heartbeat: (scope: QueueScope) => call('POST', `${sessionPath(scope)}/heartbeat`),
  leave: (scope: QueueScope) => call('DELETE', sessionPath(scope)),
  eventsUrl: (scope: QueueScope) => `${sessionPath(scope)}/events`,
};
