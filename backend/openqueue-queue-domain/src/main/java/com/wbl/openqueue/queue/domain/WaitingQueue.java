package com.wbl.openqueue.queue.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 하나의 대기열 범위({@link QueueScope}) 안에서 FIFO 대기 순서, 입장 후보 선정, 세션 생명주기를 담당하는 도메인 모델.
 *
 * <p>요구사항: QUE-001(FIFO·멱등), QUE-003(생성·조회·취소), QUE-004(만료·재접속) — docs/requirements/SRS.md §3
 *
 * <p>보호하는 불변조건:
 * <ol>
 *   <li>대기 순서는 최초 등록 순서이며, 재등록·heartbeat 로 순서가 바뀌지 않는다.</li>
 *   <li>한 세션은 정확히 하나의 상태를 가지며, WAITING 세션만 대기 순서에 존재한다.</li>
 *   <li>ADMITTED 세션은 다시 WAITING 이 될 수 없다. 종단 상태(EXPIRED/CANCELLED)의 재등록은 정책에 따른다.</li>
 *   <li>같은 멱등 키의 입장 요청은 최초 1회만 상태를 변경하고, 이후에는 최초 결과를 반환한다.</li>
 *   <li>조회 연산은 상태를 바꾸지 않는다. 만료 전이는 {@link #expire} 와 {@link #admit}(선정 직전)에서만 일어난다.</li>
 * </ol>
 *
 * <p>시간: 도메인은 시계를 갖지 않고 호출자가 {@code now} 를 넘긴다(D-QUE-004-1).
 *
 * <p>동시성 주의: 이 클래스는 스레드 안전하지 않다. 다중 스레드·다중 노드 환경의 원자성은
 * Phase 2 의 Redis Lua Script(QUE-008)가 보장한다. 이 클래스를 여러 스레드에서 공유하지 않는다.
 *
 * <p>저장 위치: 메모리. MariaDB 에 저장하지 않는다(DMZ 배치, NET-001).
 * 종단 상태 세션도 메모리에 남긴다(D-QUE-004-4). 보관 기간은 Phase 2 Redis TTL 로 정의한다.
 */
public final class WaitingQueue {

    private final QueueScope scope;
    private final QueuePolicy policy;

    /** 알려진 모든 세션(종단 상태 포함). 상태 변경 시 불변 레코드를 교체한다. */
    private final Map<WaitingSessionId, WaitingSession> sessions = new HashMap<>();

    /** 대기 순서. WAITING 세션만 포함하며 LinkedHashSet 으로 삽입 순서(FIFO)와 중복 방지를 보장한다. */
    private final LinkedHashSet<WaitingSessionId> waitingOrder = new LinkedHashSet<>();

    /**
     * 처리된 멱등 키별 결과. 재전송 시 상태 변경 없이 그대로 반환한다.
     * 보관 기간 제한은 도메인 범위 밖이다(D-QUE-001-2).
     */
    private final Map<AdmissionRequestId, AdmissionResult> processedAdmissions = new HashMap<>();

    /**
     * 빈 대기열을 생성한다.
     *
     * @param scope  대기열 범위(site_id + queue_id)
     * @param policy 대기열 정책(TTL, 재진입)
     * @throws IllegalArgumentException 인자가 null 인 경우
     */
    public WaitingQueue(QueueScope scope, QueuePolicy policy) {
        this.scope = IdentifierRules.requireNonNull(scope, "queue_scope");
        this.policy = IdentifierRules.requireNonNull(policy, "queue_policy");
    }

    /** 이 대기열의 범위를 반환한다. */
    public QueueScope scope() {
        return scope;
    }

    /** 이 대기열의 정책을 반환한다. */
    public QueuePolicy policy() {
        return policy;
    }

    /**
     * 세션을 대기 순서 맨 뒤에 등록하거나, 이미 있는 세션의 재접속을 처리한다.
     *
     * <ul>
     *   <li>미등록 → WAITING 으로 맨 뒤에 추가 ({@code REGISTERED})</li>
     *   <li>WAITING → 순번 유지, 활동 시각만 갱신 ({@code ALREADY_WAITING}, AC-QUE-001-2/AC-QUE-004-4)</li>
     *   <li>ADMITTED → 변경 없음 ({@code ALREADY_ADMITTED}, AC-QUE-001-3)</li>
     *   <li>EXPIRED/CANCELLED → 재진입 정책: ALLOW 면 새 세션으로 맨 뒤에, DENY 면 거부 (AC-QUE-004-5)</li>
     * </ul>
     *
     * @param sessionId 등록할 세션
     * @param now       현재 시각
     * @return 등록 결과
     * @throws IllegalArgumentException 인자가 null 인 경우
     */
    public RegistrationResult register(WaitingSessionId sessionId, Instant now) {
        IdentifierRules.requireNonNull(sessionId, "waiting_session_id");
        IdentifierRules.requireNonNull(now, "now");

        WaitingSession existing = sessions.get(sessionId);
        if (existing == null) {
            append(sessionId, now);
            return new RegistrationResult(sessionId, RegistrationOutcome.REGISTERED);
        }
        return switch (existing.status()) {
            case WAITING -> {
                sessions.put(sessionId, existing.withLastSeenAt(now));
                yield new RegistrationResult(sessionId, RegistrationOutcome.ALREADY_WAITING);
            }
            case ADMITTED -> new RegistrationResult(sessionId, RegistrationOutcome.ALREADY_ADMITTED);
            case EXPIRED, CANCELLED -> reenter(sessionId, now);
        };
    }

    /** 종단 상태 세션의 재진입. 정책이 DENY 면 기존 상태를 그대로 둔다. */
    private RegistrationResult reenter(WaitingSessionId sessionId, Instant now) {
        if (policy.reentry() == ReentryPolicy.DENY) {
            return new RegistrationResult(sessionId, RegistrationOutcome.REENTRY_DENIED);
        }
        append(sessionId, now);
        return new RegistrationResult(sessionId, RegistrationOutcome.REGISTERED);
    }

    private void append(WaitingSessionId sessionId, Instant now) {
        sessions.put(sessionId, new WaitingSession(sessionId, WaitingSessionStatus.WAITING, now, now));
        waitingOrder.add(sessionId);
    }

    /**
     * 활동 신호를 기록해 만료 시각을 연장한다.
     *
     * @param sessionId 세션
     * @param now       현재 시각
     * @return WAITING/ADMITTED 세션이면 true, 종단 상태·미등록이면 false(변경 없음)
     * @throws IllegalArgumentException 인자가 null 인 경우
     */
    public boolean heartbeat(WaitingSessionId sessionId, Instant now) {
        IdentifierRules.requireNonNull(sessionId, "waiting_session_id");
        IdentifierRules.requireNonNull(now, "now");
        WaitingSession session = sessions.get(sessionId);
        if (session == null || session.status().isTerminal()) {
            return false;
        }
        sessions.put(sessionId, session.withLastSeenAt(now));
        return true;
    }

    /**
     * 세션을 취소한다. WAITING 은 대기 순서에서 제거되고, ADMITTED 는 입장 포기로 처리된다.
     *
     * @param sessionId 세션
     * @return 상태가 CANCELLED 로 바뀌었으면 true, 종단 상태·미등록이면 false(멱등)
     * @throws IllegalArgumentException sessionId 가 null 인 경우
     */
    public boolean cancel(WaitingSessionId sessionId) {
        IdentifierRules.requireNonNull(sessionId, "waiting_session_id");
        WaitingSession session = sessions.get(sessionId);
        if (session == null || session.status().isTerminal()) {
            return false;
        }
        waitingOrder.remove(sessionId);
        sessions.put(sessionId, session.withStatus(WaitingSessionStatus.CANCELLED));
        return true;
    }

    /**
     * 대기 TTL 이 지난 WAITING 세션을 만료 처리한다 ({@code last_seen_at + ttl <= now}).
     *
     * @param now 현재 시각
     * @return 만료된 세션(대기 순서대로)
     * @throws IllegalArgumentException now 가 null 인 경우
     */
    public List<WaitingSessionId> expire(Instant now) {
        IdentifierRules.requireNonNull(now, "now");
        List<WaitingSessionId> expired = new ArrayList<>();
        Iterator<WaitingSessionId> iterator = waitingOrder.iterator();
        while (iterator.hasNext()) {
            WaitingSessionId id = iterator.next();
            WaitingSession session = sessions.get(id);
            // 활동 순서와 등록 순서가 다르므로 앞이 살아 있어도 뒤를 계속 검사한다.
            if (!session.lastSeenAt().plus(policy.waitingSessionTtl()).isAfter(now)) {
                iterator.remove();
                sessions.put(id, session.withStatus(WaitingSessionStatus.EXPIRED));
                expired.add(id);
            }
        }
        return expired;
    }

    /**
     * 만료 세션을 먼저 정리한 뒤, 대기 순서 앞에서부터 최대 {@code availableSlots} 명을 입장 후보로 선정한다.
     *
     * <p>처리 순서:
     * <ol>
     *   <li>입력 검증 (오류 시 상태 불변)</li>
     *   <li>멱등 키가 이미 처리되었으면 최초 결과를 그대로 반환한다(D-QUE-001-1)</li>
     *   <li>{@link #expire} 로 죽은 세션을 배제한다(AC-QUE-004-6)</li>
     *   <li>대기 순서 앞에서부터 선정하여 ADMITTED 로 전이하고 대기 순서에서 제거한다</li>
     *   <li>결과를 멱등 키에 기록한다</li>
     * </ol>
     *
     * @param requestId      입장 요청 멱등 키
     * @param availableSlots 이번에 입장 가능한 인원(0 이상)
     * @param now            현재 시각
     * @return 선정 결과(대기 순서 그대로의 세션 목록)
     * @throws IllegalArgumentException 인자가 null 이거나 availableSlots 가 음수인 경우
     */
    public AdmissionResult admit(AdmissionRequestId requestId, int availableSlots, Instant now) {
        IdentifierRules.requireNonNull(requestId, "admission_request_id");
        IdentifierRules.requireNonNull(now, "now");
        if (availableSlots < 0) {
            throw new IllegalArgumentException("available_slots 는 0 이상이어야 합니다.");
        }

        AdmissionResult previous = processedAdmissions.get(requestId);
        if (previous != null) {
            // 재전송된 요청: 새로운 선정을 하지 않아야 중복 입장이 발생하지 않는다.
            return previous;
        }

        expire(now);
        List<WaitingSessionId> selected = takeFromHead(availableSlots);
        for (WaitingSessionId id : selected) {
            sessions.put(id, sessions.get(id).withStatus(WaitingSessionStatus.ADMITTED));
        }

        AdmissionResult result = new AdmissionResult(requestId, selected);
        processedAdmissions.put(requestId, result);
        return result;
    }

    /**
     * 세션 정보를 조회한다. 상태를 바꾸지 않는다.
     *
     * @param sessionId 세션
     * @return 세션 뷰, 미등록이면 빈 값
     */
    public Optional<WaitingSession> find(WaitingSessionId sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * 대기 중인 세션의 현재 순번(1부터 시작)을 반환한다.
     *
     * @param sessionId 조회할 세션
     * @return 대기 중이면 순번, 그 외(미등록·입장·종단)는 빈 값
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
     * @return 상태, 미등록 세션이면 빈 값
     */
    public Optional<WaitingSessionStatus> statusOf(WaitingSessionId sessionId) {
        return find(sessionId).map(WaitingSession::status);
    }

    /** 현재 대기 중인 세션 수(WAITING 만). */
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
