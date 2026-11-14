package com.wbl.openqueue.queue.domain;

/**
 * 대기열 저장소 장애 시 사이트별 처리 정책 (QUE-009, DEC-09).
 *
 * <p>기본값은 {@link #FAIL_CLOSED} 다. Fail-open 은 원본 서비스가 과부하를 감수하더라도 가용성을 우선할 때
 * 사이트 단위로 명시적으로 선택한다. 실제 분기(차단 응답 vs 통과)는 Gateway(Phase 4)가 수행하며,
 * 도메인·어댑터는 {@link QueueUnavailableException} 으로 장애를 알리는 역할만 한다.
 */
public enum FailurePolicy {
    /** 저장소 장애 시 신규 입장을 차단한다(안전 기본값). 원본 서비스를 보호한다. */
    FAIL_CLOSED,
    /** 저장소 장애 시 대기열을 우회해 통과시킨다. 원본 서비스 과부하 위험을 사이트 운영자가 수용한 경우에만 사용한다. */
    FAIL_OPEN;

    /** 사이트 정책이 지정되지 않았을 때 적용하는 기본값. */
    public static final FailurePolicy DEFAULT = FAIL_CLOSED;
}
