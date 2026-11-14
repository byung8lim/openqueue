package com.wbl.openqueue.queue.domain;

import java.time.Duration;

/**
 * 입장률(단위 시간당 입장 수) 제한 정책 (QUE-006).
 *
 * <p>"제한 없음"을 null 이나 특수 숫자(Integer.MAX_VALUE)로 표현하지 않고 별도 타입 {@link Unlimited} 로 둔다(D-QUE-006-5).
 * sealed 이므로 switch 에서 두 경우를 모두 다루지 않으면 컴파일 오류가 난다.
 *
 * <p>집계 규칙(D-QUE-006-2/3): 입장 시각 {@code t} 는 {@code t + window > now} 이면 창 안이다.
 * 취소·lease 만료로 슬롯이 회수되어도 입장 사건은 창 안에 남는다. 재전송은 입장 사건이 아니다.
 */
public sealed interface AdmissionRateLimit permits AdmissionRateLimit.Unlimited, AdmissionRateLimit.PerWindow {

    /** 입장률을 제한하지 않는다. 입장 시각을 기록하지 않는다(D-QUE-006-4). */
    static AdmissionRateLimit unlimited() {
        return Unlimited.INSTANCE;
    }

    /**
     * 창 길이 {@code window} 동안 최대 {@code maxAdmissions} 건까지만 입장시킨다(슬라이딩 창).
     *
     * @param maxAdmissions 창 안 최대 입장 건수 (1 이상)
     * @param window        창 길이 (양수)
     * @throws IllegalArgumentException 값이 범위를 벗어나거나 window 가 null 인 경우
     */
    static AdmissionRateLimit perWindow(int maxAdmissions, Duration window) {
        return new PerWindow(maxAdmissions, window);
    }

    /** 제한 없음. 단일 인스턴스를 쓰며 {@link #unlimited()} 로 얻는다. */
    record Unlimited() implements AdmissionRateLimit {
        private static final Unlimited INSTANCE = new Unlimited();
    }

    /**
     * 창 기반 제한.
     *
     * @param maxAdmissions 창 안 최대 입장 건수 (1 이상)
     * @param window        창 길이 (양수)
     */
    record PerWindow(int maxAdmissions, Duration window) implements AdmissionRateLimit {

        /**
         * 정책 값을 검증한다.
         *
         * @throws IllegalArgumentException maxAdmissions 가 1 미만이거나 window 가 null·0·음수인 경우
         */
        public PerWindow {
            IdentifierRules.requireNonNull(window, "admission_rate.window");
            if (maxAdmissions < 1) {
                throw new IllegalArgumentException("admission_rate.max_admissions 는 1 이상이어야 합니다.");
            }
            if (window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("admission_rate.window 는 0보다 커야 합니다.");
            }
        }
    }
}
