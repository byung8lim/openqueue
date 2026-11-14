/**
 * 예상 대기시간 추정(WUI-001). 서버는 순번만 주므로 클라이언트가 관측한 순번 감소 속도로 추정한다.
 * 표본: (시각 ms, 순번) 쌍. 최근 창(window) 안의 첫 표본과 마지막 표본 사이의 처리량으로 계산한다.
 */
export interface PositionSample {
  at: number;
  position: number;
}

export const DEFAULT_WINDOW_MS = 60_000;

/** 표본을 창 안으로 잘라 내고 새 표본을 덧붙인다(순수 함수). */
export function pushSample(samples: PositionSample[], sample: PositionSample, windowMs = DEFAULT_WINDOW_MS): PositionSample[] {
  return [...samples.filter((s) => sample.at - s.at <= windowMs), sample];
}

/**
 * @return 예상 남은 초. 표본이 2개 미만이거나 순번이 줄지 않았으면 undefined(화면은 "계산 중" 표시).
 */
export function estimateWaitSeconds(samples: PositionSample[]): number | undefined {
  if (samples.length < 2) return undefined;
  const first = samples[0];
  const last = samples[samples.length - 1];
  const advanced = first.position - last.position;
  const elapsedMs = last.at - first.at;
  if (advanced <= 0 || elapsedMs <= 0) return undefined;
  const perMs = advanced / elapsedMs;
  return Math.ceil(last.position / perMs / 1000);
}

/** 초를 "약 N분" / "1분 미만" 으로 표기한다. */
export function formatWait(seconds: number | undefined): string {
  if (seconds === undefined) return '계산 중';
  if (seconds < 60) return '1분 미만';
  return `약 ${Math.ceil(seconds / 60)}분`;
}
