package com.wbl.openqueue.queue.domain;

import java.time.Instant;

/**
 * 대기 세션의 조회 뷰 (QUE-003). 불변이며 상태 변경 시 새 인스턴스로 교체한다.
 *
 * @param id           세션 식별자
 * @param status       현재 상태
 * @param registeredAt 이번 대기 등록 시각(재진입 시 갱신)
 * @param lastSeenAt   마지막 활동(등록·heartbeat) 시각
 */
public record WaitingSession(WaitingSessionId id, WaitingSessionStatus status, Instant registeredAt, Instant lastSeenAt) {

    WaitingSession withStatus(WaitingSessionStatus newStatus) {
        return new WaitingSession(id, newStatus, registeredAt, lastSeenAt);
    }

    WaitingSession withLastSeenAt(Instant now) {
        return new WaitingSession(id, status, registeredAt, now);
    }
}
