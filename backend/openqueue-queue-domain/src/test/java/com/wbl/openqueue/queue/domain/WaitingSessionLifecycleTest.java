package com.wbl.openqueue.queue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * QUE-003 대기 세션 생성·조회·취소, QUE-004 만료·재접속 규칙 단위 테스트.
 *
 * <p>각 테스트는 docs/requirements/SRS.md 의 AC-QUE-003-x / AC-QUE-004-x 와 1:1 대응한다.
 */
@DisplayName("QUE-003/004 대기 세션 생명주기")
class WaitingSessionLifecycleTest {

    private static final WaitingSessionId SESSION_A = new WaitingSessionId("sessionA-0000000001");
    private static final WaitingSessionId SESSION_B = new WaitingSessionId("sessionB-0000000002");
    private static final WaitingSessionId SESSION_C = new WaitingSessionId("sessionC-0000000003");
    private static final Instant T0 = Instant.parse("2026-11-14T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);

    private static WaitingQueue newQueue(ReentryPolicy reentry) {
        return new WaitingQueue(new QueueScope(new SiteId("shop"), new QueueId("main")), new QueuePolicy(TTL, reentry));
    }

    @Nested
    @DisplayName("AC-QUE-003 생성·조회·취소")
    class CreateLookupCancel {

        private final WaitingQueue queue = newQueue(ReentryPolicy.ALLOW);

        @Test
        @DisplayName("AC-QUE-003-1 생성 후 조회하면 WAITING, 순번 1, 등록·활동 시각이 T0 이다")
        void createdSessionIsVisible() {
            queue.register(SESSION_A, T0);

            assertThat(queue.find(SESSION_A)).contains(new WaitingSession(SESSION_A, WaitingSessionStatus.WAITING, T0, T0));
            assertThat(queue.positionOf(SESSION_A)).hasValue(1);
        }

        @Test
        @DisplayName("AC-QUE-003-2 가운데 세션을 취소하면 뒤 순번이 당겨진다")
        void cancelShiftsLaterSessions() {
            queue.register(SESSION_A, T0);
            queue.register(SESSION_B, T0);
            queue.register(SESSION_C, T0);

            assertThat(queue.cancel(SESSION_B)).isTrue();

            assertThat(queue.statusOf(SESSION_B)).contains(WaitingSessionStatus.CANCELLED);
            assertThat(queue.positionOf(SESSION_B)).isEmpty();
            assertThat(queue.positionOf(SESSION_C)).hasValue(2);
            assertThat(queue.waitingCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("AC-QUE-003-3 취소는 멱등이다")
        void cancelIsIdempotent() {
            queue.register(SESSION_B, T0);
            queue.cancel(SESSION_B);

            assertThat(queue.cancel(SESSION_B)).isFalse();
            assertThat(queue.statusOf(SESSION_B)).contains(WaitingSessionStatus.CANCELLED);
        }

        @Test
        @DisplayName("AC-QUE-003-4 미등록 세션 취소는 false 이고 세션이 생기지 않는다")
        void cancelUnknownSessionIsNoop() {
            assertThat(queue.cancel(SESSION_A)).isFalse();
            assertThat(queue.find(SESSION_A)).isEmpty();
        }

        @Test
        @DisplayName("AC-QUE-003-5 취소된 세션은 입장 후보가 되지 않는다")
        void cancelledSessionIsNotAdmitted() {
            queue.register(SESSION_A, T0);
            queue.register(SESSION_B, T0);
            queue.cancel(SESSION_A);

            AdmissionResult result = queue.admit(new AdmissionRequestId("request-0001"), 1, T0);

            assertThat(result.admittedSessions()).containsExactly(SESSION_B);
        }

        @Test
        @DisplayName("AC-QUE-003-6 입장 완료 세션의 취소는 입장 포기(CANCELLED)다")
        void cancelAdmittedSession() {
            queue.register(SESSION_A, T0);
            queue.admit(new AdmissionRequestId("request-0001"), 1, T0);

            assertThat(queue.cancel(SESSION_A)).isTrue();
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.CANCELLED);
        }

        @Test
        @DisplayName("null 입력은 입력 오류다")
        void nullInputsAreRejected() {
            assertThatThrownBy(() -> queue.register(null, T0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.register(SESSION_A, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.cancel(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.heartbeat(SESSION_A, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.expire(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("AC-QUE-004 만료와 재접속")
    class ExpiryAndReconnect {

        private final WaitingQueue queue = newQueue(ReentryPolicy.ALLOW);

        @Test
        @DisplayName("AC-QUE-004-1 TTL 이 지난 세션만 만료되고 뒤 순번이 당겨진다")
        void expiresOnlyStaleSessions() {
            queue.register(SESSION_A, T0);
            queue.register(SESSION_B, T0.plus(Duration.ofMinutes(1)));

            assertThat(queue.expire(T0.plus(TTL))).containsExactly(SESSION_A);

            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
            assertThat(queue.positionOf(SESSION_A)).isEmpty();
            assertThat(queue.positionOf(SESSION_B)).hasValue(1);
        }

        @Test
        @DisplayName("AC-QUE-004-2 경계값: last_seen_at + ttl <= now 일 때 만료")
        void expiryBoundary() {
            queue.register(SESSION_A, T0);

            assertThat(queue.expire(T0.plus(TTL).minusNanos(1))).isEmpty();
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.WAITING);

            assertThat(queue.expire(T0.plus(TTL))).containsExactly(SESSION_A);
        }

        @Test
        @DisplayName("AC-QUE-004-3 heartbeat 는 만료 시각을 연장한다")
        void heartbeatExtendsExpiry() {
            queue.register(SESSION_A, T0);
            assertThat(queue.heartbeat(SESSION_A, T0.plus(Duration.ofMinutes(4)))).isTrue();

            assertThat(queue.expire(T0.plus(Duration.ofMinutes(5)))).isEmpty();
            assertThat(queue.expire(T0.plus(Duration.ofMinutes(9)))).containsExactly(SESSION_A);
        }

        @Test
        @DisplayName("AC-QUE-004-4 재접속(재등록)은 순번을 유지하고 활동 시각을 갱신한다")
        void reconnectKeepsPositionAndRefreshesActivity() {
            queue.register(SESSION_A, T0);
            queue.register(SESSION_B, T0);
            Instant reconnectAt = T0.plus(Duration.ofMinutes(4));

            RegistrationResult again = queue.register(SESSION_A, reconnectAt);

            assertThat(again.outcome()).isEqualTo(RegistrationOutcome.ALREADY_WAITING);
            assertThat(queue.positionOf(SESSION_A)).hasValue(1);
            assertThat(queue.find(SESSION_A)).map(WaitingSession::lastSeenAt).contains(reconnectAt);
            assertThat(queue.expire(T0.plus(TTL))).containsExactly(SESSION_B);
        }

        @Test
        @DisplayName("AC-QUE-004-5 ALLOW: 만료 세션은 맨 뒤에 새로 등록된다")
        void reentryAllowedAfterExpiry() {
            queue.register(SESSION_A, T0);
            queue.register(SESSION_B, T0.plus(Duration.ofMinutes(1)));
            queue.expire(T0.plus(TTL));
            Instant rejoinAt = T0.plus(Duration.ofMinutes(6));

            RegistrationResult rejoin = queue.register(SESSION_A, rejoinAt);

            assertThat(rejoin.outcome()).isEqualTo(RegistrationOutcome.REGISTERED);
            assertThat(queue.positionOf(SESSION_A)).hasValue(2);
            assertThat(queue.find(SESSION_A)).contains(new WaitingSession(SESSION_A, WaitingSessionStatus.WAITING, rejoinAt, rejoinAt));
        }

        @Test
        @DisplayName("AC-QUE-004-5 DENY: 만료·취소 세션의 재등록은 거부되고 상태가 유지된다")
        void reentryDenied() {
            WaitingQueue denyQueue = newQueue(ReentryPolicy.DENY);
            denyQueue.register(SESSION_A, T0);
            denyQueue.register(SESSION_B, T0);
            denyQueue.cancel(SESSION_B);
            denyQueue.expire(T0.plus(TTL));

            assertThat(denyQueue.register(SESSION_A, T0.plus(TTL)).outcome()).isEqualTo(RegistrationOutcome.REENTRY_DENIED);
            assertThat(denyQueue.register(SESSION_B, T0.plus(TTL)).outcome()).isEqualTo(RegistrationOutcome.REENTRY_DENIED);
            assertThat(denyQueue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
            assertThat(denyQueue.statusOf(SESSION_B)).contains(WaitingSessionStatus.CANCELLED);
            assertThat(denyQueue.waitingCount()).isZero();
        }

        @Test
        @DisplayName("AC-QUE-004-6 입장 선정 전에 만료 세션을 먼저 제거한다")
        void admitExpiresStaleSessionsFirst() {
            queue.register(SESSION_A, T0);
            queue.register(SESSION_B, T0.plus(Duration.ofMinutes(1)));

            AdmissionResult result = queue.admit(new AdmissionRequestId("request-0001"), 1, T0.plus(TTL));

            assertThat(result.admittedSessions()).containsExactly(SESSION_B);
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
        }

        @Test
        @DisplayName("AC-QUE-004-7 종단 상태·미등록 세션의 heartbeat 는 false 이고 상태가 바뀌지 않는다")
        void heartbeatOnTerminalOrUnknownSession() {
            queue.register(SESSION_A, T0);
            queue.register(SESSION_B, T0);
            queue.cancel(SESSION_B);
            queue.expire(T0.plus(TTL));

            assertThat(queue.heartbeat(SESSION_A, T0.plus(TTL))).isFalse();
            assertThat(queue.heartbeat(SESSION_B, T0.plus(TTL))).isFalse();
            assertThat(queue.heartbeat(SESSION_C, T0.plus(TTL))).isFalse();
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
            assertThat(queue.statusOf(SESSION_B)).contains(WaitingSessionStatus.CANCELLED);
            assertThat(queue.find(SESSION_C)).isEmpty();
        }

        @Test
        @DisplayName("AC-QUE-004-8 정책 검증: ttl 0·음수·null, reentry null 은 입력 오류")
        void policyValidation() {
            assertThatThrownBy(() -> new QueuePolicy(Duration.ZERO, ReentryPolicy.ALLOW)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new QueuePolicy(Duration.ofSeconds(-1), ReentryPolicy.ALLOW))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new QueuePolicy(null, ReentryPolicy.ALLOW)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new QueuePolicy(TTL, null)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
