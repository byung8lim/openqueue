package com.wbl.openqueue.queue.domain;

/**
 * 하나의 대기열을 식별하는 범위(site_id + queue_id).
 *
 * <p>불변조건: 모든 대기 순서·입장 판단은 이 범위 안에서만 이루어진다.
 * 서로 다른 범위의 세션이 섞이지 않도록 Redis key 와 토큰 claim 에도 동일 범위를 사용한다(QUE-008, TOK-002).
 *
 * @param siteId  사이트 식별자
 * @param queueId 대기열 식별자
 * @throws IllegalArgumentException 어느 하나라도 null 인 경우
 */
public record QueueScope(SiteId siteId, QueueId queueId) {

    public QueueScope {
        IdentifierRules.requireNonNull(siteId, "site_id");
        IdentifierRules.requireNonNull(queueId, "queue_id");
    }
}
