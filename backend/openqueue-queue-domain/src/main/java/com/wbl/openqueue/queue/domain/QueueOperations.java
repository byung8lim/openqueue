package com.wbl.openqueue.queue.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 대기열 연산 포트(계약). 메모리 구현 {@link WaitingQueue}(Phase 1, 규칙의 기준 구현)과
 * Redis 구현(Phase 2, `openqueue-queue-infrastructure`)이 같은 의미로 구현한다.
 *
 * <p>의미 규칙은 docs/requirements/SRS.md §3 QUE-001~007 의 수용 기준을 따른다. 구현별 차이는 다음만 허용한다.
 * <ul>
 *   <li>시간 정밀도: 메모리 구현은 나노초, Redis 구현은 밀리초.</li>
 *   <li>동시성: 메모리 구현은 스레드 안전하지 않다. Redis 구현은 Lua 스크립트로 연산 단위 원자성을 보장한다(QUE-008).</li>
 *   <li>가용성: Redis 구현은 저장소에 접근할 수 없으면 {@link QueueUnavailableException} 을 던진다(QUE-009).</li>
 * </ul>
 *
 * <p>모든 시간 의존 연산은 호출자가 {@code now} 를 넘긴다(D-QUE-004-1).
 */
public interface QueueOperations {

    /** 이 대기열의 범위(site_id + queue_id). */
    QueueScope scope();

    /** 이 대기열에 적용 중인 정책. */
    QueuePolicy policy();

    /** 세션 등록 또는 재접속·재진입 처리 (QUE-001, QUE-003, QUE-004). */
    RegistrationResult register(WaitingSessionId sessionId, Instant now);

    /** 활동 신호. WAITING 은 대기 TTL, ADMITTED 는 lease 를 연장한다 (QUE-004, QUE-007). */
    boolean heartbeat(WaitingSessionId sessionId, Instant now);

    /** 대기 포기 또는 입장 포기 (QUE-003). 종단 상태·미등록이면 false. */
    boolean cancel(WaitingSessionId sessionId);

    /** 대기 TTL 이 지난 WAITING 세션을 EXPIRED 로 전이한다 (QUE-004). */
    List<WaitingSessionId> expire(Instant now);

    /** 입장 lease 가 지난 ADMITTED 세션을 EXPIRED 로 전이하고 슬롯을 회수한다 (QUE-007). */
    List<WaitingSessionId> expireLeases(Instant now);

    /** 남은 슬롯·입장률 허용 인원만큼 선정한다 (QUE-005, QUE-006). 멱등 키 재전송은 최초 결과를 반환한다. */
    AdmissionResult admit(AdmissionRequestId requestId, Instant now);

    /** 요청 인원·남은 슬롯·입장률 허용 인원의 최솟값만큼 선정한다 (QUE-001, QUE-005, QUE-006). */
    AdmissionResult admit(AdmissionRequestId requestId, int requestedSlots, Instant now);

    /** 세션 조회(상태 불변). 미등록이면 빈 값. */
    Optional<WaitingSession> find(WaitingSessionId sessionId);

    /** 대기 중 세션의 순번(1부터). 대기 중이 아니면 빈 값. */
    OptionalInt positionOf(WaitingSessionId sessionId);

    /** 세션 상태. 미등록이면 빈 값. */
    Optional<WaitingSessionStatus> statusOf(WaitingSessionId sessionId);

    /** 현재 대기 중(WAITING) 세션 수. */
    int waitingCount();

    /** {@code now} 기준 남은 동시 입장 슬롯 수(상태 불변, QUE-005). */
    int availableSlots(Instant now);
}
