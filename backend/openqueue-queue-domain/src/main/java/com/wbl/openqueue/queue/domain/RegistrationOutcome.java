package com.wbl.openqueue.queue.domain;

/** 대기 등록 요청의 처리 결과 분류 (QUE-001, QUE-004). */
public enum RegistrationOutcome {
    /** 새로 대기 순서 맨 뒤에 추가되었다. */
    REGISTERED,
    /** 이미 대기 중이었다. 순번은 유지되고 활동 시각만 갱신되었다(재접속). */
    ALREADY_WAITING,
    /** 이미 입장 완료된 세션이다. 변경 없음. */
    ALREADY_ADMITTED,
    /** 종단 상태 세션의 재등록이 정책(DENY)으로 거부되었다. 변경 없음. */
    REENTRY_DENIED
}
