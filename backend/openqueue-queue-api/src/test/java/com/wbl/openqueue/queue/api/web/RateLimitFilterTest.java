package com.wbl.openqueue.queue.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Phase 3 "사용자 요청 rate limit": 클라이언트 주소별 분당 한도를 넘으면 429 + Retry-After. 별도 컨텍스트(낮은 한도). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "openqueue.rate-limit.requests-per-minute=3")
@DisplayName("Queue API rate limit")
class RateLimitFilterTest {

    private static final String BASE = "/queue/v1/sites/shop/queues/main/session";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("AC-API-4 한도(3) 안에서는 통과하고 4번째 요청부터 429 Problem Details 와 Retry-After 를 받는다")
    void exceedingLimitIsRejected() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post(BASE)).andExpect(status().isOk());
        }
        mvc.perform(post(BASE))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value(ApiErrors.RATE_LIMITED))
            .andExpect(jsonPath("$.requestId").exists());
    }
}
