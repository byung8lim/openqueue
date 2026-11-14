package com.wbl.openqueue.queue.domain;

/**
 * 대기 세션 상태.
 *
 * <p>상태 전이:
 * <pre>
 *   (미등록) → WAITING → ADMITTED
 *             WAITING → EXPIRED    (QUE-004, 대기 TTL 경과)
 *             WAITING → CANCELLED  (QUE-003, 사용자 취소)
 *             ADMITTED → CANCELLED (QUE-003, 입장 포기)
 *             ADMITTED → EXPIRED   (QUE-007, 입장 lease 만료)
 *   EXPIRED / CANCELLED → (재진입 ALLOW 시) 새 WAITING  (QUE-004)
 * </pre>
 * ADMITTED 세션은 다시 WAITING 이 될 수 없다. 대기 만료(QUE-004)와 lease 만료(QUE-007)는 모두 EXPIRED 로 전이한다(D-QUE-007-2).
 */
public enum WaitingSessionStatus {
    /** 대기 순서에 포함되어 입장을 기다리는 상태. */
    WAITING,
    /** 입장 후보로 선정되어 대기 순서에서 제거된 상태. 다시 대기열에 들어갈 수 없다. */
    ADMITTED,
    /** 대기 TTL(WAITING) 또는 입장 lease(ADMITTED) 동안 활동 신호가 없어 제거된 종단 상태. */
    EXPIRED,
    /** 사용자가 대기 또는 입장을 포기한 종단 상태. */
    CANCELLED;

    /** 종단 상태(재진입 정책 적용 대상)인지 여부. */
    public boolean isTerminal() {
        return this == EXPIRED || this == CANCELLED;
    }
}
