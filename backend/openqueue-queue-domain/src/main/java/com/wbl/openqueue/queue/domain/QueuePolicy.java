package com.wbl.openqueue.queue.domain;

import java.time.Duration;

/**
 * 대기열 운영 정책 (QUE-004 범위).
 *
 * <p>기본값을 두지 않는다(D-QUE-004-3). 운영 기본값은 Control Plane 정책에서 정한다.
 *
 * @param waitingSessionTtl 마지막 활동 이후 이 시간이 지나면 대기 세션을 만료한다 (양수)
 * @param reentry           종단 상태 세션의 재등록 처리
 */
public record QueuePolicy(Duration waitingSessionTtl, ReentryPolicy reentry) {

    /**
     * 정책 값을 검증한다.
     *
     * @throws IllegalArgumentException ttl 이 null·0·음수이거나 reentry 가 null 인 경우
     */
    public QueuePolicy {
        IdentifierRules.requireNonNull(waitingSessionTtl, "waiting_session_ttl");
        IdentifierRules.requireNonNull(reentry, "reentry");
        if (waitingSessionTtl.isZero() || waitingSessionTtl.isNegative()) {
            throw new IllegalArgumentException("waiting_session_ttl 은 0보다 커야 합니다.");
        }
    }
}
