package com.wbl.openqueue.queue.api.web;

import com.wbl.openqueue.queue.api.config.OpenQueueProperties;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 대기 세션 쿠키 (D-API-001). 세션 ID 소지가 소유 권한이므로 HttpOnly(스크립트 접근 차단)·Secure·SameSite=Lax 로 둔다.
 * Path 는 공개 API 접두어로 제한한다.
 */
@Component
public class SessionCookie {

    private static final String PATH = "/queue/";

    private final OpenQueueProperties.Cookie settings;

    public SessionCookie(OpenQueueProperties properties) {
        this.settings = properties.cookie() == null ? new OpenQueueProperties.Cookie("oq_session", true) : properties.cookie();
    }

    /**
     * 요청 쿠키에서 세션 ID 를 읽는다. 형식이 맞지 않는 값은 "세션 없음"으로 취급하고 원문을 어디에도 남기지 않는다.
     */
    public Optional<WaitingSessionId> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (settings.name().equals(cookie.getName())) {
                return parse(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private static Optional<WaitingSessionId> parse(String value) {
        try {
            return Optional.of(new WaitingSessionId(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * 세션 쿠키를 만든다. 수명은 대기 TTL + lease TTL 로 두어 브라우저를 닫고 돌아와도 복구할 수 있게 한다(WUI-002).
     */
    public ResponseCookie issue(WaitingSessionId sessionId, Duration maxAge) {
        return ResponseCookie.from(settings.name(), sessionId.value())
            .httpOnly(true)
            .secure(settings.secure())
            .sameSite("Lax")
            .path(PATH)
            .maxAge(maxAge)
            .build();
    }
}
