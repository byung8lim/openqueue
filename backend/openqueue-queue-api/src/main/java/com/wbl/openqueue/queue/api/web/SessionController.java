package com.wbl.openqueue.queue.api.web;

import com.wbl.openqueue.queue.application.QueueRegistry;
import com.wbl.openqueue.queue.application.SessionView;
import com.wbl.openqueue.queue.application.UnknownQueueException;
import com.wbl.openqueue.queue.application.WaitingRoomService;
import com.wbl.openqueue.queue.domain.QueueId;
import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueuePolicy;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.SiteId;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 공개 Queue API (error-and-logging-conventions.md §1: {@code /queue/v1}).
 *
 * <pre>
 *   POST   /queue/v1/sites/{siteId}/queues/{queueId}/session            입장(등록 또는 쿠키 세션 복구) — 세션 쿠키 발급
 *   GET    /queue/v1/sites/{siteId}/queues/{queueId}/session            상태 조회
 *   POST   /queue/v1/sites/{siteId}/queues/{queueId}/session/heartbeat  활동 신호
 *   DELETE /queue/v1/sites/{siteId}/queues/{queueId}/session            대기 포기(입장 포기)
 *   GET    /queue/v1/sites/{siteId}/queues/{queueId}/session/events     SSE 상태 스트림
 * </pre>
 *
 * <p>세션은 쿠키로만 식별한다(본문·헤더 금지, D-API-001). 경로의 식별자는 도메인 형식 검증을 거치며 위반 시 400(원문 미반영).
 */
@RestController
@RequestMapping(path = "/queue/v1/sites/{siteId}/queues/{queueId}/session", produces = MediaType.APPLICATION_JSON_VALUE)
public class SessionController {

    private final WaitingRoomService service;
    private final QueueRegistry registry;
    private final SessionCookie cookie;
    private final SseStatusStreamer streamer;
    private final Clock clock;

    public SessionController(WaitingRoomService service, QueueRegistry registry, SessionCookie cookie,
                             SseStatusStreamer streamer, Clock clock) {
        this.service = service;
        this.registry = registry;
        this.cookie = cookie;
        this.streamer = streamer;
        this.clock = clock;
    }

    /** 입장: 쿠키 세션이 있으면 복구(순번 유지), 없으면 새 세션. 항상 쿠키를 (재)발급한다. */
    @PostMapping
    public ResponseEntity<SessionResponse> join(@PathVariable String siteId, @PathVariable String queueId, HttpServletRequest request) {
        QueueScope scope = scope(siteId, queueId);
        SessionView view = service.join(scope, cookie.read(request));
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.issue(view.sessionId(), cookieMaxAge(scope)).toString())
            .body(SessionResponse.from(view, clock.instant()));
    }

    /** 상태 조회(상태 불변). */
    @GetMapping
    public SessionResponse status(@PathVariable String siteId, @PathVariable String queueId, HttpServletRequest request) {
        return withSession(siteId, queueId, request, service::status);
    }

    /** 활동 신호(SSE 를 쓸 수 없는 클라이언트의 폴링 경로). */
    @PostMapping("/heartbeat")
    public SessionResponse heartbeat(@PathVariable String siteId, @PathVariable String queueId, HttpServletRequest request) {
        return withSession(siteId, queueId, request, service::heartbeat);
    }

    /** 대기 포기 또는 입장 포기. 종단 상태 세션은 변경 없이 현재 상태를 돌려준다(멱등). */
    @DeleteMapping
    public SessionResponse leave(@PathVariable String siteId, @PathVariable String queueId, HttpServletRequest request) {
        return withSession(siteId, queueId, request, service::leave);
    }

    /** SSE 상태 스트림. */
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String siteId, @PathVariable String queueId, HttpServletRequest request) {
        QueueScope scope = scope(siteId, queueId);
        WaitingSessionId sessionId = cookie.read(request).orElseThrow(SessionMissingException::new);
        service.status(scope, sessionId).orElseThrow(SessionNotFoundException::new);
        return streamer.open(scope, sessionId);
    }

    private SessionResponse withSession(String siteId, String queueId, HttpServletRequest request,
                                        BiFunction<QueueScope, WaitingSessionId, Optional<SessionView>> action) {
        QueueScope scope = scope(siteId, queueId);
        WaitingSessionId sessionId = cookie.read(request).orElseThrow(SessionMissingException::new);
        SessionView view = action.apply(scope, sessionId).orElseThrow(SessionNotFoundException::new);
        return SessionResponse.from(view, clock.instant());
    }

    private static QueueScope scope(String siteId, String queueId) {
        return new QueueScope(new SiteId(siteId), new QueueId(queueId));
    }

    /** 쿠키 수명 = 대기 TTL + lease TTL (WUI-002 복구 창). */
    private Duration cookieMaxAge(QueueScope scope) {
        QueuePolicy policy = registry.find(scope).map(QueueOperations::policy).orElseThrow(UnknownQueueException::new);
        return policy.waitingSessionTtl().plus(policy.admissionLeaseTtl());
    }
}
