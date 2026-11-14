package com.wbl.openqueue.queue.api.web;

/** 세션 쿠키가 없거나 형식이 맞지 않는다 (401). 입력 원문을 메시지에 넣지 않는다. */
public class SessionMissingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SessionMissingException() {
        super("대기 세션이 없습니다.");
    }
}
