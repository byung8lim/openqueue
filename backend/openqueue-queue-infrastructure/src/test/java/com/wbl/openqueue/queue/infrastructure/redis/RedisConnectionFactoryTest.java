package com.wbl.openqueue.queue.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wbl.openqueue.queue.domain.QueueUnavailableException;
import io.lettuce.core.RedisURI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NET-003 TLS/ACL 연결 설정, QUE-009 연결 실패 보고. Redis 서버 없이 실행 가능한 단위 테스트. */
@DisplayName("QUE-008/009 Redis 연결 설정")
class RedisConnectionFactoryTest {

    private static final RedisCredentials CREDENTIALS = new RedisCredentials("queue-engine", "unit-test-only");

    @Test
    @DisplayName("AC-QUE-008-2 TLS 를 켜면 SSL 과 서버 인증서 검증이 함께 켜지고 ACL 자격증명이 전달된다")
    void tlsSettingsMapToUri() {
        RedisConnectionSettings settings =
            new RedisConnectionSettings("redis.dmz.internal", 6380, true, CREDENTIALS, Duration.ofSeconds(2));

        RedisURI uri = RedisConnectionFactory.toRedisUri(settings);

        assertThat(uri.getHost()).isEqualTo("redis.dmz.internal");
        assertThat(uri.getPort()).isEqualTo(6380);
        assertThat(uri.isSsl()).isTrue();
        assertThat(uri.isVerifyPeer()).isTrue();
        assertThat(uri.getTimeout()).isEqualTo(Duration.ofSeconds(2));
        io.lettuce.core.RedisCredentials resolved = uri.getCredentialsProvider().resolveCredentials().block();
        assertThat(resolved).isNotNull();
        assertThat(resolved.getUsername()).isEqualTo("queue-engine");
        assertThat(String.valueOf(resolved.getPassword())).isEqualTo("unit-test-only");
    }

    @Test
    @DisplayName("TLS 를 끄면 평문 연결이다(개발·CI 컨테이너 전용)")
    void plainSettingsMapToUri() {
        RedisConnectionSettings plain = new RedisConnectionSettings("127.0.0.1", 6379, false, CREDENTIALS, Duration.ofSeconds(1));

        RedisURI uri = RedisConnectionFactory.toRedisUri(plain);

        assertThat(uri.isSsl()).isFalse();
    }

    @Test
    @DisplayName("SEC-001 자격증명 toString 은 비밀번호를 마스킹한다")
    void credentialsToStringMasksPassword() {
        assertThat(CREDENTIALS.toString()).contains("queue-engine").doesNotContain("unit-test-only").contains("****");
    }

    @Test
    @DisplayName("설정 검증: 빈 호스트, 포트 범위, 자격증명 없음, 타임아웃 0 은 입력 오류")
    void settingsValidation() {
        assertThatThrownBy(() -> new RedisConnectionSettings(" ", 6379, true, CREDENTIALS, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisConnectionSettings("h", 0, true, CREDENTIALS, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisConnectionSettings("h", 65536, true, CREDENTIALS, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisConnectionSettings("h", 6379, true, null, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisConnectionSettings("h", 6379, true, CREDENTIALS, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisCredentials("", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisCredentials("u", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisRetentionPolicy(Duration.ZERO, Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisRetentionPolicy(Duration.ofHours(1), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-QUE-009-1 연결할 수 없으면 QueueUnavailableException 이며 메시지에 호스트·자격증명이 없다")
    void unreachableHostIsReportedAsUnavailable() {
        RedisConnectionSettings settings = new RedisConnectionSettings("127.0.0.1", 1, false, CREDENTIALS, Duration.ofSeconds(2));

        assertThatThrownBy(() -> RedisConnectionFactory.open(settings))
            .isInstanceOf(QueueUnavailableException.class)
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("127.0.0.1").doesNotContain("unit-test-only"));
    }
}
