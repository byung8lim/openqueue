import { describe, expect, it } from 'vitest';
import { estimateWaitSeconds, formatWait, pushSample } from './waitEstimate';

describe('estimateWaitSeconds', () => {
  it('표본이 하나면 추정하지 않는다', () => {
    expect(estimateWaitSeconds([{ at: 0, position: 10 }])).toBeUndefined();
  });

  it('순번이 줄지 않으면 추정하지 않는다', () => {
    expect(estimateWaitSeconds([{ at: 0, position: 10 }, { at: 5000, position: 10 }])).toBeUndefined();
  });

  it('10초에 2명 처리되고 8번째면 40초로 추정한다', () => {
    expect(estimateWaitSeconds([{ at: 0, position: 10 }, { at: 10_000, position: 8 }])).toBe(40);
  });
});

describe('pushSample', () => {
  it('창(60s) 밖의 오래된 표본은 버린다', () => {
    const s = pushSample([{ at: 0, position: 5 }, { at: 30_000, position: 4 }], { at: 61_000, position: 3 });
    expect(s.map((x) => x.at)).toEqual([30_000, 61_000]);
  });
});

describe('formatWait', () => {
  it.each([
    [undefined, '계산 중'],
    [30, '1분 미만'],
    [61, '약 2분'],
    [600, '약 10분'],
  ])('%s → %s', (input, expected) => {
    expect(formatWait(input)).toBe(expected);
  });
});
