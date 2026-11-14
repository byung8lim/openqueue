import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WaitingRoom } from './WaitingRoom';
import { resolveScope } from './App';
import type { SessionResponse } from './api/queueApi';

const scope = { siteId: 'demo', queueId: 'main' };

function session(partial: Partial<SessionResponse>): SessionResponse {
  return {
    siteId: 'demo',
    queueId: 'main',
    status: 'WAITING',
    position: 5,
    waitingCount: 12,
    registeredAt: '2026-11-15T00:00:00Z',
    lastSeenAt: '2026-11-15T00:00:00Z',
    expiresAt: '2026-11-15T00:05:00Z',
    reentryDenied: false,
    serverTime: '2026-11-15T00:00:00Z',
    ...partial,
  };
}

/** 테스트용 EventSource: 생성된 인스턴스를 모아 두고 테스트가 이벤트를 주입한다. */
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  readyState = FakeEventSource.OPEN;
  onerror: (() => void) | null = null;
  private listeners = new Map<string, ((ev: MessageEvent<string>) => void)[]>();
  closed = false;
  constructor(readonly url: string, readonly init?: EventSourceInit) {
    FakeEventSource.instances.push(this);
  }
  addEventListener(type: string, fn: (ev: MessageEvent<string>) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), fn]);
  }
  emit(type: string, data: unknown) {
    for (const fn of this.listeners.get(type) ?? []) fn({ data: JSON.stringify(data) } as MessageEvent<string>);
  }
  close() {
    this.closed = true;
    this.readyState = FakeEventSource.CLOSED;
  }
}

function mockFetch(handlers: Record<string, () => { status: number; body?: unknown }>) {
  const spy = vi.fn(async (url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url}`;
    const handler = handlers[key];
    if (!handler) throw new Error(`unexpected ${key}`);
    const { status, body } = handler();
    return { ok: status < 300, status, headers: new Headers(), json: async () => body } as unknown as Response;
  });
  vi.stubGlobal('fetch', spy);
  return spy;
}

const base = '/queue/v1/sites/demo/queues/main/session';

beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal('EventSource', FakeEventSource);
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('WaitingRoom', () => {
  it('등록 후 순번·대기 인원을 표시하고 SSE 를 쿠키 포함으로 연다 (WUI-001)', async () => {
    mockFetch({ [`POST ${base}`]: () => ({ status: 201, body: session({ position: 5 }) }) });
    render(<WaitingRoom scope={scope} />);
    expect(await screen.findByTestId('position')).toHaveTextContent('5');
    expect(screen.getByText(/대기 인원 12명/)).toBeInTheDocument();
    expect(screen.getByTestId('estimate')).toHaveTextContent('계산 중');
    expect(FakeEventSource.instances[0].url).toBe(`${base}/events`);
    expect(FakeEventSource.instances[0].init?.withCredentials).toBe(true);
  });

  it('SSE status 이벤트로 순번이 갱신되고 ADMITTED 면 입장 안내를 보인다', async () => {
    mockFetch({ [`POST ${base}`]: () => ({ status: 201, body: session({ position: 3 }) }) });
    render(<WaitingRoom scope={scope} />);
    await screen.findByTestId('position');
    act(() => FakeEventSource.instances[0].emit('status', session({ position: 1 })));
    expect(screen.getByTestId('position')).toHaveTextContent('1');
    act(() => FakeEventSource.instances[0].emit('status', session({ status: 'ADMITTED', position: undefined })));
    expect(screen.getByRole('status')).toHaveTextContent('입장 차례입니다');
  });

  it('gone 이벤트면 만료 안내와 다시 등록 버튼을 보이고, 누르면 다시 join 한다 (WUI-002)', async () => {
    const spy = mockFetch({ [`POST ${base}`]: () => ({ status: 201, body: session({}) }) });
    render(<WaitingRoom scope={scope} />);
    await screen.findByTestId('position');
    act(() => FakeEventSource.instances[0].emit('gone', {}));
    expect(screen.getByRole('status')).toHaveTextContent('다시 등록해 주세요');
    expect(FakeEventSource.instances[0].closed).toBe(true);
    await userEvent.click(screen.getByRole('button', { name: '다시 등록' }));
    await waitFor(() => expect(spy).toHaveBeenCalledTimes(2));
  });

  it('새로고침 시 서버가 기존 세션을 복구하면(같은 쿠키) 그대로 순번을 보인다 (WUI-002)', async () => {
    mockFetch({ [`POST ${base}`]: () => ({ status: 201, body: session({ position: 2, waitingCount: 2 }) }) });
    render(<WaitingRoom scope={scope} />);
    expect(await screen.findByTestId('position')).toHaveTextContent('2');
  });

  it('재진입 거부면 안내만 보이고 다시 등록 버튼이 없다', async () => {
    mockFetch({ [`POST ${base}`]: () => ({ status: 201, body: session({ status: 'EXPIRED', reentryDenied: true, position: undefined }) }) });
    render(<WaitingRoom scope={scope} />);
    // 로딩 문구도 role=status 이므로 최종 문구가 나타날 때까지 기다린다.
    expect(await screen.findByText(/재진입을 허용하지 않습니다/)).toBeInTheDocument();
    expect(screen.queryByRole('button')).toBeNull();
    expect(FakeEventSource.instances).toHaveLength(0);
  });

  it('대기 취소를 누르면 DELETE 후 취소 안내를 보인다', async () => {
    mockFetch({
      [`POST ${base}`]: () => ({ status: 201, body: session({}) }),
      [`DELETE ${base}`]: () => ({ status: 200, body: session({ status: 'CANCELLED', position: undefined }) }),
    });
    render(<WaitingRoom scope={scope} />);
    await screen.findByTestId('position');
    await userEvent.click(screen.getByRole('button', { name: '대기 취소' }));
    expect(await screen.findByRole('status')).toHaveTextContent('대기를 취소했습니다');
  });

  it('저장소 장애(503)면 오류 안내와 다시 시도 버튼을 보인다', async () => {
    mockFetch({ [`POST ${base}`]: () => ({ status: 503, body: { type: 'urn:openqueue:problem:que-store-001' } }) });
    render(<WaitingRoom scope={scope} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('일시적인 문제');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });

  it('SSE 가 영구 실패(CLOSED)하면 폴링(heartbeat)으로 전환한다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const spy = mockFetch({
      [`POST ${base}`]: () => ({ status: 201, body: session({ position: 4 }) }),
      [`POST ${base}/heartbeat`]: () => ({ status: 200, body: session({ position: 3 }) }),
    });
    render(<WaitingRoom scope={scope} />);
    await screen.findByTestId('position');
    const source = FakeEventSource.instances[0];
    act(() => {
      source.readyState = FakeEventSource.CLOSED;
      source.onerror?.();
    });
    await act(() => vi.advanceTimersByTimeAsync(5_100));
    expect(spy).toHaveBeenCalledWith(`${base}/heartbeat`, expect.objectContaining({ method: 'POST' }));
    expect(await screen.findByTestId('position')).toHaveTextContent('3');
    expect(screen.getByText(/주기적으로 갱신/)).toBeInTheDocument();
  });
});

describe('resolveScope', () => {
  it('경로 /{site}/{queue} 를 우선하고 없으면 쿼리를 쓴다', () => {
    expect(resolveScope({ pathname: '/demo/main', search: '' })).toEqual({ siteId: 'demo', queueId: 'main' });
    expect(resolveScope({ pathname: '/', search: '?site=a&queue=b' })).toEqual({ siteId: 'a', queueId: 'b' });
    expect(resolveScope({ pathname: '/', search: '' })).toBeUndefined();
  });
});
