package com.wbl.openqueue.queue.api.web;

import com.wbl.openqueue.queue.application.UnknownQueueException;
import com.wbl.openqueue.queue.domain.QueueUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 오류 → RFC 9457 Problem Details 매핑 (error-and-logging-conventions.md §2).
 *
 * <p>규칙: detail 에 입력 원문을 넣지 않는다. 5xx 는 일반 메시지 + requestId 만 돌려주고 원인은 서버 로그에만 남긴다.
 * 스택·호스트·Redis key 는 응답에 포함하지 않는다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final long STORE_RETRY_AFTER_SECONDS = 5;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> invalidInput(HttpServletRequest request, IllegalArgumentException e) {
        // 메시지는 도메인 검증(필드명만 포함)에서 오며 입력 원문은 담기지 않는다(SEC-001). 원인 추적용으로 서버 로그에만 남긴다.
        LOG.info("event=invalid_input requestId={} cause={}", RequestIdFilter.requestId(request), e.getMessage());
        return respond(request, HttpStatus.BAD_REQUEST, ApiErrors.INPUT_INVALID, "입력값이 올바르지 않습니다.", "요청 경로 또는 값의 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(SessionMissingException.class)
    public ResponseEntity<ProblemDetail> sessionMissing(HttpServletRequest request) {
        return respond(request, HttpStatus.UNAUTHORIZED, ApiErrors.SESSION_MISSING, "대기 세션이 없습니다.", "대기열에 먼저 입장해야 합니다.");
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ProblemDetail> sessionNotFound(HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, ApiErrors.SESSION_NOT_FOUND, "대기 세션을 찾을 수 없습니다.", "세션이 만료되어 정리되었거나 이 대기열의 세션이 아닙니다.");
    }

    @ExceptionHandler({UnknownQueueException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> queueNotFound(HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, ApiErrors.QUEUE_NOT_FOUND, "대기열을 찾을 수 없습니다.", "요청한 사이트·대기열이 운영 중이 아닙니다.");
    }

    @ExceptionHandler(QueueUnavailableException.class)
    public ResponseEntity<ProblemDetail> storeUnavailable(HttpServletRequest request, QueueUnavailableException e) {
        // Fail-closed(QUE-009 기본값): 대기열 상태를 알 수 없으면 입장을 허용하지 않는다. Fail-open 분기는 Gateway(Phase 4) 책임이다(D-API-004).
        LOG.warn("event=store_unavailable requestId={}", RequestIdFilter.requestId(request));
        LOG.debug("store unavailable", e);
        ResponseEntity<ProblemDetail> response = respond(request, HttpStatus.SERVICE_UNAVAILABLE, ApiErrors.STORE_UNAVAILABLE,
            "대기열을 일시적으로 사용할 수 없습니다.", "잠시 후 다시 시도해 주세요.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(STORE_RETRY_AFTER_SECONDS))
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
            .body(response.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> internal(HttpServletRequest request, Exception e) {
        LOG.error("event=internal_error requestId={}", RequestIdFilter.requestId(request), e);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, ApiErrors.INTERNAL, "요청을 처리할 수 없습니다.", "잠시 후 다시 시도해 주세요.");
    }

    private static ResponseEntity<ProblemDetail> respond(HttpServletRequest request, HttpStatus status, String code,
                                                         String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(ApiErrors.TYPE_PREFIX + code.toLowerCase(java.util.Locale.ROOT)));
        problem.setProperty("code", code);
        String requestId = RequestIdFilter.requestId(request);
        problem.setProperty("requestId", requestId);
        // instance 를 비워 두면 Spring 이 요청 경로(입력 원문)를 채운다. 원문 미반영(SEC-001)을 위해 요청 ID URN 으로 고정한다.
        problem.setInstance(URI.create("urn:openqueue:request:" + requestId));
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
