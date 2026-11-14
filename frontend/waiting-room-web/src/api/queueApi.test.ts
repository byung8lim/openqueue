import { describe, expect, it, vi, afterEach } from 'vitest';
import { QueueApiError, queueApi, sessionPath } from './queueApi';

const scope = { siteId: 'demo', queueId: 'main' };

function mockFetch(status: number, body?: unknown, headers: Record<string, string> = {}) {
  const response = {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(headers),
    json: () => (body === undefined ? Promise.reject(new Error('no body')) : Promise.resolve(body)),
  } as unknown as Response;
  const spy = vi.fn().mockResolvedValue(response);
  vi.stubGlobal('fetch', spy);
  return spy;
}

afterEach(() => vi.unstubAllGlobals());

describe('queueApi', () => {
  it('경로는 식별자를 URL 인코딩한다', () => {
    expect(sessionPath({ siteId: 'a b', queueId: 'q/1' })).toBe('/queue/v1/sites/a%20b/queues/q%2F1/session');
  });

  it('join 은 POST + credentials include 로 호출하고 본문을 돌려준다 (AC-QUE-011-1 클라이언트 측)', async () => {
    const body = { siteId: 'demo', queueId: 'main', status: 'WAITING', position: 3, waitingCount: 10 };
    const spy = mockFetch(201, body);
    await expect(queueApi.join(scope)).resolves.toEqual(body);
    expect(spy).toHaveBeenCalledWith('/queue/v1/sites/demo/queues/main/session', expect.objectContaining({ method: 'POST', credentials: 'include' }));
  });

  it('오류 응답은 ProblemDetail type 에서 코드를 뽑아 QueueApiError 로 던진다', async () => {
    mockFetch(404, { type: 'urn:openqueue:problem:que-session-002', title: '세션 없음' });
    await expect(queueApi.status(scope)).rejects.toMatchObject({ status: 404, code: 'QUE-SESSION-002' });
  });

  it('429 는 Retry-After 를 초 단위로 보존한다', async () => {
    mockFetch(429, { type: 'urn:openqueue:problem:que-rate-001' }, { 'Retry-After': '17' });
    const err = await queueApi.heartbeat(scope).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(QueueApiError);
    expect((err as QueueApiError).retryAfterSeconds).toBe(17);
  });

  it('본문 없는 오류는 HTTP 상태 코드를 코드로 쓴다', async () => {
    mockFetch(503);
    await expect(queueApi.leave(scope)).rejects.toMatchObject({ code: 'HTTP-503' });
  });
});
