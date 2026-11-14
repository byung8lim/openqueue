package com.wbl.openqueue.queue.domain;

/**
 * 대기 세션 식별자.
 *
 * <p>추측 불가능한 opaque 값(base64url, 16~128자)이어야 한다. 개인정보를 포함하지 않는다(설계서 SEC-06).
 *
 * <p>보안 주의: 세션 ID는 사실상 대기 순번에 대한 소유 증명이므로 {@link #toString()} 은
 * 앞 4자만 노출하고 나머지는 마스킹한다. 로그에 원문이 남는 것을 방지한다(SEC-001).
 *
 * @param value 검증된 세션 식별자 원문
 * @throws IllegalArgumentException null 이거나 형식이 맞지 않는 경우
 */
public record WaitingSessionId(String value) {

    private static final int VISIBLE_PREFIX_LENGTH = 4;

    public WaitingSessionId {
        IdentifierRules.requireValid(value, IdentifierRules.SESSION_ID, "waiting_session_id");
    }

    /**
     * 마스킹된 문자열 표현을 반환한다. 원문이 필요하면 {@link #value()} 를 명시적으로 사용한다.
     *
     * @return 앞 4자 + "****" 형태
     */
    @Override
    public String toString() {
        return "WaitingSessionId[" + value.substring(0, VISIBLE_PREFIX_LENGTH) + "****]";
    }
}
