package com.wbl.openqueue.queue.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wbl.openqueue.queue.domain.AdmissionRateLimit;
import com.wbl.openqueue.queue.domain.AdmissionRequestId;
import com.wbl.openqueue.queue.domain.AdmissionResult;
import com.wbl.openqueue.queue.domain.QueueId;
import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueuePolicy;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.ReentryPolicy;
import com.wbl.openqueue.queue.domain.RegistrationOutcome;
import com.wbl.openqueue.queue.domain.SiteId;
import com.wbl.openqueue.queue.domain.WaitingSession;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import com.wbl.openqueue.queue.domain.WaitingSessionStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * QUE-008 Redis 어댑터 통합 테스트 (Testcontainers, Docker 필요 → `-Pintegration`, CI redis-integration 잡).
 *
 * <p>메모리 구현의 수용 기준(AC-QUE-001~007)을 Redis 구현에 대해 다시 검증하고, Redis 고유 규칙(다중 어댑터 공유 상태, 범위 격리,
 * 보존 TTL)을 추가로 확인한다. 시간은 ms 정밀도다.
 */
@Testcontainers
@DisplayName("QUE-008 RedisWaitingQueue (Redis 7)")
class RedisWaitingQueueIT {

    /** 컨테이너 전용 비밀번호. 운영 값이 아니며 ACL 인증 경로를 통합 테스트에 포함하기 위한 것이다. */
    private static final String CONTAINER_PASSWORD = "redis-local-test"; // gitleaks:allow
    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
        .withExposedPorts(REDIS_PORT)
        .withCommand("redis-server", "--requirepass", CONTAINER_PASSWORD);

    private static final QueueScope SCOPE = new QueueScope(new SiteId("shop"), new QueueId("main"));
    private static final WaitingSessionId SESSION_A = new WaitingSessionId("sessionA-0000000001");
    private static final WaitingSessionId SESSION_B = new WaitingSessionId("sessionB-0000000002");
    private static final WaitingSessionId SESSION_C = new WaitingSessionId("sessionC-0000000003");
    private static final WaitingSessionId SESSION_D = new WaitingSessionId("sessionD-0000000004");
    private static final AdmissionRequestId REQUEST_1 = new AdmissionRequestId("request-0001");
    private static final AdmissionRequestId REQUEST_2 = new AdmissionRequestId("request-0002");
    private static final AdmissionRequestId REQUEST_3 = new AdmissionRequestId("request-0003");
    private static final AdmissionRequestId REQUEST_4 = new AdmissionRequestId("request-0004");
    private static final Instant T0 = Instant.parse("2026-11-14T00:00:00Z");
    private static final Duration WAITING_TTL = Duration.ofMinutes(5);
    private static final Duration LEASE_TTL = Duration.ofMinutes(10);
    private static final RedisRetentionPolicy RETENTION = new RedisRetentionPolicy(Duration.ofHours(1), Duration.ofHours(1));

    private static RedisQueueConnection connection;

    @BeforeAll
    static void connect() {
        RedisConnectionSettings settings = new RedisConnectionSettings(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT), false,
            new RedisCredentials("default", CONTAINER_PASSWORD), Duration.ofSeconds(5));
        connection = RedisConnectionFactory.open(settings);
    }

    @AfterAll
    static void disconnect() {
        connection.close();
    }

    @BeforeEach
    void flush() {
        connection.commands().flushall();
    }

    private static QueueOperations queue(int limit, AdmissionRateLimit rate, ReentryPolicy reentry) {
        QueuePolicy policy = new QueuePolicy(WAITING_TTL, reentry, limit, LEASE_TTL, rate);
        return new RedisWaitingQueue(SCOPE, policy, RETENTION, connection.commands());
    }

    private static QueueOperations unlimitedQueue() {
        return queue(10, AdmissionRateLimit.unlimited(), ReentryPolicy.ALLOW);
    }

    @Test
    @DisplayName("AC-QUE-001-1 FIFO 선정과 재전송 멱등성")
    void fifoAndIdempotency() {
        QueueOperations queue = unlimitedQueue();
        queue.register(SESSION_A, T0);
        queue.register(SESSION_B, T0);

        AdmissionResult first = queue.admit(REQUEST_1, 1, T0);
        AdmissionResult resent = queue.admit(REQUEST_1, 5, T0);

        assertThat(first.admittedSessions()).containsExactly(SESSION_A);
        assertThat(resent).isEqualTo(first);
        assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.ADMITTED);
        assertThat(queue.positionOf(SESSION_B)).hasValue(1);
        assertThat(queue.find(SESSION_A)).contains(new WaitingSession(SESSION_A, WaitingSessionStatus.ADMITTED, T0, T0));
    }

    @Test
    @DisplayName("AC-QUE-001-2/3, AC-QUE-004-4 재등록은 순번을 유지하고 활동 시각만 갱신한다")
    void reRegistrationKeepsPosition() {
        QueueOperations queue = unlimitedQueue();
        queue.register(SESSION_A, T0);
        queue.register(SESSION_B, T0);
        Instant later = T0.plus(Duration.ofMinutes(4));

        assertThat(queue.register(SESSION_A, later).outcome()).isEqualTo(RegistrationOutcome.ALREADY_WAITING);
        assertThat(queue.positionOf(SESSION_A)).hasValue(1);
        assertThat(queue.find(SESSION_A)).map(WaitingSession::lastSeenAt).contains(later);

        queue.admit(REQUEST_1, 1, later);
        assertThat(queue.register(SESSION_A, later).outcome()).isEqualTo(RegistrationOutcome.ALREADY_ADMITTED);
        assertThat(queue.waitingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-QUE-003-2/3/4 취소는 뒤 순번을 당기고 멱등이며 미등록은 변경 없음")
    void cancelShiftsAndIsIdempotent() {
        QueueOperations queue = unlimitedQueue();
        queue.register(SESSION_A, T0);
        queue.register(SESSION_B, T0);
        queue.register(SESSION_C, T0);

        assertThat(queue.cancel(SESSION_B)).isTrue();
        assertThat(queue.positionOf(SESSION_C)).hasValue(2);
        assertThat(queue.waitingCount()).isEqualTo(2);
        assertThat(queue.cancel(SESSION_B)).isFalse();
        assertThat(queue.statusOf(SESSION_B)).contains(WaitingSessionStatus.CANCELLED);
        assertThat(queue.cancel(SESSION_D)).isFalse();
        assertThat(queue.find(SESSION_D)).isEmpty();
    }

    @Test
    @DisplayName("AC-QUE-004-1/3/7 대기 TTL 만료는 대기 순서대로 반환되고 heartbeat 가 연장한다")
    void waitingTtlExpiryAndHeartbeat() {
        QueueOperations queue = unlimitedQueue();
        queue.register(SESSION_A, T0);
        queue.register(SESSION_B, T0.plus(Duration.ofMinutes(1)));
        queue.register(SESSION_C, T0);
        assertThat(queue.heartbeat(SESSION_C, T0.plus(Duration.ofMinutes(4)))).isTrue();

        assertThat(queue.expire(T0.plus(WAITING_TTL))).containsExactly(SESSION_A);

        assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
        assertThat(queue.positionOf(SESSION_B)).hasValue(1);
        assertThat(queue.positionOf(SESSION_C)).hasValue(2);
        assertThat(queue.heartbeat(SESSION_A, T0.plus(WAITING_TTL))).isFalse();
    }

    @Test
    @DisplayName("AC-QUE-004-5 종단 상태 재등록은 재진입 정책을 따른다")
    void reentryPolicy() {
        QueueOperations allow = unlimitedQueue();
        allow.register(SESSION_A, T0);
        allow.expire(T0.plus(WAITING_TTL));
        Instant rejoin = T0.plus(Duration.ofMinutes(6));
        assertThat(allow.register(SESSION_A, rejoin).outcome()).isEqualTo(RegistrationOutcome.REGISTERED);
        assertThat(allow.find(SESSION_A)).contains(new WaitingSession(SESSION_A, WaitingSessionStatus.WAITING, rejoin, rejoin));

        connection.commands().flushall();
        QueueOperations deny = queue(10, AdmissionRateLimit.unlimited(), ReentryPolicy.DENY);
        deny.register(SESSION_A, T0);
        deny.cancel(SESSION_A);
        assertThat(deny.register(SESSION_A, rejoin).outcome()).isEqualTo(RegistrationOutcome.REENTRY_DENIED);
        assertThat(deny.statusOf(SESSION_A)).contains(WaitingSessionStatus.CANCELLED);
    }

    @Test
    @DisplayName("AC-QUE-004-6 입장 선정 전에 만료 세션을 먼저 제거한다")
    void admitExpiresStaleFirst() {
        QueueOperations queue = unlimitedQueue();
        queue.register(SESSION_A, T0);
        queue.register(SESSION_B, T0.plus(Duration.ofMinutes(1)));

        assertThat(queue.admit(REQUEST_1, 1, T0.plus(WAITING_TTL)).admittedSessions()).containsExactly(SESSION_B);
        assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
    }

    @Test
    @DisplayName("AC-QUE-005-1/2/4 동시 입장 상한과 취소에 의한 슬롯 회수")
    void concurrencyLimitAndCancelReleasesSlot() {
        QueueOperations queue = queue(2, AdmissionRateLimit.unlimited(), ReentryPolicy.ALLOW);
        queue.register(SESSION_A, T0);
        queue.register(SESSION_B, T0);
        queue.register(SESSION_C, T0);

        assertThat(queue.admit(REQUEST_1, T0).admittedSessions()).containsExactly(SESSION_A, SESSION_B);
        assertThat(queue.availableSlots(T0)).isZero();
        assertThat(queue.positionOf(SESSION_C)).hasValue(1);
        assertThat(queue.admit(REQUEST_2, T0).admittedSessions()).isEmpty();

        assertThat(queue.cancel(SESSION_A)).isTrue();
        assertThat(queue.availableSlots(T0)).isEqualTo(1);
        assertThat(queue.admit(REQUEST_3, T0).admittedSessions()).containsExactly(SESSION_C);
    }

    @Test
    @DisplayName("AC-QUE-007-1/3/4 lease 는 입장 시각부터 시작하고 heartbeat 로 연장되며 만료 시 슬롯을 회수한다")
    void leaseExpiryReleasesSlot() {
        QueueOperations queue = queue(1, AdmissionRateLimit.unlimited(), ReentryPolicy.ALLOW);
        queue.register(SESSION_A, T0);
        Instant admittedAt = T0.plus(Duration.ofMinutes(4));
        queue.admit(REQUEST_1, admittedAt);
        assertThat(queue.find(SESSION_A)).map(WaitingSession::lastSeenAt).contains(admittedAt);

        assertThat(queue.heartbeat(SESSION_A, T0.plus(Duration.ofMinutes(8)))).isTrue();
        assertThat(queue.expireLeases(T0.plus(Duration.ofMinutes(14)))).isEmpty();
        assertThat(queue.availableSlots(T0.plus(Duration.ofMinutes(14)))).isZero();
        assertThat(queue.expireLeases(T0.plus(Duration.ofMinutes(18)))).containsExactly(SESSION_A);
        assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
        assertThat(queue.availableSlots(T0.plus(Duration.ofMinutes(18)))).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-QUE-007-5 입장 선정 전에 만료된 lease 를 먼저 회수한다")
    void admitReclaimsExpiredLease() {
        QueueOperations queue = queue(1, AdmissionRateLimit.unlimited(), ReentryPolicy.ALLOW);
        queue.register(SESSION_A, T0);
        queue.admit(REQUEST_1, T0);
        queue.register(SESSION_B, T0.plus(Duration.ofMinutes(8)));

        assertThat(queue.admit(REQUEST_2, T0.plus(LEASE_TTL)).admittedSessions()).containsExactly(SESSION_B);
        assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.EXPIRED);
    }

    @Test
    @DisplayName("AC-QUE-006-1/3/7 슬라이딩 창 입장률, 재전송은 소비하지 않음")
    void rateLimitSlidingWindow() {
        QueueOperations queue = queue(10, AdmissionRateLimit.perWindow(2, Duration.ofMinutes(1)), ReentryPolicy.ALLOW);
        queue.register(SESSION_A, T0);
        queue.register(SESSION_B, T0);
        queue.register(SESSION_C, T0);
        queue.register(SESSION_D, T0);

        assertThat(queue.admit(REQUEST_1, 1, T0).admittedSessions()).containsExactly(SESSION_A);
        assertThat(queue.admit(REQUEST_1, 1, T0).admittedSessions()).containsExactly(SESSION_A);
        assertThat(queue.admit(REQUEST_2, 1, T0.plusSeconds(30)).admittedSessions()).containsExactly(SESSION_B);
        assertThat(queue.admit(REQUEST_3, T0.plusSeconds(60)).admittedSessions()).containsExactly(SESSION_C);
        assertThat(queue.admit(REQUEST_4, T0.plusSeconds(90)).admittedSessions()).containsExactly(SESSION_D);
    }

    @Test
    @DisplayName("AC-QUE-006-8 취소로 슬롯이 회수되어도 입장률 집계는 남는다")
    void cancelDoesNotRefundRate() {
        QueueOperations queue = queue(5, AdmissionRateLimit.perWindow(1, Duration.ofMinutes(1)), ReentryPolicy.ALLOW);
        queue.register(SESSION_A, T0);
        queue.register(SESSION_B, T0);
        assertThat(queue.admit(REQUEST_1, T0).admittedSessions()).containsExactly(SESSION_A);

        assertThat(queue.cancel(SESSION_A)).isTrue();
        assertThat(queue.availableSlots(T0.plusSeconds(10))).isEqualTo(5);
        assertThat(queue.admit(REQUEST_2, T0.plusSeconds(10)).admittedSessions()).isEmpty();
        assertThat(queue.admit(REQUEST_3, T0.plusSeconds(60)).admittedSessions()).containsExactly(SESSION_B);
    }

    @Test
    @DisplayName("AC-QUE-008-3 종단 상태 세션과 멱등 키 결과에는 보존 TTL 이 걸린다")
    void terminalSessionsAndResultsHaveRetentionTtl() {
        QueueOperations queue = unlimitedQueue();
        RedisKeySpace keys = new RedisKeySpace(SCOPE);
        queue.register(SESSION_A, T0);
        queue.register(SESSION_B, T0);
        queue.admit(REQUEST_1, 1, T0);
        queue.cancel(SESSION_B);
        queue.expireLeases(T0.plus(LEASE_TTL));

        long oneHour = Duration.ofHours(1).toMillis();
        assertThat(connection.commands().pttl(keys.session(SESSION_B))).isBetween(1L, oneHour);
        assertThat(connection.commands().pttl(keys.session(SESSION_A))).isBetween(1L, oneHour);
        assertThat(connection.commands().pttl(keys.admissionResult(REQUEST_1))).isBetween(1L, oneHour);
        // 재진입하면 보존 TTL 이 풀린다(활성 세션은 만료되지 않는다)
        queue.register(SESSION_B, T0);
        assertThat(connection.commands().pttl(keys.session(SESSION_B))).isEqualTo(-1L);
    }

    @Test
    @DisplayName("AC-QUE-008-4 여러 Queue Engine 노드(어댑터 인스턴스)가 같은 대기열 상태를 공유한다")
    void stateIsSharedAcrossAdapterInstances() {
        QueueOperations node1 = unlimitedQueue();
        QueueOperations node2 = unlimitedQueue();
        node1.register(SESSION_A, T0);
        node2.register(SESSION_B, T0);

        assertThat(node2.positionOf(SESSION_A)).hasValue(1);
        assertThat(node2.admit(REQUEST_1, 1, T0).admittedSessions()).containsExactly(SESSION_A);
        assertThat(node1.statusOf(SESSION_A)).contains(WaitingSessionStatus.ADMITTED);
        assertThat(node1.admit(REQUEST_1, 1, T0).admittedSessions()).containsExactly(SESSION_A);
        assertThat(node1.positionOf(SESSION_B)).hasValue(1);
    }

    @Test
    @DisplayName("AC-QUE-008-5 다른 대기열 범위는 서로 영향을 주지 않는다")
    void scopesAreIsolated() {
        QueueOperations main = unlimitedQueue();
        QueueOperations other = new RedisWaitingQueue(new QueueScope(new SiteId("shop"), new QueueId("other")),
            main.policy(), RETENTION, connection.commands());
        main.register(SESSION_A, T0);
        other.register(SESSION_A, T0);

        assertThat(main.cancel(SESSION_A)).isTrue();

        assertThat(other.statusOf(SESSION_A)).contains(WaitingSessionStatus.WAITING);
        assertThat(other.positionOf(SESSION_A)).hasValue(1);
    }

    @Test
    @DisplayName("입력 검증은 메모리 구현과 같다")
    void inputValidation() {
        QueueOperations queue = unlimitedQueue();
        assertThatThrownBy(() -> queue.admit(REQUEST_1, -1, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.register(null, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.heartbeat(SESSION_A, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
