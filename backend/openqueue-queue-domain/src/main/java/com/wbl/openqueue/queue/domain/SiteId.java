package com.wbl.openqueue.queue.domain;

/**
 * 보호 대상 사이트 식별자 (DEC-12, SRS QUE-002 선구현 범위).
 *
 * <p>형식: {@code ^[a-z0-9][a-z0-9-]{0,63}$}. Redis key 및 정책 범위의 일부로 사용된다.
 *
 * @param value 검증된 식별자 문자열
 * @throws IllegalArgumentException null 이거나 형식이 맞지 않는 경우
 */
public record SiteId(String value) {

    public SiteId {
        IdentifierRules.requireValid(value, IdentifierRules.SCOPE_ID, "site_id");
    }
}
