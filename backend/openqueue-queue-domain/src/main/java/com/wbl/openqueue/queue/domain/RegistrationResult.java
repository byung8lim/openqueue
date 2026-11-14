package com.wbl.openqueue.queue.domain;

/**
 * 대기 등록 결과.
 *
 * @param sessionId       등록을 요청한 세션
 * @param newlyRegistered 이번 요청으로 새로 대기열에 추가되었으면 true,
 *                        이미 대기 중이거나 입장 완료된 세션이면 false(중복 등록 무시)
 */
public record RegistrationResult(WaitingSessionId sessionId, boolean newlyRegistered) {
}
