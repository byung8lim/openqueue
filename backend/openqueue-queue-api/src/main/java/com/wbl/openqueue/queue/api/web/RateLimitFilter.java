package com.wbl.openqueue.queue.api.web;

import com.wbl.openqueue.queue.api.config.OpenQueueProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 사용자 요청 rate limit (Phase 3 항목, D-API-003): 클라이언트 주소별 분 단위 고정 창.
 *
 * <p>목적은 한 클라이언트의 폴링 폭주·스크립트 남용으로 Queue Engine 이 소진되는 것을 막는 것이다. 사이트 전체 유입 제어는 대기열 자체가 하고,
 * 정교한 분산 rate limit·신뢰 프록시 IP 추출은 Gateway(Phase 4)가 담당한다. 여기서는 {@code remoteAddr} 만 쓴다(프록시 헤더를 신뢰하지 않음).
 * 창이 지난 항목은 요청마다 함께 정리해 메모리를 제한한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MINUTE_MS = 60_000L;
    private static final int CLEANUP_EVERY = 256;

    private final int limitPerMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceCleanup = new AtomicInteger();

    public RateLimitFilter(OpenQueueProperties properties, Clock clock) {
        this.limitPerMinute = properties.rateLimit() == null ? new OpenQueueProperties.RateLimit(120).requestsPerMinute()
            : properties.rateLimit().requestsPerMinute();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/queue/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        long nowMs = clock.millis();
        long minute = nowMs / MINUTE_MS;
        Window window = windows.compute(request.getRemoteAddr(), (key, current) ->
            current == null || current.minute != minute ? new Window(minute) : current);
        cleanupOccasionally(minute);
        if (window.count.incrementAndGet() > limitPerMinute) {
            reject(request, response, (minute + 1) * MINUTE_MS - nowMs);
            return;
        }
        chain.doFilter(request, response);
    }

    private void cleanupOccasionally(long currentMinute) {
        if (requestsSinceCleanup.incrementAndGet() % CLEANUP_EVERY == 0) {
            windows.values().removeIf(w -> w.minute != currentMinute);
        }
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterMs) throws IOException {
        long retryAfterSeconds = Math.max(1, (retryAfterMs + 999) / 1000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"type\":\"" + ApiErrors.TYPE_PREFIX + "que-rate-001\",\"title\":\"요청이 너무 많습니다.\",\"status\":429,"
            + "\"code\":\"" + ApiErrors.RATE_LIMITED + "\",\"requestId\":\"" + RequestIdFilter.requestId(request) + "\","
            + "\"detail\":\"잠시 후 다시 시도해 주세요.\"}";
        response.getWriter().write(body);
    }

    /** 분 단위 창. */
    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        Window(long minute) {
            this.minute = minute;
        }
    }
}
