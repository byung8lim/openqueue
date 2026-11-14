package com.wbl.openqueue.queue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * QUE-001 FIFO 입장 후보 선정 및 재전송 멱등성 단위 테스트.
 *
 * <p>각 테스트는 docs/requirements/SRS.md 의 수용 기준(AC-QUE-001-x)과 1:1로 대응한다.
 */
@DisplayName("QUE-001 WaitingQueue")
class WaitingQueueTest {

    private static final WaitingSessionId SESSION_A = new WaitingSessionId("sessionA-0000000001");
    private static final WaitingSessionId SESSION_B = new WaitingSessionId("sessionB-0000000002");
    private static final WaitingSessionId SESSION_C = new WaitingSessionId("sessionC-0000000003");

    private WaitingQueue queue;

    @BeforeEach
    void setUp() {
        queue = new WaitingQueue(new QueueScope(new SiteId("shop"), new QueueId("main")));
    }

    @Nested
    @DisplayName("AC-QUE-001-1 FIFO 선정과 재전송 멱등성")
    class FifoAndIdempotency {

        @Test
        @DisplayName("입장 가능 인원 1명이면 먼저 등록된 A만 선정되고 B는 1번 순번을 유지한다")
        void selectsFirstRegisteredSessionOnly() {
            queue.register(SESSION_A);
            queue.register(SESSION_B);

            AdmissionResult result = queue.admit(new AdmissionRequestId("request-0001"), 1);

            assertThat(result.admittedSessions()).containsExactly(SESSION_A);
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.ADMITTED);
            assertThat(queue.statusOf(SESSION_B)).contains(WaitingSessionStatus.WAITING);
            assertThat(queue.positionOf(SESSION_B)).hasValue(1);
        }

        @Test
        @DisplayName("같은 입장 요청이 재전송되면 최초 결과를 반환하고 추가 입장이 발생하지 않는다")
        void resentAdmissionRequestReturnsSameResultWithoutSideEffect() {
            queue.register(SESSION_A);
            queue.register(SESSION_B);
            AdmissionRequestId requestId = new AdmissionRequestId("request-0001");

            AdmissionResult first = queue.admit(requestId, 1);
            AdmissionResult resent = queue.admit(requestId, 1);

            assertThat(resent).isEqualTo(first);
            assertThat(resent.admittedSessions()).containsExactly(SESSION_A);
            assertThat(queue.statusOf(SESSION_B)).contains(WaitingSessionStatus.WAITING);
            assertThat(queue.waitingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("D-QUE-001-1: 재전송 시 available_slots 값이 달라도 최초 결과를 유지한다")
        void resentRequestWithDifferentSlotsKeepsFirstResult() {
            queue.register(SESSION_A);
            queue.register(SESSION_B);
            AdmissionRequestId requestId = new AdmissionRequestId("request-0001");

            queue.admit(requestId, 1);
            AdmissionResult resent = queue.admit(requestId, 5);

            assertThat(resent.admittedSessions()).containsExactly(SESSION_A);
            assertThat(queue.statusOf(SESSION_B)).contains(WaitingSessionStatus.WAITING);
        }

        @Test
        @DisplayName("여러 명이 입장 가능하면 등록 순서 그대로 선정된다")
        void selectsInRegistrationOrder() {
            queue.register(SESSION_C);
            queue.register(SESSION_A);
            queue.register(SESSION_B);

            AdmissionResult result = queue.admit(new AdmissionRequestId("request-0001"), 2);

            assertThat(result.admittedSessions()).containsExactly(SESSION_C, SESSION_A);
            assertThat(queue.positionOf(SESSION_B)).hasValue(1);
        }
    }

    @Nested
    @DisplayName("AC-QUE-001-2/3 중복 등록")
    class DuplicateRegistration {

        @Test
        @DisplayName("대기 중인 세션의 재등록은 새로 추가되지 않고 기존 순번을 유지한다")
        void reRegistrationKeepsOriginalPosition() {
            queue.register(SESSION_A);
            queue.register(SESSION_B);

            RegistrationResult again = queue.register(SESSION_A);

            assertThat(again.newlyRegistered()).isFalse();
            assertThat(queue.positionOf(SESSION_A)).hasValue(1);
            assertThat(queue.positionOf(SESSION_B)).hasValue(2);
            assertThat(queue.waitingCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("최초 등록은 newlyRegistered=true 를 반환하고 맨 뒤 순번을 받는다")
        void firstRegistrationIsAppended() {
            RegistrationResult first = queue.register(SESSION_A);
            RegistrationResult second = queue.register(SESSION_B);

            assertThat(first.newlyRegistered()).isTrue();
            assertThat(second.newlyRegistered()).isTrue();
            assertThat(queue.positionOf(SESSION_B)).hasValue(2);
        }

        @Test
        @DisplayName("이미 입장한 세션은 재등록해도 대기열에 다시 추가되지 않는다")
        void admittedSessionIsNotQueuedAgain() {
            queue.register(SESSION_A);
            queue.admit(new AdmissionRequestId("request-0001"), 1);

            RegistrationResult again = queue.register(SESSION_A);

            assertThat(again.newlyRegistered()).isFalse();
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.ADMITTED);
            assertThat(queue.positionOf(SESSION_A)).isEmpty();
            assertThat(queue.waitingCount()).isZero();
        }
    }

    @Nested
    @DisplayName("AC-QUE-001-4/5 경계값과 연속 입장")
    class Boundaries {

        @Test
        @DisplayName("입장 가능 인원이 0이면 아무도 선정되지 않는다")
        void zeroSlotsAdmitsNobody() {
            queue.register(SESSION_A);
            queue.register(SESSION_B);

            AdmissionResult result = queue.admit(new AdmissionRequestId("request-0001"), 0);

            assertThat(result.admittedSessions()).isEmpty();
            assertThat(queue.waitingCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("입장 가능 인원이 대기 인원보다 많으면 대기자 전원만 선정된다")
        void slotsExceedingWaitingCountAdmitsAll() {
            queue.register(SESSION_A);
            queue.register(SESSION_B);

            AdmissionResult result = queue.admit(new AdmissionRequestId("request-0001"), 5);

            assertThat(result.admittedSessions()).containsExactly(SESSION_A, SESSION_B);
            assertThat(queue.waitingCount()).isZero();
        }

        @Test
        @DisplayName("빈 대기열에서 입장 요청 시 빈 결과를 반환한다")
        void emptyQueueAdmitsNobody() {
            AdmissionResult result = queue.admit(new AdmissionRequestId("request-0001"), 3);

            assertThat(result.admittedSessions()).isEmpty();
        }

        @Test
        @DisplayName("입장 가능 인원이 음수이면 입력 오류가 발생하고 상태는 변하지 않는다")
        void negativeSlotsIsRejected() {
            queue.register(SESSION_A);

            assertThatThrownBy(() -> queue.admit(new AdmissionRequestId("request-0001"), -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(queue.statusOf(SESSION_A)).contains(WaitingSessionStatus.WAITING);
        }

        @Test
        @DisplayName("새로운 멱등 키의 입장 요청은 다음 대기자를 선정한다")
        void newRequestIdAdmitsNextSession() {
            queue.register(SESSION_A);
            queue.register(SESSION_B);
            queue.admit(new AdmissionRequestId("request-0001"), 1);

            AdmissionResult second = queue.admit(new AdmissionRequestId("request-0002"), 1);

            assertThat(second.admittedSessions()).containsExactly(SESSION_B);
        }

        @Test
        @DisplayName("등록되지 않은 세션은 순번과 상태가 비어 있다")
        void unknownSessionHasNoPositionOrStatus() {
            assertThat(queue.positionOf(SESSION_A)).isEmpty();
            assertThat(queue.statusOf(SESSION_A)).isEmpty();
        }

        @Test
        @DisplayName("null 입력은 입력 오류로 거부한다")
        void nullInputsAreRejected() {
            assertThatThrownBy(() -> queue.register(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.admit(null, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new WaitingQueue(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("입장 결과 목록은 외부에서 변경할 수 없다")
    void admissionResultIsImmutable() {
        queue.register(SESSION_A);
        AdmissionResult result = queue.admit(new AdmissionRequestId("request-0001"), 1);

        assertThatThrownBy(() -> result.admittedSessions().add(SESSION_B))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("대기열은 자신의 site/queue 범위를 노출한다")
    void exposesScope() {
        assertThat(queue.scope()).isEqualTo(new QueueScope(new SiteId("shop"), new QueueId("main")));
    }
}
