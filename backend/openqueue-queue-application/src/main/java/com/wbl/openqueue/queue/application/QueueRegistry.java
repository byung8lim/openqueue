package com.wbl.openqueue.queue.application;

import com.wbl.openqueue.queue.domain.FailurePolicy;
import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueueScope;
import java.util.Collection;
import java.util.Optional;

/**
 * 활성 대기열 목록 포트. 어떤 사이트·대기열이 운영 중이고 어떤 저장소·정책을 쓰는지 제공한다.
 *
 * <p>Phase 3 구현은 설정 파일 기반 {@link InMemoryQueueRegistry} 다. Phase 6 에서 서명된 정책 스냅샷(ADR-0009)으로 원자 교체되는 구현으로
 * 바뀌어도 유스케이스는 이 인터페이스만 본다.
 */
public interface QueueRegistry {

    /** 범위에 해당하는 대기열. 운영 중이 아니면 빈 값(API 는 404). */
    Optional<QueueOperations> find(QueueScope scope);

    /** 범위의 장애 정책(QUE-009). 등록되지 않은 범위는 {@link FailurePolicy#DEFAULT}. */
    FailurePolicy failurePolicyOf(QueueScope scope);

    /** 운영 중인 모든 대기열 범위(입장 주기 실행 대상). */
    Collection<QueueScope> scopes();
}
