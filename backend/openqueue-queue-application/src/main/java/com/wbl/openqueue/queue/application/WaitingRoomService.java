package com.wbl.openqueue.queue.application;

import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.RegistrationOutcome;
import com.wbl.openqueue.queue.domain.RegistrationResult;
import com.wbl.openqueue.queue.domain.WaitingSession;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Waiting Room 유스케이스 (QUE-003/004, WUI-001/002): 입장(등록·재접속), 상태 조회, 활동 신호, 대기 포기.
 *
 * <p>시계는 주입받아 도메인에 {@code now} 로 넘긴다. 저장소 장애({@code QueueUnavailableException})는 그대로 전파하고
 * API 계층이 사이트 장애 정책에 따라 응답한다(QUE-009). 세션 소유 검증은 "세션 ID 소지 = 소유"이며 ID 는 API 가 HttpOnly 쿠키로만 전달한다.
 */
public final class WaitingRoomService {

    private final QueueRegistry registry;
    private final SessionIdGenerator ids;
    private final Clock clock;

    /**
     * @param registry 운영 중 대기열
     * @param ids      세션 ID 생성기
     * @param clock    시계(UTC)
     */
    public WaitingRoomService(QueueRegistry registry, SessionIdGenerator ids, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 대기열에 들어간다. 기존 세션 ID 가 있으면 재접속(순번 유지)이고, 없거나 미등록이면 새 세션을 만든다.
     *
     * <p>클라이언트가 보낸 세션 ID 가 이 대기열에 등록된 적이 없으면 그 ID 를 재사용하지 않고 새 ID 를 발급한다
     * (외부에서 고른 값이 세션 ID 가 되는 것을 막는다, SEC-002).
     *
     * @param scope    대기열 범위
     * @param existing 쿠키로 전달된 기존 세션 ID(없을 수 있음)
     * @return 현재 상태. 재진입이 거부되면 종단 상태 + {@code reentryDenied=true}
     * @throws UnknownQueueException 운영 중이 아닌 대기열
     */
    public SessionView join(QueueScope scope, Optional<WaitingSessionId> existing) {
        QueueOperations queue = queueOf(scope);
        Instant now = clock.instant();
        WaitingSessionId sessionId = existing.filter(id -> queue.find(id).isPresent()).orElseGet(ids::newSessionId);
        RegistrationResult result = queue.register(sessionId, now);
        boolean denied = result.outcome() == RegistrationOutcome.REENTRY_DENIED;
        WaitingSession session = queue.find(sessionId).orElseThrow(() -> new IllegalStateException("등록 직후 세션을 찾을 수 없습니다."));
        return SessionView.of(queue, session, denied);
    }

    /**
     * 상태 조회(상태 불변).
     *
     * @return 세션이 이 대기열에 없으면 빈 값(API 는 404)
     * @throws UnknownQueueException 운영 중이 아닌 대기열
     */
    public Optional<SessionView> status(QueueScope scope, WaitingSessionId sessionId) {
        QueueOperations queue = queueOf(scope);
        return queue.find(sessionId).map(session -> SessionView.of(queue, session, false));
    }

    /**
     * 활동 신호. 대기 TTL 또는 입장 lease 를 연장하고 최신 상태를 돌려준다.
     *
     * @return 세션이 없으면 빈 값. 종단 상태면 연장 없이 현재 상태
     */
    public Optional<SessionView> heartbeat(QueueScope scope, WaitingSessionId sessionId) {
        QueueOperations queue = queueOf(scope);
        queue.heartbeat(sessionId, clock.instant());
        return queue.find(sessionId).map(session -> SessionView.of(queue, session, false));
    }

    /**
     * 대기 포기(또는 입장 포기).
     *
     * @return 세션이 없으면 빈 값. 있으면 취소 후 상태(이미 종단이면 변경 없이 현재 상태)
     */
    public Optional<SessionView> leave(QueueScope scope, WaitingSessionId sessionId) {
        QueueOperations queue = queueOf(scope);
        queue.cancel(sessionId);
        return queue.find(sessionId).map(session -> SessionView.of(queue, session, false));
    }

    private QueueOperations queueOf(QueueScope scope) {
        return registry.find(scope).orElseThrow(UnknownQueueException::new);
    }
}
