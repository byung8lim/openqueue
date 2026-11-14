package com.wbl.openqueue.queue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * QUE-005 동시 입장 상한 기반 입장 가능 인원 계산, QUE-007 입장 lease·heartbeat 단위 테스트.
 *
 * <p>각 테스트는 docs/requirements/SRS.md 의 AC-QUE-005-x / AC-QUE-007-x 와 1:1 대응한다.
 */
@DisplayName("QUE-005/007 동시 입장 상한과 입장 lease")
class AdmissionCapacityTest {

    private static final WaitingSessionId SESSION_A = new WaitingSessionId("sessionA-0000000001");
    private static final WaitingSessionId SESSION_B = new WaitingSessionId("sessionB-0000000002");
    private static final WaitingSessionId SESSION_C = new WaitingSessionId("sessionC-0000000003");
    private static final AdmissionRequestId REQUEST_1 = new AdmissionRequestId("request-0001");
    private static final AdmissionRequestId REQUEST_2 = new AdmissionRequestId("request-0002");
    private static final Instant T0 = Instant.parse("2026-11-14T00:00:00Z");
    private static final Duration WAITING_TTL = Duration.ofMinutes(5);
    private static final Duration LEASE_TTL = Duration.ofMinutes(10);
    private static final AdmissionRateLimit UNLIMITED = AdmissionRateLimit.unlimited();

    private static WaitingQueue newQueue(int concurrencyLimit, ReentryPolicy reentry) {
        return new WaitingQueue(new QueueScope(new SiteId("shop"), new QueueId("main")),
            new QueuePolicy(WAITING_TTL, reentry, concurrencyLimit, LEASE_TTL, UNLIMITED));
    }

    /** A 를 T0 에 등록하고 바로 입장시킨다(lease 시작 = T0). */
    private static WaitingQueue queueWithAdmittedA(int concurrencyLimit, ReentryPolicy reentry) {
        WaitingQueue queue = newQueue(concurrencyLimit, reentry);
        queue.register(SESSION_A, T0);
        queue.admit(REQUEST_1, T0);
        return queue;
    }

    @Nested
    @DisplayName("AC-QUE-005 동시 입장 상한")
    class ConcurrencyLimit {

        @Test
        @DisplayName("AC-QUE-005-1 상한(2)만큼만 선정되고 나머지는 순번을 유지한다")
        void admitsUpToConcurrencyLimit() {
            WaitingQueue queue = newQueue(2, ReentryPolicy.ALLOW);
            queue.register(SESSION_A, T0);
            queue.register(SESSION_B, T0);
            queue.register(SESSION_C, T0);

            AdmissionResult result = queue.admit(REQUEST_1, T0);

            assertThat(result.admittedSessions()).containsExactly(SESSION_A, SESSION_B);
            assertThat(queue.statusOf(SESSION_C)).contains(WaitingSessionStatus.WAITING);
            assertThat(queue.positionOf(SESSION_C)).hasValue(1);
            assertThat(queue.availableSlots(T0)).isZero();
        }

        @Test
        @DisplayName("AC-QUE-005-2 슬롯이 없으면 빈 결과를 반환하고 재전송에도 같은 결과다")
        void noSlotsYieldsEmptyIdempotentResult() {
            WaitingQueue queue = queueWithAdmittedA(1, ReentryPolicy.ALLOW);
            queue.register(SESSION_B, T0);

            AdmissionResult first = queue.admit(REQUEST_2, T0);
            AdmissionResult resent = queue.admit(REQUEST_2, T0);

            assertThat(first.admittedSessions()).isEmpty();
            assertThat(resent).isEqualTo(first);
            assertThat(queue.statusOf(SESSION_B)).contains(WaitingSessionStatus.WAITING);
            assertThat(queue.positionOf(SESSION_B)).hasValue(1);
        }

        @Test
        @DisplayName("AC-QUE-005-3 호출자가 명시한 인원도 남은 슬롯으로 제한된다")
        void explicitSlotsAreCappedByRemainingCapacity() {
            WaitingQueue queue = newQueue(2, ReentryPolicy.ALLOW);
            queue.register(SESSION_A, T0);
            queue.register(SESSION_B, T0);
            queue.register(SESSION_C, T0);

            AdmissionResult result = queue.admit(REQUEST_1, 5, T0);

            assertThat(result.admittedSessions()).containsExactly(SESSION_A, SESSION_B);
            assertThat(queue.waitingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-QUE-005-4 입장 포기(취소)는 슬롯을 즉시 회수한다")
        void cancelReleasesSlot() {
            WaitingQueue queue = queueWithAdmittedA(1, ReentryPolicy.ALLOW);
            queue.register(SESSION_B, T0);
            assertThat(queue.availableSlots(T0)).isZero();

            assertThat(queue.cancel(SESSION_A)).isTrue();

            assertThat(queue.availableSlots(T0)).isEqualTo(1);
            assertThat(queue.admit(REQUEST_2, T0).admittedSessions()).containsExactly(SESSION_B);
        }

        @Test
        @DisplayName("AC-QUE-005-5 정책 검증: 상한이 0 또는 음수면 입력 오류")
        void concurrencyLimitValidation() {
            assertThatThrownBy(() -> new QueuePolicy(WAITING_TTL, ReentryPolicy.ALLOW, 0, LEASE_TTL, UNLIMITED))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new QueuePolicy(WAITING_TTL, ReentryPolicy.ALLOW, -1, LEASE_TTL, UNLIMITED))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AC-QUE-005-6 availableSlots 조회는 lease 만료를 반영하되 상태를 바꾸지 않는다")
        void availableSlotsQueryDoesNotMutate() {
            WaitingQueue queue = queueWithAdmittedA(1, ReentryPolicy.ALLOW);

            assertThat(queue.availableSlots(T0.plus(LEASE_TTL).minusNanos(1))).isZero();
            assertThat(queue.availableSlots(T0.plus(LEASE_TTL))).isEqualTo(1);
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.ADMITTED);
        }

        @Test
        @DisplayName("null 입력은 입력 오류다")
        void nullInputsAreRejected() {
            WaitingQueue queue = newQueue(1, ReentryPolicy.ALLOW);
            assertThatThrownBy(() -> queue.admit(null, T0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.admit(REQUEST_1, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.availableSlots(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.expireLeases(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("AC-QUE-007 입장 lease 와 heartbeat")
    class AdmissionLease {

        @Test
        @DisplayName("AC-QUE-007-1 lease TTL 이 지나면 EXPIRED 가 되고 슬롯이 회수된다")
        void leaseExpiryReleasesSlot() {
            WaitingQueue queue = queueWithAdmittedA(1, ReentryPolicy.ALLOW);

            assertThat(queue.expireLeases(T0.plus(LEASE_TTL))).containsExactly(SESSION_A);

            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
            assertThat(queue.availableSlots(T0.plus(LEASE_TTL))).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-QUE-007-2 경계값: last_seen_at + lease_ttl <= now 일 때 만료")
        void leaseBoundary() {
            WaitingQueue queue = queueWithAdmittedA(1, ReentryPolicy.ALLOW);

            assertThat(queue.expireLeases(T0.plus(LEASE_TTL).minusNanos(1))).isEmpty();
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.ADMITTED);

            assertThat(queue.expireLeases(T0.plus(LEASE_TTL))).containsExactly(SESSION_A);
        }

        @Test
        @DisplayName("AC-QUE-007-3 heartbeat 는 lease 를 연장한다")
        void heartbeatExtendsLease() {
            WaitingQueue queue = queueWithAdmittedA(1, ReentryPolicy.ALLOW);
            assertThat(queue.heartbeat(SESSION_A, T0.plus(Duration.ofMinutes(8)))).isTrue();

            assertThat(queue.expireLeases(T0.plus(Duration.ofMinutes(10)))).isEmpty();
            assertThat(queue.expireLeases(T0.plus(Duration.ofMinutes(18)))).containsExactly(SESSION_A);
        }

        @Test
        @DisplayName("AC-QUE-007-4 lease 는 등록 시각이 아닌 입장 시각부터 시작한다")
        void leaseStartsAtAdmission() {
            WaitingQueue queue = newQueue(1, ReentryPolicy.ALLOW);
            queue.register(SESSION_A, T0);
            Instant admittedAt = T0.plus(Duration.ofMinutes(4));
            queue.admit(REQUEST_1, admittedAt);

            assertThat(queue.find(SESSION_A)).map(WaitingSession::lastSeenAt).contains(admittedAt);
            assertThat(queue.expireLeases(T0.plus(Duration.ofMinutes(10)))).isEmpty();
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.ADMITTED);
            assertThat(queue.expireLeases(T0.plus(Duration.ofMinutes(14)))).containsExactly(SESSION_A);
        }

        @Test
        @DisplayName("AC-QUE-007-5 입장 선정 전에 만료된 lease 를 먼저 회수한다")
        void admitReclaimsExpiredLeasesFirst() {
            WaitingQueue queue = queueWithAdmittedA(1, ReentryPolicy.ALLOW);
            // B 는 대기 TTL(5분) 안에 들어와야 하므로 T0+8분에 등록한다.
            queue.register(SESSION_B, T0.plus(Duration.ofMinutes(8)));

            AdmissionResult result = queue.admit(REQUEST_2, T0.plus(LEASE_TTL));

            assertThat(result.admittedSessions()).containsExactly(SESSION_B);
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
        }

        @Test
        @DisplayName("AC-QUE-007-6 lease 만료 세션의 재등록은 재진입 정책을 따른다")
        void reentryAfterLeaseExpiry() {
            Instant after = T0.plus(LEASE_TTL);

            WaitingQueue allowQueue = queueWithAdmittedA(1, ReentryPolicy.ALLOW);
            allowQueue.expireLeases(after);
            RegistrationResult rejoin = allowQueue.register(SESSION_A, after);
            assertThat(rejoin.outcome()).isEqualTo(RegistrationOutcome.REGISTERED);
            assertThat(allowQueue.positionOf(SESSION_A)).hasValue(1);

            WaitingQueue denyQueue = queueWithAdmittedA(1, ReentryPolicy.DENY);
            denyQueue.expireLeases(after);
            assertThat(denyQueue.register(SESSION_A, after).outcome()).isEqualTo(RegistrationOutcome.REENTRY_DENIED);
            assertThat(denyQueue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
        }

        @Test
        @DisplayName("AC-QUE-007-7 정책 검증: lease ttl 0·음수·null 은 입력 오류")
        void leaseTtlValidation() {
            assertThatThrownBy(() -> new QueuePolicy(WAITING_TTL, ReentryPolicy.ALLOW, 1, Duration.ZERO, UNLIMITED))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new QueuePolicy(WAITING_TTL, ReentryPolicy.ALLOW, 1, Duration.ofSeconds(-1), UNLIMITED))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new QueuePolicy(WAITING_TTL, ReentryPolicy.ALLOW, 1, null, UNLIMITED))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AC-QUE-007-8 대기 만료(expire)는 ADMITTED 세션을 건드리지 않는다")
        void waitingExpireLeavesAdmittedAlone() {
            WaitingQueue queue = queueWithAdmittedA(1, ReentryPolicy.ALLOW);

            assertThat(queue.expire(T0.plus(WAITING_TTL))).isEmpty();
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.ADMITTED);
            assertThat(queue.availableSlots(T0.plus(WAITING_TTL))).isZero();
        }
    }
}
