package com.wbl.openqueue.queue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wbl.openqueue.queue.domain.AdmissionRateLimit;
import com.wbl.openqueue.queue.domain.FailurePolicy;
import com.wbl.openqueue.queue.domain.QueueId;
import com.wbl.openqueue.queue.domain.QueuePolicy;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.ReentryPolicy;
import com.wbl.openqueue.queue.domain.SiteId;
import com.wbl.openqueue.queue.domain.WaitingQueue;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import com.wbl.openqueue.queue.domain.WaitingSessionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** WUI-001/002 유스케이스: 입장(등록·재접속·재진입), 조회, heartbeat, 포기, 입장 주기. 메모리 대기열로 검증한다. */
@DisplayName("WaitingRoomService / AdmissionCycle")
class WaitingRoomServiceTest {

    private static final QueueScope SCOPE = new QueueScope(new SiteId("shop"), new QueueId("main"));
    private static final QueueScope OTHER = new QueueScope(new SiteId("shop"), new QueueId("other"));
    private static final Instant T0 = Instant.parse("2026-11-14T00:00:00Z");
    private static final Duration WAITING_TTL = Duration.ofMinutes(5);
    private static final Duration LEASE_TTL = Duration.ofMinutes(10);

    private final MutableClock clock = new MutableClock(T0);
    private final SessionIdGenerator ids = new SessionIdGenerator();
    private final WaitingQueue queue = new WaitingQueue(SCOPE,
        new QueuePolicy(WAITING_TTL, ReentryPolicy.DENY, 1, LEASE_TTL, AdmissionRateLimit.unlimited()));
    private final QueueRegistry registry =
        new InMemoryQueueRegistry(List.of(new InMemoryQueueRegistry.Entry(queue, FailurePolicy.FAIL_OPEN)));
    private final WaitingRoomService service = new WaitingRoomService(registry, ids, clock);
    private final AdmissionCycle cycle = new AdmissionCycle(registry, ids, clock);

    @Test
    @DisplayName("AC-WUI-002-1 세션 없이 입장하면 새 세션(43자 base64url)이 만들어지고 순번 1, 만료 시각 = 등록 + 대기 TTL")
    void joinCreatesNewSession() {
        SessionView view = service.join(SCOPE, Optional.empty());

        assertThat(view.sessionId().value()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(view.status()).isEqualTo(WaitingSessionStatus.WAITING);
        assertThat(view.position()).hasValue(1);
        assertThat(view.waitingCount()).isEqualTo(1);
        assertThat(view.expiresAt()).isEqualTo(T0.plus(WAITING_TTL));
        assertThat(view.isLive()).isTrue();
        assertThat(view.reentryDenied()).isFalse();
    }

    @Test
    @DisplayName("AC-WUI-002-2 기존 세션으로 다시 입장(새로고침)하면 순번을 유지하고 활동 시각이 갱신된다")
    void rejoinKeepsPosition() {
        SessionView first = service.join(SCOPE, Optional.empty());
        service.join(SCOPE, Optional.empty());
        clock.advance(Duration.ofMinutes(2));

        SessionView again = service.join(SCOPE, Optional.of(first.sessionId()));

        assertThat(again.sessionId()).isEqualTo(first.sessionId());
        assertThat(again.position()).hasValue(1);
        assertThat(again.lastSeenAt()).isEqualTo(T0.plus(Duration.ofMinutes(2)));
        assertThat(again.waitingCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("SEC-002 미등록 세션 ID 를 보내면 그 값을 재사용하지 않고 새 ID 를 발급한다")
    void unknownClientSessionIdIsNotReused() {
        WaitingSessionId forged = new WaitingSessionId("attacker-chosen-value-0001");

        SessionView view = service.join(SCOPE, Optional.of(forged));

        assertThat(view.sessionId()).isNotEqualTo(forged);
        assertThat(queue.find(forged)).isEmpty();
    }

    @Test
    @DisplayName("AC-WUI-002-3 만료된 세션의 재입장은 재진입 정책(DENY)에 따라 거부되고 상태를 알려준다")
    void reentryDeniedIsReported() {
        SessionView first = service.join(SCOPE, Optional.empty());
        clock.advance(WAITING_TTL);
        queue.expire(clock.instant());

        SessionView denied = service.join(SCOPE, Optional.of(first.sessionId()));

        assertThat(denied.status()).isEqualTo(WaitingSessionStatus.EXPIRED);
        assertThat(denied.reentryDenied()).isTrue();
        assertThat(denied.isLive()).isFalse();
        assertThat(denied.expiresAt()).isNull();
    }

    @Test
    @DisplayName("상태 조회·heartbeat·포기: 없는 세션은 빈 값, heartbeat 는 만료 시각을 연장, 포기는 CANCELLED")
    void statusHeartbeatLeave() {
        SessionView joined = service.join(SCOPE, Optional.empty());
        WaitingSessionId unknown = new WaitingSessionId("unknown-session-0000000001");

        assertThat(service.status(SCOPE, unknown)).isEmpty();
        assertThat(service.heartbeat(SCOPE, unknown)).isEmpty();
        assertThat(service.leave(SCOPE, unknown)).isEmpty();

        clock.advance(Duration.ofMinutes(3));
        assertThat(service.heartbeat(SCOPE, joined.sessionId())).map(SessionView::expiresAt)
            .contains(T0.plus(Duration.ofMinutes(3)).plus(WAITING_TTL));
        assertThat(service.leave(SCOPE, joined.sessionId())).map(SessionView::status).contains(WaitingSessionStatus.CANCELLED);
        assertThat(service.status(SCOPE, joined.sessionId())).map(SessionView::position).hasValueSatisfying(p -> assertThat(p).isEmpty());
    }

    @Test
    @DisplayName("운영 중이 아닌 대기열은 UnknownQueueException, 장애 정책은 등록 값 또는 기본값")
    void unknownQueueAndFailurePolicy() {
        assertThatThrownBy(() -> service.join(OTHER, Optional.empty())).isInstanceOf(UnknownQueueException.class);
        assertThatThrownBy(() -> cycle.run(OTHER)).isInstanceOf(UnknownQueueException.class);
        assertThat(registry.failurePolicyOf(SCOPE)).isEqualTo(FailurePolicy.FAIL_OPEN);
        assertThat(registry.failurePolicyOf(OTHER)).isEqualTo(FailurePolicy.DEFAULT);
        assertThat(registry.scopes()).containsExactly(SCOPE);
    }

    @Test
    @DisplayName("입장 주기는 매번 새 멱등 키로 상한만큼 선정하고, ADMITTED 뷰의 만료 시각은 입장 + lease TTL")
    void admissionCycleAdmitsAndViewShowsLease() {
        SessionView a = service.join(SCOPE, Optional.empty());
        SessionView b = service.join(SCOPE, Optional.empty());
        clock.advance(Duration.ofMinutes(1));

        assertThat(cycle.runAll()).singleElement().satisfies(r -> assertThat(r.admittedSessions()).containsExactly(a.sessionId()));
        assertThat(cycle.run(SCOPE).admittedSessions()).isEmpty();

        SessionView admitted = service.status(SCOPE, a.sessionId()).orElseThrow();
        assertThat(admitted.status()).isEqualTo(WaitingSessionStatus.ADMITTED);
        assertThat(admitted.position()).isEmpty();
        assertThat(admitted.expiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(1)).plus(LEASE_TTL));
        assertThat(service.status(SCOPE, b.sessionId()).orElseThrow().position()).hasValue(1);
    }

    @Test
    @DisplayName("같은 범위를 두 번 등록하면 입력 오류")
    void duplicateScopeRejected() {
        InMemoryQueueRegistry.Entry entry = new InMemoryQueueRegistry.Entry(queue, FailurePolicy.FAIL_CLOSED);
        assertThatThrownBy(() -> new InMemoryQueueRegistry(List.of(entry, entry))).isInstanceOf(IllegalArgumentException.class);
    }

    /** 테스트용 가변 시계. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
