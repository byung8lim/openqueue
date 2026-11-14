package com.wbl.openqueue.queue.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 ID 부여와 공통 보안 응답 헤더 (OBS-001, error-and-logging-conventions.md §3).
 *
 * <p>외부에서 들어온 {@code X-Request-Id} 는 형식({@code [A-Za-z0-9_-]{8,64}})이 맞을 때만 신뢰하고, 아니면 새로 만든다
 * (로그 인젝션 방지). 모든 응답에 {@code Cache-Control: no-store}(세션 상태 캐시 금지)와 {@code X-Content-Type-Options: nosniff} 를 붙인다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** 요청 속성 이름. */
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    /** 헤더 이름. */
    public static final String HEADER = "X-Request-Id";

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    /** 현재 요청의 ID. 필터를 거치지 않은 경우(테스트) 빈 문자열 대신 "unknown". */
    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        chain.doFilter(request, response);
    }
}
