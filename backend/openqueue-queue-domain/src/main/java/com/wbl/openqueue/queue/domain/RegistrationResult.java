package com.wbl.openqueue.queue.domain;

/**
 * 대기 등록 결과.
 *
 * @param sessionId 등록을 요청한 세션
 * @param outcome   처리 결과 분류
 */
public record RegistrationResult(WaitingSessionId sessionId, RegistrationOutcome outcome) {

    /** 이번 요청으로 새로 대기열에 추가되었으면 true. */
    public boolean newlyRegistered() {
        return outcome == RegistrationOutcome.REGISTERED;
    }
}
