package com.wbl.openqueue.queue.domain;

/**
 * 사이트 내 대기열 식별자 (DEC-12, SRS QUE-002 선구현 범위).
 *
 * <p>형식: {@code ^[a-z0-9][a-z0-9-]{0,63}$}. 동일 queue_id 라도 site_id 가 다르면 다른 대기열이다.
 *
 * @param value 검증된 식별자 문자열
 * @throws IllegalArgumentException null 이거나 형식이 맞지 않는 경우
 */
public record QueueId(String value) {

    public QueueId {
        IdentifierRules.requireValid(value, IdentifierRules.SCOPE_ID, "queue_id");
    }
}
