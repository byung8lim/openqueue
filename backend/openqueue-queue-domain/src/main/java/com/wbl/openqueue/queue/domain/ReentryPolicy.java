package com.wbl.openqueue.queue.domain;

/** 종단 상태(EXPIRED/CANCELLED) 세션이 다시 등록할 때의 처리 정책 (QUE-004). */
public enum ReentryPolicy {
    /** 맨 뒤에 새 세션으로 다시 줄을 선다. */
    ALLOW,
    /** 재등록을 거부한다. 기존 종단 상태를 유지한다. */
    DENY
}
