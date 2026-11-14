package com.wbl.openqueue.queue.api.web;

import com.wbl.openqueue.queue.application.SessionView;
import com.wbl.openqueue.queue.domain.WaitingSessionStatus;
import java.time.Instant;

/**
 * 공개 Queue API 세션 응답 (WUI-001/002). 세션 ID 는 본문에 넣지 않는다 — HttpOnly 쿠키로만 전달해 스크립트 노출(XSS 시 탈취)을 막는다.
 *
 * @param siteId        site_id
 * @param queueId       queue_id
 * @param status        WAITING / ADMITTED / EXPIRED / CANCELLED
 * @param position      대기 순번(대기 중이 아니면 null)
 * @param waitingCount  현재 대기 인원
 * @param registeredAt  등록 시각
 * @param lastSeenAt    마지막 활동 시각
 * @param expiresAt     현재 상태 만료 시각(종단 상태면 null)
 * @param reentryDenied 재진입 거부 여부
 * @param serverTime    서버 시각(클라이언트가 남은 시간을 계산할 기준)
 */
public record SessionResponse(String siteId, String queueId, WaitingSessionStatus status, Integer position, int waitingCount,
                              Instant registeredAt, Instant lastSeenAt, Instant expiresAt, boolean reentryDenied, Instant serverTime) {

    static SessionResponse from(SessionView view, Instant serverTime) {
        return new SessionResponse(view.scope().siteId().value(), view.scope().queueId().value(), view.status(),
            view.position().isPresent() ? view.position().getAsInt() : null, view.waitingCount(),
            view.registeredAt(), view.lastSeenAt(), view.expiresAt(), view.reentryDenied(), serverTime);
    }
}
