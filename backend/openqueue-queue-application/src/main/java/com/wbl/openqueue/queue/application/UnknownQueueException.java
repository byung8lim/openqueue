package com.wbl.openqueue.queue.application;

/** 운영 중이 아닌 대기열 범위를 요청했다 (API 는 404). 메시지에 입력 원문을 넣지 않는다. */
public class UnknownQueueException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownQueueException() {
        super("요청한 대기열이 존재하지 않습니다.");
    }
}
