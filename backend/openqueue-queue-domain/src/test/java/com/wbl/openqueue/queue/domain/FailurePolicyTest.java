package com.wbl.openqueue.queue.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** QUE-009 (DEC-09): 장애 정책 기본값은 Fail-closed 다. */
@DisplayName("QUE-009 FailurePolicy")
class FailurePolicyTest {

    @Test
    @DisplayName("AC-QUE-009-2 사이트 정책이 없으면 FAIL_CLOSED 가 적용된다")
    void defaultIsFailClosed() {
        assertThat(FailurePolicy.DEFAULT).isEqualTo(FailurePolicy.FAIL_CLOSED);
        assertThat(FailurePolicy.values()).containsExactly(FailurePolicy.FAIL_CLOSED, FailurePolicy.FAIL_OPEN);
    }

    @Test
    @DisplayName("WaitingQueue 는 QueueOperations 포트를 구현한다(Redis 구현과 같은 계약)")
    void inMemoryQueueImplementsPort() {
        assertThat(QueueOperations.class).isAssignableFrom(WaitingQueue.class);
    }
}
