package com.wbl.openqueue.queue.domain;

import java.time.Duration;

/**
 * 대기열 운영 정책 (QUE-004, QUE-005, QUE-007 범위).
 *
 * <p>기본값을 두지 않는다(D-QUE-004-3). 운영 기본값과 값의 상·하한은 Control Plane 정책 검증(Phase 5)에서 정한다.
 *
 * @param waitingSessionTtl 마지막 활동 이후 이 시간이 지나면 대기(WAITING) 세션을 만료한다 (양수, QUE-004)
 * @param reentry           종단 상태 세션의 재등록 처리 (QUE-004)
 * @param concurrencyLimit  동시에 입장(ADMITTED, lease 유효)할 수 있는 최대 세션 수 (1 이상, QUE-005)
 * @param admissionLeaseTtl 입장 후 마지막 활동 이후 이 시간이 지나면 입장 lease 를 만료해 슬롯을 회수한다 (양수, QUE-007)
 */
public record QueuePolicy(Duration waitingSessionTtl, ReentryPolicy reentry, int concurrencyLimit, Duration admissionLeaseTtl) {

    /**
     * 정책 값을 검증한다.
     *
     * @throws IllegalArgumentException ttl 류가 null·0·음수이거나, reentry 가 null 이거나, 상한이 1 미만인 경우
     */
    public QueuePolicy {
        IdentifierRules.requireNonNull(reentry, "reentry");
        requirePositive(waitingSessionTtl, "waiting_session_ttl");
        requirePositive(admissionLeaseTtl, "admission_lease_ttl");
        if (concurrencyLimit < 1) {
            // 상한 0 은 "아무도 입장 불가"가 아니라 설정 오류로 본다. 입장 차단은 QUE-009(Fail-closed)·운영 정책의 역할이다.
            throw new IllegalArgumentException("concurrency_limit 은 1 이상이어야 합니다.");
        }
    }

    private static void requirePositive(Duration value, String fieldName) {
        IdentifierRules.requireNonNull(value, fieldName);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " 은 0보다 커야 합니다.");
        }
    }
}
