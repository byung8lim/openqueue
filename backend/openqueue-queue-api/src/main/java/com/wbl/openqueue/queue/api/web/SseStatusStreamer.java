package com.wbl.openqueue.queue.api.web;

import com.wbl.openqueue.queue.api.config.OpenQueueProperties;
import com.wbl.openqueue.queue.application.SessionView;
import com.wbl.openqueue.queue.application.WaitingRoomService;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import com.wbl.openqueue.queue.domain.WaitingSessionStatus;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 상태 스트림 (WUI-001, DEC-05 SSE). 주기마다 세션 상태를 {@code status} 이벤트로 보낸다.
 *
 * <p>D-API-002: 스트림이 열려 있는 동안 매 주기 heartbeat 를 함께 수행한다. 탭이 열려 있으면 세션이 살아 있고,
 * 브라우저가 닫히면 스트림이 끊겨 heartbeat 가 멈추고 TTL 로 만료된다 — "활성 세션 판단"(설계서 7.3)을 연결 자체로 구현한다.
 * WAITING 이 아닌 상태(입장·만료·취소)는 마지막 이벤트로 보내고 스트림을 닫는다. 세션이 사라졌으면 {@code gone} 이벤트 후 닫는다.
 * 연결은 설정 timeout 뒤 서버가 닫고 클라이언트(EventSource)가 자동 재접속한다.
 */
@Component
public class SseStatusStreamer {

    private final WaitingRoomService service;
    private final Clock clock;
    private final Duration interval;
    private final Duration timeout;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "oq-sse-ticker");
        thread.setDaemon(true);
        return thread;
    });

    public SseStatusStreamer(WaitingRoomService service, Clock clock, OpenQueueProperties properties) {
        this.service = service;
        this.clock = clock;
        OpenQueueProperties.Sse sse = properties.sse() == null
            ? new OpenQueueProperties.Sse(Duration.ofSeconds(2), Duration.ofMinutes(5))
            : properties.sse();
        this.interval = sse.interval();
        this.timeout = sse.timeout();
    }

    /**
     * 세션 상태 스트림을 연다. 첫 이벤트는 즉시 보낸다.
     *
     * @param scope     대기열 범위
     * @param sessionId 쿠키의 세션 ID
     * @return SSE emitter
     */
    public SseEmitter open(QueueScope scope, WaitingSessionId sessionId) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        AtomicReference<ScheduledFuture<?>> ticker = new AtomicReference<>();
        Runnable stop = () -> {
            ScheduledFuture<?> future = ticker.get();
            if (future != null) {
                future.cancel(false);
            }
        };
        emitter.onCompletion(stop);
        emitter.onTimeout(stop);
        emitter.onError(e -> stop.run());
        ticker.set(scheduler.scheduleAtFixedRate(() -> tick(emitter, scope, sessionId), 0, interval.toMillis(), TimeUnit.MILLISECONDS));
        return emitter;
    }

    private void tick(SseEmitter emitter, QueueScope scope, WaitingSessionId sessionId) {
        try {
            Optional<SessionView> view = service.heartbeat(scope, sessionId);
            if (view.isEmpty()) {
                emitter.send(SseEmitter.event().name("gone").id(Long.toString(clock.millis())).data("{}", MediaType.APPLICATION_JSON));
                emitter.complete();
                return;
            }
            SessionResponse body = SessionResponse.from(view.get(), clock.instant());
            emitter.send(SseEmitter.event().name("status").id(Long.toString(clock.millis())).data(body, MediaType.APPLICATION_JSON));
            // 대기 중이 아니면(입장·만료·취소) 마지막 상태를 보낸 뒤 닫는다. 입장 후 lease 유지는 Gateway/서비스 쪽 책임이다.
            if (view.get().status() != WaitingSessionStatus.WAITING) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException e) {
            // 클라이언트가 끊었거나 이미 완료된 emitter: 조용히 종료한다(정상 경로).
            emitter.completeWithError(e);
        } catch (RuntimeException e) {
            // 저장소 장애 등: 스트림을 오류로 닫아 클라이언트가 재접속(백오프)하게 한다.
            emitter.completeWithError(e);
        }
    }
}
