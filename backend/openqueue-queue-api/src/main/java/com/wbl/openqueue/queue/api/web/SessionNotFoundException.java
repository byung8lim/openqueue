package com.wbl.openqueue.queue.api.web;

/** 쿠키의 세션이 이 대기열에 없다 (404). 다른 사이트 세션으로 접근해도 같은 응답이라 존재 여부를 구분할 수 없다(IDOR 방지). */
public class SessionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SessionNotFoundException() {
        super("대기 세션을 찾을 수 없습니다.");
    }
}
