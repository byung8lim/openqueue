package com.wbl.openqueue.queue.infrastructure.redis;

import java.time.Duration;

/**
 * Queue Redis 연결 설정 (NET-003, QUE-008).
 *
 * <p>보안 기본값: ACL 자격증명은 필수이며, TLS 를 켜면 서버 인증서 검증(verify peer)도 항상 수행한다.
 * 운영 환경은 {@code tls=true} 를 전제로 한다. 평문 연결은 개발·CI 컨테이너 테스트에서만 허용한다.
 *
 * @param host           Redis 호스트 (DMZ 전용 서브넷 사설 주소)
 * @param port           포트 (1~65535)
 * @param tls            TLS 사용 여부. true 면 인증서 검증 포함
 * @param credentials    ACL 자격증명(필수)
 * @param commandTimeout 명령 타임아웃(양수). 이 시간을 넘기면 저장소 장애로 간주한다(QUE-009)
 */
public record RedisConnectionSettings(String host, int port, boolean tls, RedisCredentials credentials, Duration commandTimeout) {

    private static final int MAX_PORT = 65535;

    /**
     * 값을 검증한다.
     *
     * @throws IllegalArgumentException 호스트가 비었거나 포트 범위를 벗어나거나 자격증명·타임아웃이 올바르지 않은 경우
     */
    public RedisConnectionSettings {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("redis.host 값이 없습니다.");
        }
        if (port < 1 || port > MAX_PORT) {
            throw new IllegalArgumentException("redis.port 는 1~65535 범위여야 합니다.");
        }
        if (credentials == null) {
            throw new IllegalArgumentException("redis.credentials 값이 없습니다. ACL 계정은 필수입니다.");
        }
        if (commandTimeout == null || commandTimeout.isZero() || commandTimeout.isNegative()) {
            throw new IllegalArgumentException("redis.command_timeout 은 0보다 커야 합니다.");
        }
    }
}
