package com.wbl.openqueue.queue.infrastructure.redis;

/**
 * Redis ACL 자격증명 (NET-003: Queue Engine 전용 최소 권한 계정).
 *
 * <p>보안: {@link #toString()} 은 비밀번호를 마스킹한다. 로그·예외 메시지에 이 객체를 찍어도 비밀정보가 노출되지 않게 하기 위함이다.
 * 값은 운영 Secret Manager/환경변수에서 주입하며 소스·설정 파일에 두지 않는다(개발요청서 §12).
 *
 * @param username ACL 사용자명 (비어 있을 수 없음)
 * @param password 비밀번호 (비어 있을 수 없음)
 */
public record RedisCredentials(String username, String password) {

    /**
     * 값을 검증한다.
     *
     * @throws IllegalArgumentException 사용자명·비밀번호가 null 또는 빈 문자열인 경우
     */
    public RedisCredentials {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("redis.username 값이 없습니다.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("redis.password 값이 없습니다.");
        }
    }

    @Override
    public String toString() {
        return "RedisCredentials[username=" + username + ", password=****]";
    }
}
