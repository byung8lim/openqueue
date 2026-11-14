package com.wbl.openqueue.queue.domain;

/**
 * 대기 세션 상태.
 *
 * <p>상태 전이(QUE-001 범위): (미등록) → {@link #WAITING} → {@link #ADMITTED}.
 * 역방향 전이는 없다. 만료·취소 상태는 QUE-003/QUE-004 에서 추가한다.
 */
public enum WaitingSessionStatus {
    /** 대기 순서에 포함되어 입장을 기다리는 상태. */
    WAITING,
    /** 입장 후보로 선정되어 대기 순서에서 제거된 상태. 다시 대기열에 들어갈 수 없다. */
    ADMITTED
}
