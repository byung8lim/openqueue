package com.wbl.openqueue.queue.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 하나의 대기열 범위({@link QueueScope}) 안에서 FIFO 대기 순서와 입장 후보 선정 규칙을 담당하는 도메인 모델.
 *
 * <p>요구사항: QUE-001 (docs/requirements/SRS.md §3)
 *
 * <p>보호하는 불변조건:
 * <ol>
 *   <li>대기 순서는 최초 등록 순서이며, 재등록으로 순서가 바뀌지 않는다.</li>
 *   <li>한 세션은 WAITING 과 ADMITTED 중 하나에만 속한다. ADMITTED 세션은 다시 WAITING 이 될 수 없다.</li>
 *   <li>같은 멱등 키의 입장 요청은 최초 1회만 상태를 변경하고, 이후에는 최초 결과를 반환한다.</li>
 * </ol>
 *
 * <p>동시성 주의: 이 클래스는 스레드 안전하지 않다. 업무 규칙을 명확히 표현하기 위한 메모리 모델이며,
 * 다중 스레드·다중 노드 환경의 원자성은 Phase 2 의 Redis Lua Script(QUE-008)가 보장한다.
 * 이 클래스를 여러 스레드에서 공유하지 않는다.
 *
 * <p>저장 위치: 메모리. MariaDB 에 저장하지 않는다(DMZ 배치, NET-001).
 */
public final class WaitingQueue {

    private final QueueScope scope;

    /** 대기 순서. LinkedHashSet 으로 삽입 순서(FIFO)와 중복 방지를 동시에 보장한다. */
    private final LinkedHashSet<WaitingSessionId> waitingOrder = new LinkedHashSet<>();

    /** 입장 완료 세션. 재등록 시 대기열 재진입을 막기 위해 보관한다. */
    private final Set<WaitingSessionId> admittedSessions = new HashSet<>();

    /**
     * 처리된 멱등 키별 결과. 재전송 시 상태 변경 없이 그대로 반환한다.
     * 보관 기간 제한은 도메인 범위 밖이다(D-QUE-001-2, Redis TTL 로 정의 예정).
     */
    private final Map<AdmissionRequestId, AdmissionResult> processedAdmissions = new HashMap<>();

    /**
     * 빈 대기열을 생성한다.
     *
     * @param scope 대기열 범위(site_id + queue_id)
     * @throws IllegalArgumentException scope 가 null 인 경우
     */
    public WaitingQueue(QueueScope scope) {
        this.scope = IdentifierRules.requireNonNull(scope, "queue_scope");
    }

    /**
     * 이 대기열의 범위를 반환한다.
     *
     * @return site_id + queue_id 범위
     */
    public QueueScope scope() {
        return scope;
    }

    /**
     * 세션을 대기 순서 맨 뒤에 등록한다.
     *
     * <p>멱등 동작: 이미 대기 중이거나 이미 입장한 세션이면 아무것도 바꾸지 않고
     * {@code newlyRegistered=false} 를 반환한다(AC-QUE-001-2, AC-QUE-001-3).
     * 새로고침·재시도로 순번이 뒤로 밀리거나 입장 세션이 다시 줄을 서는 일을 막는다.
     *
     * @param sessionId 등록할 세션
     * @return 등록 결과
     * @throws IllegalArgumentException sessionId 가 null 인 경우
     */
    public RegistrationResult register(WaitingSessionId sessionId) {
        IdentifierRules.requireNonNull(sessionId, "waiting_session_id");
        if (admittedSessions.contains(sessionId)) {
            return new RegistrationResult(sessionId, false);
        }
        boolean added = waitingOrder.add(sessionId);
        return new RegistrationResult(sessionId, added);
    }

    /**
     * 대기 순서 앞에서부터 최대 {@code availableSlots} 명을 입장 후보로 선정한다.
     *
     * <p>처리 순서:
     * <ol>
     *   <li>멱등 키가 이미 처리되었으면 최초 결과를 그대로 반환한다(상태 변경 없음, D-QUE-001-1).</li>
     *   <li>대기 순서 앞에서부터 선정하여 ADMITTED 로 전이하고 대기 순서에서 제거한다.</li>
     *   <li>결과를 멱등 키에 기록한다.</li>
     * </ol>
     *
     * <p>입력 검증은 상태 변경 이전에 수행하여, 오류 시 대기열 상태가 절대 바뀌지 않도록 한다.
     *
     * @param requestId      입장 요청 멱등 키
     * @param availableSlots 이번에 입장 가능한 인원(0 이상)
     * @return 선정 결과(대기 순서 그대로의 세션 목록)
     * @throws IllegalArgumentException requestId 가 null 이거나 availableSlots 가 음수인 경우
     */
    public AdmissionResult admit(AdmissionRequestId requestId, int availableSlots) {
        IdentifierRules.requireNonNull(requestId, "admission_request_id");
        if (availableSlots < 0) {
            throw new IllegalArgumentException("available_slots 는 0 이상이어야 합니다.");
        }

        AdmissionResult previous = processedAdmissions.get(requestId);
        if (previous != null) {
            // 재전송된 요청: 새로운 선정을 하지 않아야 중복 입장이 발생하지 않는다.
            return previous;
        }

        List<WaitingSessionId> selected = takeFromHead(availableSlots);
        admittedSessions.addAll(selected);

        AdmissionResult result = new AdmissionResult(requestId, selected);
        processedAdmissions.put(requestId, result);
        return result;
    }

    /**
     * 대기 중인 세션의 현재 순번(1부터 시작)을 반환한다.
     *
     * @param sessionId 조회할 세션
     * @return 대기 중이면 순번, 미등록이거나 이미 입장한 세션이면 빈 값
     */
    public OptionalInt positionOf(WaitingSessionId sessionId) {
        int position = 1;
        for (WaitingSessionId waiting : waitingOrder) {
            if (waiting.equals(sessionId)) {
                return OptionalInt.of(position);
            }
            position++;
        }
        return OptionalInt.empty();
    }

    /**
     * 세션의 현재 상태를 반환한다.
     *
     * @param sessionId 조회할 세션
     * @return WAITING/ADMITTED, 미등록 세션이면 빈 값
     */
    public Optional<WaitingSessionStatus> statusOf(WaitingSessionId sessionId) {
        if (admittedSessions.contains(sessionId)) {
            return Optional.of(WaitingSessionStatus.ADMITTED);
        }
        if (waitingOrder.contains(sessionId)) {
            return Optional.of(WaitingSessionStatus.WAITING);
        }
        return Optional.empty();
    }

    /**
     * 현재 대기 중인 세션 수를 반환한다.
     *
     * @return 대기 인원(입장 완료 세션 제외)
     */
    public int waitingCount() {
        return waitingOrder.size();
    }

    /** 대기 순서 앞에서부터 최대 count 개를 꺼내 대기 순서에서 제거한다. */
    private List<WaitingSessionId> takeFromHead(int count) {
        List<WaitingSessionId> taken = new ArrayList<>(Math.min(count, waitingOrder.size()));
        Iterator<WaitingSessionId> iterator = waitingOrder.iterator();
        while (taken.size() < count && iterator.hasNext()) {
            taken.add(iterator.next());
            iterator.remove();
        }
        return taken;
    }
}
