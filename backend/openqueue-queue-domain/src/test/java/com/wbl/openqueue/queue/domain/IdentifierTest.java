package com.wbl.openqueue.queue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * AC-QUE-001-6 식별자 검증 단위 테스트 (D-QUE-001-3 형식 규칙).
 */
@DisplayName("AC-QUE-001-6 식별자 검증")
class IdentifierTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "shop", "shop-2026", "0abc"})
    @DisplayName("유효한 site_id/queue_id 는 허용된다")
    void acceptsValidScopeIdentifiers(String value) {
        assertThat(new SiteId(value).value()).isEqualTo(value);
        assertThat(new QueueId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-shop", "Shop", "shop_1", "shop/../admin", "shop main", "shop\nlog"})
    @DisplayName("형식에 맞지 않는 site_id/queue_id 는 거부된다")
    void rejectsInvalidScopeIdentifiers(String value) {
        assertThatThrownBy(() -> new SiteId(value)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueueId(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("site_id/queue_id 는 최대 64자까지 허용한다")
    void scopeIdentifierLengthBoundary() {
        String max = "a".repeat(64);
        String over = "a".repeat(65);

        assertThat(new SiteId(max).value()).hasSize(64);
        assertThatThrownBy(() -> new SiteId(over)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("session_id 는 16~128자 base64url 문자만 허용한다")
    void sessionIdBoundary() {
        assertThat(new WaitingSessionId("A".repeat(16)).value()).hasSize(16);
        assertThat(new WaitingSessionId("a_-Z".repeat(32)).value()).hasSize(128);
        assertThatThrownBy(() -> new WaitingSessionId("A".repeat(15))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WaitingSessionId("A".repeat(129))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WaitingSessionId("session=0000000001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WaitingSessionId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("admission_request_id 는 8~128자 base64url 문자만 허용한다")
    void admissionRequestIdBoundary() {
        assertThat(new AdmissionRequestId("A".repeat(8)).value()).hasSize(8);
        assertThatThrownBy(() -> new AdmissionRequestId("A".repeat(7))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdmissionRequestId("A".repeat(129))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdmissionRequestId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SEC-001: 오류 메시지에 입력 원문을 포함하지 않는다")
    void errorMessageDoesNotEchoInput() {
        String malicious = "INJECTED\r\nFAKE-LOG-LINE";

        assertThatThrownBy(() -> new SiteId(malicious)).hasMessageNotContaining("INJECTED");
        assertThatThrownBy(() -> new QueueId(malicious)).hasMessageNotContaining("INJECTED");
        assertThatThrownBy(() -> new WaitingSessionId(malicious)).hasMessageNotContaining("INJECTED");
        assertThatThrownBy(() -> new AdmissionRequestId(malicious)).hasMessageNotContaining("INJECTED");
    }

    @Test
    @DisplayName("QueueScope 는 site_id 와 queue_id 를 모두 요구한다")
    void queueScopeRequiresBothIdentifiers() {
        assertThatThrownBy(() -> new QueueScope(null, new QueueId("main"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueueScope(new SiteId("shop"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("WaitingSessionId 의 문자열 표현은 원문 전체를 노출하지 않는다")
    void sessionIdToStringIsMasked() {
        WaitingSessionId id = new WaitingSessionId("secretSessionValue-123456");

        assertThat(id.toString()).doesNotContain("secretSessionValue-123456");
    }
}
