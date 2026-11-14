package com.wbl.openqueue.queue.infrastructure.redis;

import com.wbl.openqueue.queue.domain.AdmissionRateLimit;
import com.wbl.openqueue.queue.domain.AdmissionRequestId;
import com.wbl.openqueue.queue.domain.AdmissionResult;
import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueuePolicy;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.QueueUnavailableException;
import com.wbl.openqueue.queue.domain.RegistrationOutcome;
import com.wbl.openqueue.queue.domain.RegistrationResult;
import com.wbl.openqueue.queue.domain.WaitingSession;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import com.wbl.openqueue.queue.domain.WaitingSessionStatus;
import io.lettuce.core.Range;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * {@link QueueOperations} 의 Redis 구현 (QUE-008). 규칙의 기준은 메모리 구현 {@code WaitingQueue} 이며,
 * 상태를 바꾸는 연산은 모두 Lua 스크립트 한 번으로 수행해 다중 노드·다중 스레드에서도 원자성을 보장한다.
 *
 * <p>key 설계와 스크립트 계약: docs/database/redis-keys.md. 시간 정밀도는 밀리초다.
 *
 * <p>장애 처리(QUE-009): 연결 실패·타임아웃은 {@link QueueUnavailableException} 으로 바꿔 던진다. 어댑터는 자동 재시도하지 않는다.
 * 모든 변경 연산이 멱등(등록·heartbeat·취소는 재실행 안전, 입장은 멱등 키)이므로 재시도 여부는 호출자가 정책에 따라 결정한다(D-QUE-008-5).
 *
 * <p>스레드 안전성: 이 객체는 연결 하나를 감싼다. 연결별로 인스턴스를 만들어 쓴다.
 */
public final class RedisWaitingQueue implements QueueOperations {

    private static final String SESSION_ID_FIELD = "waiting_session_id";
    private static final String NOW_FIELD = "now";

    private final QueueScope scope;
    private final QueuePolicy policy;
    private final RedisRetentionPolicy retention;
    private final RedisCommands<String, String> redis;
    private final RedisKeySpace keys;
    private final RedisQueueScripts scripts;

    /**
     * @param scope     대기열 범위
     * @param policy    대기열 정책(서명된 스냅샷에서 로드된 값, 매 호출에 ARGV 로 전달한다)
     * @param retention Redis 보존 기간
     * @param redis     동기 명령 API
     */
    public RedisWaitingQueue(QueueScope scope, QueuePolicy policy, RedisRetentionPolicy retention, RedisCommands<String, String> redis) {
        this.scope = Objects.requireNonNull(scope, "queue_scope");
        this.policy = Objects.requireNonNull(policy, "queue_policy");
        this.retention = Objects.requireNonNull(retention, "retention");
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keys = new RedisKeySpace(scope);
        this.scripts = new RedisQueueScripts(redis);
    }

    @Override
    public QueueScope scope() {
        return scope;
    }

    @Override
    public QueuePolicy policy() {
        return policy;
    }

    @Override
    public RegistrationResult register(WaitingSessionId sessionId, Instant now) {
        requireNonNull(sessionId, SESSION_ID_FIELD);
        requireNonNull(now, NOW_FIELD);
        String[] scriptKeys = {keys.sequence(), keys.waiting(), keys.waitingSeen(), keys.session(sessionId)};
        String outcome = guard(() -> scripts.execute(LuaScript.REGISTER, ScriptOutputType.VALUE, scriptKeys,
            sessionId.value(), millis(now), policy.reentry().name()));
        return new RegistrationResult(sessionId, RegistrationOutcome.valueOf(outcome));
    }

    @Override
    public boolean heartbeat(WaitingSessionId sessionId, Instant now) {
        requireNonNull(sessionId, SESSION_ID_FIELD);
        requireNonNull(now, NOW_FIELD);
        String[] scriptKeys = {keys.waitingSeen(), keys.admitted(), keys.session(sessionId)};
        Long changed = guard(() -> scripts.execute(LuaScript.HEARTBEAT, ScriptOutputType.INTEGER, scriptKeys,
            sessionId.value(), millis(now)));
        return changed != null && changed == 1L;
    }

    @Override
    public boolean cancel(WaitingSessionId sessionId) {
        requireNonNull(sessionId, SESSION_ID_FIELD);
        String[] scriptKeys = {keys.waiting(), keys.waitingSeen(), keys.admitted(), keys.session(sessionId)};
        Long changed = guard(() -> scripts.execute(LuaScript.CANCEL, ScriptOutputType.INTEGER, scriptKeys,
            sessionId.value(), millis(retention.terminalSession())));
        return changed != null && changed == 1L;
    }

    @Override
    public List<WaitingSessionId> expire(Instant now) {
        requireNonNull(now, NOW_FIELD);
        String[] scriptKeys = {keys.waiting(), keys.waitingSeen()};
        List<Object> expired = guard(() -> scripts.execute(LuaScript.EXPIRE_WAITING, ScriptOutputType.MULTI, scriptKeys,
            millis(now), millis(policy.waitingSessionTtl()), millis(retention.terminalSession()), keys.sessionPrefix()));
        return toSessionIds(expired);
    }

    @Override
    public List<WaitingSessionId> expireLeases(Instant now) {
        requireNonNull(now, NOW_FIELD);
        String[] scriptKeys = {keys.admitted()};
        List<Object> expired = guard(() -> scripts.execute(LuaScript.EXPIRE_LEASES, ScriptOutputType.MULTI, scriptKeys,
            millis(now), millis(policy.admissionLeaseTtl()), millis(retention.terminalSession()), keys.sessionPrefix()));
        return toSessionIds(expired);
    }

    @Override
    public AdmissionResult admit(AdmissionRequestId requestId, Instant now) {
        return admit(requestId, Integer.MAX_VALUE, now);
    }

    @Override
    public AdmissionResult admit(AdmissionRequestId requestId, int requestedSlots, Instant now) {
        requireNonNull(requestId, "admission_request_id");
        requireNonNull(now, NOW_FIELD);
        if (requestedSlots < 0) {
            throw new IllegalArgumentException("available_slots 는 0 이상이어야 합니다.");
        }
        String[] scriptKeys = {keys.waiting(), keys.waitingSeen(), keys.admitted(), keys.admissions(), keys.admissionResult(requestId)};
        String joined = guard(() -> scripts.execute(LuaScript.ADMIT, ScriptOutputType.VALUE, scriptKeys,
            Integer.toString(requestedSlots), millis(now),
            millis(policy.waitingSessionTtl()), millis(policy.admissionLeaseTtl()), Integer.toString(policy.concurrencyLimit()),
            rateMax(), rateWindowMillis(),
            millis(retention.terminalSession()), millis(retention.admissionResult()), keys.sessionPrefix()));
        return new AdmissionResult(requestId, parseJoined(joined));
    }

    @Override
    public Optional<WaitingSession> find(WaitingSessionId sessionId) {
        requireNonNull(sessionId, SESSION_ID_FIELD);
        Map<String, String> hash = guard(() -> redis.hgetall(keys.session(sessionId)));
        if (hash == null || hash.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new WaitingSession(sessionId, WaitingSessionStatus.valueOf(hash.get("status")),
            Instant.ofEpochMilli(Long.parseLong(hash.get("registered_at"))),
            Instant.ofEpochMilli(Long.parseLong(hash.get("last_seen_at")))));
    }

    @Override
    public OptionalInt positionOf(WaitingSessionId sessionId) {
        requireNonNull(sessionId, SESSION_ID_FIELD);
        Long rank = guard(() -> redis.zrank(keys.waiting(), sessionId.value()));
        return rank == null ? OptionalInt.empty() : OptionalInt.of((int) (rank + 1));
    }

    @Override
    public Optional<WaitingSessionStatus> statusOf(WaitingSessionId sessionId) {
        requireNonNull(sessionId, SESSION_ID_FIELD);
        String status = guard(() -> redis.hget(keys.session(sessionId), "status"));
        return status == null ? Optional.empty() : Optional.of(WaitingSessionStatus.valueOf(status));
    }

    @Override
    public int waitingCount() {
        Long count = guard(() -> redis.zcard(keys.waiting()));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public int availableSlots(Instant now) {
        requireNonNull(now, NOW_FIELD);
        // lease 가 아직 유효한 세션만 점유로 계산: last_seen_at > now - lease_ttl (하한 배제). 상태는 바꾸지 않는다(D-QUE-005-2).
        long threshold = now.toEpochMilli() - policy.admissionLeaseTtl().toMillis();
        Range<Long> stillLeased = Range.from(Range.Boundary.excluding(threshold), Range.Boundary.unbounded());
        Long active = guard(() -> redis.zcount(keys.admitted(), stillLeased));
        return Math.max(0, policy.concurrencyLimit() - (active == null ? 0 : active.intValue()));
    }

    private String rateMax() {
        return switch (policy.admissionRate()) {
            case AdmissionRateLimit.Unlimited unlimited -> "0";
            case AdmissionRateLimit.PerWindow perWindow -> Integer.toString(perWindow.maxAdmissions());
        };
    }

    private String rateWindowMillis() {
        return switch (policy.admissionRate()) {
            case AdmissionRateLimit.Unlimited unlimited -> "0";
            case AdmissionRateLimit.PerWindow perWindow -> millis(perWindow.window());
        };
    }

    /** 드라이버의 연결·타임아웃 예외를 도메인 장애 예외로 바꾼다. 스크립트 오류 등 버그성 예외는 그대로 전파한다. */
    private static <T> T guard(Supplier<T> action) {
        try {
            return action.get();
        } catch (RedisConnectionException | RedisCommandTimeoutException e) {
            throw new QueueUnavailableException("Queue Redis 에 접근할 수 없습니다.", e);
        }
    }

    private static List<WaitingSessionId> toSessionIds(List<Object> raw) {
        List<WaitingSessionId> ids = new ArrayList<>(raw == null ? 0 : raw.size());
        if (raw != null) {
            for (Object member : raw) {
                ids.add(new WaitingSessionId(String.valueOf(member)));
            }
        }
        return ids;
    }

    private static List<WaitingSessionId> parseJoined(String joined) {
        if (joined == null || joined.isEmpty()) {
            return List.of();
        }
        List<WaitingSessionId> ids = new ArrayList<>();
        for (String member : joined.split(",")) {
            ids.add(new WaitingSessionId(member));
        }
        return ids;
    }

    private static String millis(Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }

    private static String millis(java.time.Duration duration) {
        return Long.toString(duration.toMillis());
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 값이 없습니다.");
        }
    }
}
