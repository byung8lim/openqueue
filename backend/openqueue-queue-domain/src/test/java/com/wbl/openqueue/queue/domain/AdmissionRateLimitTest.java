package com.wbl.openqueue.queue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QUE-006 초당/분당 입장률 제한 단위 테스트.
 *
 * <p>각 테스트는 docs/requirements/SRS.md 의 AC-QUE-006-x 와 1:1 대응한다.
 */
@DisplayName("QUE-006 입장률 제한")
class AdmissionRateLimitTest {

    private static final WaitingSessionId SESSION_A = new WaitingSessionId("sessionA-0000000001");
    private static final WaitingSessionId SESSION_B = new WaitingSessionId("sessionB-0000000002");
    private static final WaitingSessionId SESSION_C = new WaitingSessionId("sessionC-0000000003");
    private static final WaitingSessionId SESSION_D = new WaitingSessionId("sessionD-0000000004");
    private static final WaitingSessionId SESSION_E = new WaitingSessionId("sessionE-0000000005");
    private static final AdmissionRequestId REQUEST_1 = new AdmissionRequestId("request-0001");
    private static final AdmissionRequestId REQUEST_2 = new AdmissionRequestId("request-0002");
    private static final AdmissionRequestId REQUEST_3 = new AdmissionRequestId("request-0003");
    private static final AdmissionRequestId REQUEST_4 = new AdmissionRequestId("request-0004");
    private static final Instant T0 = Instant.parse("2026-11-14T00:00:00Z");
    private static final Duration WAITING_TTL = Duration.ofMinutes(5);
    private static final Duration LEASE_TTL = Duration.ofMinutes(10);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static WaitingQueue newQueue(int concurrencyLimit, AdmissionRateLimit rate) {
        return new WaitingQueue(new QueueScope(new SiteId("shop"), new QueueId("main")),
            new QueuePolicy(WAITING_TTL, ReentryPolicy.ALLOW, concurrencyLimit, LEASE_TTL, rate));
    }

    private static void registerAll(WaitingQueue queue, Instant now, WaitingSessionId... sessions) {
        for (WaitingSessionId session : sessions) {
            queue.register(session, now);
        }
    }

    @Test
    @DisplayName("AC-QUE-006-1 창 안에서는 최대 건수까지만 입장한다")
    void admitsUpToRateWithinWindow() {
        WaitingQueue queue = newQueue(10, AdmissionRateLimit.perWindow(2, WINDOW));
        registerAll(queue, T0, SESSION_A, SESSION_B, SESSION_C, SESSION_D, SESSION_E);

        assertThat(queue.admit(REQUEST_1, T0).admittedSessions()).containsExactly(SESSION_A, SESSION_B);
        assertThat(queue.admit(REQUEST_2, T0.plusSeconds(30)).admittedSessions()).isEmpty();
        assertThat(queue.waitingCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-QUE-006-2 창 경계: t + window <= now 이면 창 밖이다")
    void windowBoundary() {
        WaitingQueue queue = newQueue(10, AdmissionRateLimit.perWindow(2, WINDOW));
        registerAll(queue, T0, SESSION_A, SESSION_B, SESSION_C, SESSION_D, SESSION_E);
        queue.admit(REQUEST_1, T0);

        assertThat(queue.admit(REQUEST_2, T0.plus(WINDOW).minusNanos(1)).admittedSessions()).isEmpty();
        assertThat(queue.admit(REQUEST_3, T0.plus(WINDOW)).admittedSessions()).containsExactly(SESSION_C, SESSION_D);
    }

    @Test
    @DisplayName("AC-QUE-006-3 슬라이딩 창: 오래된 입장이 빠지는 만큼만 새로 허용된다")
    void slidingWindow() {
        WaitingQueue queue = newQueue(10, AdmissionRateLimit.perWindow(2, WINDOW));
        registerAll(queue, T0, SESSION_A, SESSION_B, SESSION_C, SESSION_D);

        assertThat(queue.admit(REQUEST_1, 1, T0).admittedSessions()).containsExactly(SESSION_A);
        assertThat(queue.admit(REQUEST_2, 1, T0.plusSeconds(30)).admittedSessions()).containsExactly(SESSION_B);
        assertThat(queue.admit(REQUEST_3, T0.plusSeconds(60)).admittedSessions()).containsExactly(SESSION_C);
        assertThat(queue.admit(REQUEST_4, T0.plusSeconds(90)).admittedSessions()).containsExactly(SESSION_D);
    }

    @Test
    @DisplayName("AC-QUE-006-4 동시 입장 상한과 입장률 중 작은 값이 적용된다")
    void combinesWithConcurrencyLimitByMinimum() {
        WaitingQueue slotBound = newQueue(1, AdmissionRateLimit.perWindow(5, WINDOW));
        registerAll(slotBound, T0, SESSION_A, SESSION_B, SESSION_C);
        assertThat(slotBound.admit(REQUEST_1, T0).admittedSessions()).containsExactly(SESSION_A);

        WaitingQueue rateBound = newQueue(5, AdmissionRateLimit.perWindow(1, WINDOW));
        registerAll(rateBound, T0, SESSION_A, SESSION_B, SESSION_C);
        assertThat(rateBound.admit(REQUEST_1, T0).admittedSessions()).containsExactly(SESSION_A);
    }

    @Test
    @DisplayName("AC-QUE-006-5 호출자가 명시한 인원도 입장률로 제한된다")
    void explicitSlotsAreCappedByRate() {
        WaitingQueue queue = newQueue(10, AdmissionRateLimit.perWindow(2, WINDOW));
        registerAll(queue, T0, SESSION_A, SESSION_B, SESSION_C, SESSION_D, SESSION_E);

        assertThat(queue.admit(REQUEST_1, 5, T0).admittedSessions()).containsExactly(SESSION_A, SESSION_B);
    }

    @Test
    @DisplayName("AC-QUE-006-6 무제한이면 입장률이 선정을 막지 않는다")
    void unlimitedDoesNotRestrict() {
        WaitingQueue queue = newQueue(10, AdmissionRateLimit.unlimited());
        registerAll(queue, T0, SESSION_A, SESSION_B, SESSION_C, SESSION_D, SESSION_E);

        assertThat(queue.admit(REQUEST_1, 3, T0).admittedSessions()).containsExactly(SESSION_A, SESSION_B, SESSION_C);
        assertThat(queue.admit(REQUEST_2, T0).admittedSessions()).containsExactly(SESSION_D, SESSION_E);
    }

    @Test
    @DisplayName("AC-QUE-006-7 재전송은 입장률을 소비하지 않는다")
    void resendDoesNotConsumeRate() {
        WaitingQueue queue = newQueue(10, AdmissionRateLimit.perWindow(2, WINDOW));
        registerAll(queue, T0, SESSION_A, SESSION_B, SESSION_C, SESSION_D);

        AdmissionResult first = queue.admit(REQUEST_1, T0);
        assertThat(queue.admit(REQUEST_1, T0)).isEqualTo(first);
        assertThat(queue.admit(REQUEST_2, T0.plus(WINDOW)).admittedSessions()).containsExactly(SESSION_C, SESSION_D);
    }

    @Test
    @DisplayName("AC-QUE-006-8 취소로 슬롯이 회수되어도 입장률 집계는 남는다")
    void cancelDoesNotRefundRate() {
        WaitingQueue queue = newQueue(5, AdmissionRateLimit.perWindow(1, WINDOW));
        registerAll(queue, T0, SESSION_A, SESSION_B);
        assertThat(queue.admit(REQUEST_1, T0).admittedSessions()).containsExactly(SESSION_A);
        Instant shortlyAfter = T0.plusSeconds(10);

        assertThat(queue.cancel(SESSION_A)).isTrue();

        assertThat(queue.availableSlots(shortlyAfter)).isEqualTo(5);
        assertThat(queue.admit(REQUEST_2, shortlyAfter).admittedSessions()).isEmpty();
        assertThat(queue.admit(REQUEST_3, T0.plus(WINDOW)).admittedSessions()).containsExactly(SESSION_B);
    }

    @Test
    @DisplayName("AC-QUE-006-9 정책 검증: 입장률 null, 건수 0·음수, 창 null·0·음수는 입력 오류")
    void policyValidation() {
        assertThatThrownBy(() -> new QueuePolicy(WAITING_TTL, ReentryPolicy.ALLOW, 1, LEASE_TTL, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdmissionRateLimit.perWindow(0, WINDOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdmissionRateLimit.perWindow(-1, WINDOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdmissionRateLimit.perWindow(1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdmissionRateLimit.perWindow(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdmissionRateLimit.perWindow(1, Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
