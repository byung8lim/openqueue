package com.wbl.openqueue.queue.domain;

/**
 * 대기열 저장소(Redis)에 접근할 수 없어 연산을 수행하지 못했음을 알리는 예외 (QUE-009).
 *
 * <p>상위 계층(Gateway, Phase 4)은 이 예외를 사이트별 {@link FailurePolicy} 에 따라 처리한다.
 * 기본은 Fail-closed(차단)이다. 메시지에 접속 정보·자격증명을 포함하지 않는다.
 */
public class QueueUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message 사람이 읽을 설명(비밀정보 금지)
     * @param cause   원인 예외(드라이버 예외)
     */
    public QueueUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
