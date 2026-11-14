package com.wbl.openqueue.queue.application;

import com.wbl.openqueue.queue.domain.AdmissionResult;
import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueueScope;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 입장 주기 유스케이스 (QUE-001/005/006/007): 주기마다 대기열별로 새 멱등 키를 만들어 입장 후보를 선정한다.
 *
 * <p>한 주기에 멱등 키 하나를 쓰므로 같은 주기 안의 재시도는 중복 입장을 만들지 않는다. 다중 노드가 각자 주기를 돌려도
 * 상한·입장률은 Redis 스크립트가 보장한다(QUE-008). 주기 트리거(스케줄러)는 API 모듈이 담당한다.
 */
public final class AdmissionCycle {

    private final QueueRegistry registry;
    private final SessionIdGenerator ids;
    private final Clock clock;

    /**
     * @param registry 운영 중 대기열
     * @param ids      멱등 키 생성기
     * @param clock    시계
     */
    public AdmissionCycle(QueueRegistry registry, SessionIdGenerator ids, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 한 대기열에 대해 입장 선정을 1회 수행한다.
     *
     * @throws UnknownQueueException 운영 중이 아닌 대기열
     */
    public AdmissionResult run(QueueScope scope) {
        QueueOperations queue = registry.find(scope).orElseThrow(UnknownQueueException::new);
        return queue.admit(ids.newAdmissionRequestId(), clock.instant());
    }

    /**
     * 운영 중인 모든 대기열에 대해 입장 선정을 1회씩 수행한다. 한 대기열의 저장소 장애는 다른 대기열 처리를 막지 않도록
     * 호출자가 대기열별로 처리하려면 {@link #run} 을 쓴다.
     */
    public List<AdmissionResult> runAll() {
        List<AdmissionResult> results = new ArrayList<>();
        for (QueueScope scope : registry.scopes()) {
            results.add(run(scope));
        }
        return results;
    }
}
