import type { QueueScope } from './api/queueApi';
import { useWaitingSession } from './useWaitingSession';
import { formatWait } from './waitEstimate';

const ENDED_MESSAGE: Record<string, string> = {
  expired: '대기 시간이 만료되었습니다. 다시 등록해 주세요.',
  cancelled: '대기를 취소했습니다.',
  gone: '대기 세션을 찾을 수 없습니다. 다시 등록해 주세요.',
  reentryDenied: '이 대기열은 재진입을 허용하지 않습니다.',
};

const ERROR_MESSAGE: Record<string, string> = {
  'QUE-RATE-001': '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
  'QUE-STORE-001': '대기열 서비스에 일시적인 문제가 있습니다. 잠시 후 다시 시도해 주세요.',
  'QUE-QUEUE-001': '운영 중인 대기열이 아닙니다.',
  NETWORK: '네트워크 연결을 확인해 주세요.',
};

/** 대기 화면(WUI-001/002). 업무 규칙은 useWaitingSession 에 있고 이 컴포넌트는 표시만 담당한다. */
export function WaitingRoom({ scope }: { scope: QueueScope }) {
  const { phase, leave, rejoin } = useWaitingSession(scope);

  return (
    <main className="card" aria-live="polite">
      <h1>대기실</h1>
      {phase.kind === 'joining' && <p role="status">대기열에 등록하는 중…</p>}

      {phase.kind === 'waiting' && (
        <>
          <p className="muted">현재 순번</p>
          <p className="position" data-testid="position">
            {phase.session.position ?? '-'}
          </p>
          <p className="muted">
            대기 인원 {phase.session.waitingCount}명 · 예상 대기시간 <span data-testid="estimate">{formatWait(phase.estimateSeconds)}</span>
          </p>
          {phase.transport === 'polling' && <p className="muted">실시간 연결이 끊겨 주기적으로 갱신합니다.</p>}
          <p className="muted">이 화면을 닫지 마세요. 순서가 되면 자동으로 이동합니다.</p>
          <button onClick={() => void leave()}>대기 취소</button>
        </>
      )}

      {phase.kind === 'admitted' && (
        <div className="notice" role="status">
          입장 차례입니다. 잠시 후 서비스로 이동합니다.
        </div>
      )}

      {phase.kind === 'ended' && (
        <>
          <div className="notice" role="status">{ENDED_MESSAGE[phase.reason]}</div>
          {phase.reason !== 'reentryDenied' && (
            <button className="primary" onClick={rejoin}>
              다시 등록
            </button>
          )}
        </>
      )}

      {phase.kind === 'error' && (
        <>
          <div className="notice error" role="alert">
            {ERROR_MESSAGE[phase.code] ?? '오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'}
            {phase.retryAfterSeconds !== undefined && ` (${phase.retryAfterSeconds}초 후)`}
          </div>
          <button className="primary" onClick={rejoin}>
            다시 시도
          </button>
        </>
      )}
    </main>
  );
}
