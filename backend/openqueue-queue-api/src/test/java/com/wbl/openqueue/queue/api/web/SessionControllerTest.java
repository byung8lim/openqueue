package com.wbl.openqueue.queue.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wbl.openqueue.queue.application.AdmissionCycle;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 공개 Queue API 계약·세션 보안 테스트 (WUI-001/002, SEC-001/002, error-and-logging-conventions.md). 메모리 저장소. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Queue API /queue/v1 세션")
// 인메모리 대기열 빈이 싱글턴이므로 테스트 간 세션 누적을 막기 위해 메서드마다 컨텍스트를 새로 만든다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SessionControllerTest {

    private static final String BASE = "/queue/v1/sites/shop/queues/main/session";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AdmissionCycle admissionCycle;

    private Cookie join() throws Exception {
        MvcResult result = mvc.perform(post(BASE)).andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("oq_session");
    }

    @Test
    @DisplayName("AC-API-1 입장하면 HttpOnly·Secure·SameSite=Lax·Path=/queue/ 쿠키가 발급되고 본문에 세션 ID 가 없다")
    void joinIssuesHardenedCookieAndHidesSessionId() throws Exception {
        MvcResult result = mvc.perform(post(BASE))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("WAITING"))
            .andExpect(jsonPath("$.position").value(1))
            .andExpect(jsonPath("$.siteId").value("shop"))
            .andExpect(jsonPath("$.expiresAt").exists())
            .andExpect(jsonPath("$.sessionId").doesNotExist())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("oq_session=").contains("HttpOnly").contains("Secure").contains("SameSite=Lax")
            .contains("Path=/queue/")
            .contains("Max-Age=900");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(result.getResponse().getCookie("oq_session").getValue());
    }

    @Test
    @DisplayName("AC-WUI-002-2 쿠키를 가지고 다시 입장(새로고침)하면 같은 세션으로 순번이 유지된다")
    void rejoinWithCookieKeepsPosition() throws Exception {
        Cookie first = join();
        join();

        mvc.perform(post(BASE).cookie(first))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.position").value(1))
            .andExpect(jsonPath("$.waitingCount").value(2));
        mvc.perform(get(BASE).cookie(first)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    @DisplayName("AC-API-2 쿠키 없음 401, 형식 불량 쿠키 401, 모르는 세션 404 — 모두 Problem Details 이며 입력 원문을 반영하지 않는다")
    void missingOrUnknownSession() throws Exception {
        mvc.perform(get(BASE))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value(ApiErrors.SESSION_MISSING))
            .andExpect(jsonPath("$.requestId").exists());

        mvc.perform(get(BASE).cookie(new Cookie("oq_session", "bad value!\n")))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("bad value"))));

        mvc.perform(get(BASE).cookie(new Cookie("oq_session", "unknown-session-0000000001")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ApiErrors.SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("AC-API-3 잘못된 식별자는 400(원문 미반영), 운영 중이 아닌 대기열은 404")
    void invalidIdentifiersAndUnknownQueue() throws Exception {
        mvc.perform(post("/queue/v1/sites/Shop%20X/queues/main/session"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ApiErrors.INPUT_INVALID))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Shop"))));

        mvc.perform(post("/queue/v1/sites/shop/queues/closed/session"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ApiErrors.QUEUE_NOT_FOUND));
    }

    @Test
    @DisplayName("heartbeat 는 활동 시각을 갱신하고, DELETE 는 CANCELLED 를 돌려주며 멱등이다")
    void heartbeatAndLeave() throws Exception {
        Cookie cookie = join();

        mvc.perform(post(BASE + "/heartbeat").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING"));
        mvc.perform(delete(BASE).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.position").doesNotExist()).andExpect(jsonPath("$.expiresAt").doesNotExist());
        mvc.perform(delete(BASE).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        // reentry=DENY: 취소된 세션으로 다시 입장하면 거부 사실을 알려준다 (AC-WUI-002-3)
        mvc.perform(post(BASE).cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.reentryDenied").value(true));
    }

    @Test
    @DisplayName("입장 주기가 돌면 상태가 ADMITTED 로 바뀌고 lease 만료 시각이 내려온다")
    void admissionCycleAdmits() throws Exception {
        Cookie cookie = join();

        admissionCycle.runAll();

        mvc.perform(get(BASE).cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ADMITTED"))
            .andExpect(jsonPath("$.position").doesNotExist())
            .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    @DisplayName("AC-WUI-001-1 SSE 스트림은 text/event-stream 으로 status 이벤트를 보내고 비대기 상태에서 닫힌다")
    void sseStreamsStatusAndCompletesWhenNotWaiting() throws Exception {
        Cookie cookie = join();
        admissionCycle.runAll();

        MvcResult started = mvc.perform(get(BASE + "/events").cookie(cookie).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andReturn();
        started.getAsyncResult(TimeUnit.SECONDS.toMillis(5));

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("event:status")))
            .andExpect(content().string(containsString("\"status\":\"ADMITTED\"")));
    }

    @Test
    @DisplayName("SSE 는 쿠키 없이는 401")
    void sseRequiresSession() throws Exception {
        mvc.perform(get(BASE + "/events").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("OBS-001 형식이 맞는 X-Request-Id 는 그대로 반환하고, 아니면 새로 발급한다")
    void requestIdHandling() throws Exception {
        mvc.perform(post(BASE).header("X-Request-Id", "client-req-0001")).andExpect(header().string("X-Request-Id", "client-req-0001"));
        mvc.perform(post(BASE).header("X-Request-Id", "bad id\n"))
            .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("bad id\n")));
    }
}
