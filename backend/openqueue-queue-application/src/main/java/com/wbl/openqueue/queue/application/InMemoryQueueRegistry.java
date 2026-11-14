package com.wbl.openqueue.queue.application;

import com.wbl.openqueue.queue.domain.FailurePolicy;
import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueueScope;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 고정 목록 기반 {@link QueueRegistry}. 기동 시 설정으로 채워지고 이후 바뀌지 않는다(불변, 스레드 안전).
 */
public final class InMemoryQueueRegistry implements QueueRegistry {

    /** 등록 항목: 대기열 구현과 장애 정책. */
    public record Entry(QueueOperations queue, FailurePolicy failurePolicy) {
        public Entry {
            Objects.requireNonNull(queue, "queue");
            Objects.requireNonNull(failurePolicy, "failure_policy");
        }
    }

    private final Map<QueueScope, Entry> entries;

    /**
     * @param queues 등록할 대기열들. 같은 범위가 두 번 나오면 입력 오류
     */
    public InMemoryQueueRegistry(Collection<Entry> queues) {
        Map<QueueScope, Entry> map = new LinkedHashMap<>();
        for (Entry entry : queues) {
            if (map.put(entry.queue().scope(), entry) != null) {
                throw new IllegalArgumentException("대기열 범위가 중복 등록되었습니다.");
            }
        }
        this.entries = Collections.unmodifiableMap(map);
    }

    @Override
    public Optional<QueueOperations> find(QueueScope scope) {
        Entry entry = entries.get(scope);
        return entry == null ? Optional.empty() : Optional.of(entry.queue());
    }

    @Override
    public FailurePolicy failurePolicyOf(QueueScope scope) {
        Entry entry = entries.get(scope);
        return entry == null ? FailurePolicy.DEFAULT : entry.failurePolicy();
    }

    @Override
    public Collection<QueueScope> scopes() {
        return entries.keySet();
    }
}
