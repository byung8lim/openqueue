package com.wbl.openqueue.queue.api.web;

/** 공개 Queue API 오류 코드 카탈로그 (error-and-logging-conventions.md §2). 코드는 `<영역>-<분류>-<번호>`. */
public final class ApiErrors {

    /** 입력 형식 오류 (400). */
    public static final String INPUT_INVALID = "QUE-INPUT-001";
    /** 세션 쿠키 없음·형식 오류 (401). */
    public static final String SESSION_MISSING = "QUE-SESSION-001";
    /** 세션이 이 대기열에 없음 (404). */
    public static final String SESSION_NOT_FOUND = "QUE-SESSION-002";
    /** 운영 중이 아닌 대기열 (404). */
    public static final String QUEUE_NOT_FOUND = "QUE-QUEUE-001";
    /** rate limit 초과 (429). */
    public static final String RATE_LIMITED = "QUE-RATE-001";
    /** 대기열 저장소 장애 (503). */
    public static final String STORE_UNAVAILABLE = "QUE-STORE-001";
    /** 기타 내부 오류 (500). */
    public static final String INTERNAL = "QUE-INTERNAL-001";

    /** Problem Details `type` URN 접두어. */
    public static final String TYPE_PREFIX = "urn:openqueue:problem:";

    private ApiErrors() {
    }
}
