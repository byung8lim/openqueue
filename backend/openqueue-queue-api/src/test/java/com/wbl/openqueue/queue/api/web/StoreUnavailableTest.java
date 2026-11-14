package com.wbl.openqueue.queue.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wbl.openqueue.queue.application.QueueRegistry;
import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueueUnavailableException;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** QUE-009 Fail-closed: 저장소 장애 시 503 Problem Details + Retry-After, 내부 정보 미노출. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Queue API 저장소 장애(QUE-009)")
class StoreUnavailableTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private QueueRegistry registry;

    @Test
    @DisplayName("AC-QUE-009-1(API) Redis 장애면 503, code QUE-STORE-001, Retry-After, 호스트·예외 메시지 미노출")
    void storeFailureIsFailClosed() throws Exception {
        QueueUnavailableException failure = new QueueUnavailableException("Queue Redis 에 접근할 수 없습니다.",
            new RuntimeException("connection refused redis.dmz.internal:6380"));
        // 저장소가 죽으면 조회·등록·정책 조회 등 모든 연산이 같은 예외로 실패한다.
        QueueOperations failing = Mockito.mock(QueueOperations.class, invocation -> {
            throw failure;
        });
        when(registry.find(any())).thenReturn(Optional.of(failing));

        mvc.perform(post("/queue/v1/sites/shop/queues/main/session"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "5"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value(ApiErrors.STORE_UNAVAILABLE))
            .andExpect(jsonPath("$.requestId").exists())
            .andExpect(content().string(Matchers.not(Matchers.containsString("redis.dmz.internal"))))
            .andExpect(content().string(Matchers.not(Matchers.containsString("connection refused"))));
    }
}
