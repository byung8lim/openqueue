import { WaitingRoom } from './WaitingRoom';
import type { QueueScope } from './api/queueApi';

/**
 * 대기열 범위는 경로 /{siteId}/{queueId} 에서 읽는다(Gateway 가 이 경로로 보낸다, Phase 4).
 * 로컬 개발용으로 ?site=&queue= 쿼리도 허용한다. 서버가 식별자 형식을 다시 검증하므로 여기서는 비어 있는지만 본다.
 */
export function resolveScope(location: { pathname: string; search: string }): QueueScope | undefined {
  const [siteId, queueId] = location.pathname.split('/').filter(Boolean);
  if (siteId && queueId) return { siteId, queueId };
  const params = new URLSearchParams(location.search);
  const site = params.get('site');
  const queue = params.get('queue');
  return site && queue ? { siteId: site, queueId: queue } : undefined;
}

export function App() {
  const scope = resolveScope(window.location);
  if (!scope) {
    return (
      <main className="card">
        <h1>대기실</h1>
        <div className="notice error" role="alert">
          잘못된 접근입니다. 서비스 주소로 다시 접속해 주세요.
        </div>
      </main>
    );
  }
  return <WaitingRoom scope={scope} />;
}
