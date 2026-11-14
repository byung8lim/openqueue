package com.wbl.openqueue.queue.infrastructure.redis;

import java.time.Duration;

/**
 * Redis 에 남기는 보조 데이터의 보존 기간 (QUE-008, D-QUE-008-4).
 *
 * <p>도메인 메모리 모델은 보관 기간을 제한하지 않았다(D-QUE-001-2, D-QUE-004-4). Redis 에서는 메모리 회수를 위해 TTL 로 제한한다.
 * 기본값을 두지 않고 운영 정책에서 명시한다.
 *
 * @param terminalSession 종단 상태(EXPIRED/CANCELLED) 세션 해시 보존 기간. 재진입 판정·"만료되었습니다" 안내에 필요한 기간 이상
 * @param admissionResult 입장 요청 멱등 키 결과 보존 기간. 호출자의 재전송 가능 기간 이상
 */
public record RedisRetentionPolicy(Duration terminalSession, Duration admissionResult) {

    /**
     * 값을 검증한다.
     *
     * @throws IllegalArgumentException null·0·음수인 경우
     */
    public RedisRetentionPolicy {
        requirePositive(terminalSession, "terminal_session_retention");
        requirePositive(admissionResult, "admission_result_retention");
    }

    private static void requirePositive(Duration value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 값이 없습니다.");
        }
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " 은 0보다 커야 합니다.");
        }
    }
}
