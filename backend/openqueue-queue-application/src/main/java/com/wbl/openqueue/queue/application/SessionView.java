package com.wbl.openqueue.queue.application;

import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.WaitingSession;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import com.wbl.openqueue.queue.domain.WaitingSessionStatus;
import java.time.Instant;
import java.util.OptionalInt;

/**
 * Waiting Room 에 보여주는 세션 상태 (WUI-001/002).
 *
 * @param scope         대기열 범위
 * @param sessionId     세션 ID (응답 본문에는 넣지 않고 쿠키로만 전달한다 — API 계층 책임)
 * @param status        현재 상태
 * @param position      대기 중이면 순번(1부터), 아니면 빈 값
 * @param waitingCount  현재 대기 인원
 * @param registeredAt  등록 시각
 * @param lastSeenAt    마지막 활동 시각
 * @param expiresAt     현재 상태가 만료되는 시각(WAITING: 대기 TTL, ADMITTED: lease). 종단 상태면 null
 * @param reentryDenied 종단 상태에서 재등록을 시도했으나 정책(DENY)으로 거부되었으면 true
 */
public record SessionView(QueueScope scope, WaitingSessionId sessionId, WaitingSessionStatus status, OptionalInt position,
                          int waitingCount, Instant registeredAt, Instant lastSeenAt, Instant expiresAt, boolean reentryDenied) {

    /** 대기열의 현재 상태로 뷰를 만든다. */
    static SessionView of(QueueOperations queue, WaitingSession session, boolean reentryDenied) {
        OptionalInt position = session.status() == WaitingSessionStatus.WAITING ? queue.positionOf(session.id()) : OptionalInt.empty();
        Instant expiresAt = switch (session.status()) {
            case WAITING -> session.lastSeenAt().plus(queue.policy().waitingSessionTtl());
            case ADMITTED -> session.lastSeenAt().plus(queue.policy().admissionLeaseTtl());
            case EXPIRED, CANCELLED -> null;
        };
        return new SessionView(queue.scope(), session.id(), session.status(), position, queue.waitingCount(),
            session.registeredAt(), session.lastSeenAt(), expiresAt, reentryDenied);
    }

    /** 이 상태에서 Waiting Room 이 더 갱신을 받을 필요가 있는가(WAITING 만 true). */
    public boolean isLive() {
        return status == WaitingSessionStatus.WAITING;
    }
}
