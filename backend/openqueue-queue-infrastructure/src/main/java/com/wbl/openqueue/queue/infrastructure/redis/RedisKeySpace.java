package com.wbl.openqueue.queue.infrastructure.redis;

import com.wbl.openqueue.queue.domain.AdmissionRequestId;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.WaitingSessionId;

/**
 * 대기열 하나의 Redis key 이름 규칙 (QUE-008, docs/database/redis-keys.md §2).
 *
 * <p>모든 key 는 {@code oq:{site_id:queue_id}:...} 형태로 같은 hash tag 를 공유한다.
 * Redis Cluster 에서 한 대기열의 모든 key 가 같은 슬롯에 놓여 Lua 스크립트가 여러 key 를 원자적으로 다룰 수 있다(D-QUE-008-2).
 * 식별자는 {@code [a-z0-9-]} 로 검증되어 있어(D-QUE-001-3) key 구분자와 충돌하지 않는다.
 */
public final class RedisKeySpace {

    private static final String NAMESPACE = "oq";

    private final String prefix;

    /**
     * @param scope 대기열 범위
     */
    public RedisKeySpace(QueueScope scope) {
        this.prefix = NAMESPACE + ":{" + scope.siteId().value() + ":" + scope.queueId().value() + "}:";
    }

    /** FIFO 순번용 단조 증가 시퀀스 (STRING, INCR). */
    public String sequence() {
        return prefix + "seq";
    }

    /** 대기 순서 (ZSET, score = 등록 시퀀스). WAITING 세션만 포함한다. */
    public String waiting() {
        return prefix + "waiting";
    }

    /** 대기 세션 활동 시각 (ZSET, score = last_seen_at ms). 대기 TTL 만료 판정용. */
    public String waitingSeen() {
        return prefix + "waiting_seen";
    }

    /** 입장 집합 (ZSET, score = last_seen_at ms). lease 만료 판정·활성 입장 수 계산용. */
    public String admitted() {
        return prefix + "admitted";
    }

    /** 입장 사건 (ZSET, score = 입장 시각 ms, member = "{ms}:{session_id}"). 입장률 창 집계용. */
    public String admissions() {
        return prefix + "admissions";
    }

    /** 세션 해시 key 접두어. Lua 스크립트가 세션 ID 를 붙여 key 를 만든다. */
    public String sessionPrefix() {
        return prefix + "session:";
    }

    /** 세션 해시 (HASH: status, registered_at, last_seen_at). 종단 상태는 보존 TTL 이 걸린다. */
    public String session(WaitingSessionId sessionId) {
        return sessionPrefix() + sessionId.value();
    }

    /** 입장 요청 멱등 키의 결과 (STRING, TTL = 결과 보존 기간). */
    public String admissionResult(AdmissionRequestId requestId) {
        return prefix + "admit:" + requestId.value();
    }
}
