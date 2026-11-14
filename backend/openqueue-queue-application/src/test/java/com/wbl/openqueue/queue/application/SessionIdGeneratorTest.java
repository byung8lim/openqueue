package com.wbl.openqueue.queue.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.wbl.openqueue.queue.domain.WaitingSessionId;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SEC-002: 세션 ID 는 추측 불가능하고 식별자 형식(D-QUE-001-3)을 만족해야 한다. */
@DisplayName("SessionIdGenerator")
class SessionIdGeneratorTest {

    private final SessionIdGenerator generator = new SessionIdGenerator();

    @Test
    @DisplayName("세션 ID 는 43자 base64url(256bit), 멱등 키는 22자(128bit)이며 형식 검증을 통과한다")
    void formats() {
        assertThat(generator.newSessionId().value()).hasSize(43).matches("^[A-Za-z0-9_-]+$");
        assertThat(generator.newAdmissionRequestId().value()).hasSize(22).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    @DisplayName("10,000개를 만들어도 중복이 없다")
    void noDuplicates() {
        Set<WaitingSessionId> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(generator.newSessionId())).isTrue();
        }
    }
}
