package com.wbl.openqueue.queue.api.config;

import com.wbl.openqueue.queue.domain.FailurePolicy;
import com.wbl.openqueue.queue.domain.ReentryPolicy;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Queue API 설정 ({@code openqueue.*}). 대기열 정책·저장소·운영 파라미터를 설정 파일에서 받는다(Phase 3).
 * Phase 6 에서 대기열 정책 부분은 서명된 스냅샷으로 대체된다.
 *
 * @param store     대기열 저장소 종류
 * @param redis     Redis 연결(store=REDIS 일 때 필수). 비밀번호는 환경변수로 주입한다
 * @param queues    운영할 대기열 목록
 * @param admission 입장 주기 스케줄러
 * @param sse       SSE 상태 스트림
 * @param rateLimit 사용자 요청 rate limit
 * @param cookie    세션 쿠키
 */
@ConfigurationProperties(prefix = "openqueue")
public record OpenQueueProperties(@DefaultValue("MEMORY") Store store, Redis redis, List<Queue> queues,
                                  Admission admission, Sse sse, RateLimit rateLimit, Cookie cookie) {

    public OpenQueueProperties {
        queues = queues == null ? List.of() : List.copyOf(queues);
    }

    /** 저장소 종류. MEMORY 는 단일 노드 개발·테스트 전용이다. */
    public enum Store {
        /** 프로세스 메모리(단일 노드, 재시작 시 소실). */
        MEMORY,
        /** DMZ Queue Redis(운영). */
        REDIS
    }

    /**
     * Redis 연결 설정.
     *
     * @param host                     호스트
     * @param port                     포트
     * @param tls                      TLS 사용(운영 기본 true)
     * @param username                 ACL 사용자
     * @param password                 비밀번호(환경변수 OPENQUEUE_REDIS_PASSWORD 로 주입)
     * @param commandTimeout           명령 타임아웃
     * @param terminalSessionRetention 종단 세션 보존 기간
     * @param admissionResultRetention 멱등 결과 보존 기간
     */
    public record Redis(String host, @DefaultValue("6379") int port, @DefaultValue("true") boolean tls, String username, String password,
                        @DefaultValue("2s") Duration commandTimeout, @DefaultValue("1h") Duration terminalSessionRetention,
                        @DefaultValue("10m") Duration admissionResultRetention) {
    }

    /**
     * 대기열 하나.
     *
     * @param siteId        site_id
     * @param queueId       queue_id
     * @param policy        대기열 정책
     * @param failurePolicy 저장소 장애 시 정책(QUE-009, 기본 FAIL_CLOSED)
     */
    public record Queue(String siteId, String queueId, Policy policy, @DefaultValue("FAIL_CLOSED") FailurePolicy failurePolicy) {
    }

    /**
     * 대기열 정책 값(도메인 {@code QueuePolicy} 와 1:1). 기본값을 두지 않는다(D-QUE-004-3).
     *
     * @param waitingSessionTtl 대기 TTL
     * @param reentry           재진입 정책
     * @param concurrencyLimit  동시 입장 상한
     * @param admissionLeaseTtl 입장 lease TTL
     * @param rateMaxAdmissions 입장률 창당 최대 건수(0 = 무제한)
     * @param rateWindow        입장률 창 길이(rateMaxAdmissions > 0 일 때 필수)
     */
    public record Policy(Duration waitingSessionTtl, ReentryPolicy reentry, int concurrencyLimit, Duration admissionLeaseTtl,
                         @DefaultValue("0") int rateMaxAdmissions, Duration rateWindow) {
    }

    /**
     * 입장 주기.
     *
     * @param enabled  스케줄러 사용 여부(테스트에서 false)
     * @param interval 주기
     */
    public record Admission(@DefaultValue("true") boolean enabled, @DefaultValue("1s") Duration interval) {
    }

    /**
     * SSE 설정.
     *
     * @param interval 상태 이벤트 주기(이 주기마다 heartbeat 도 수행된다)
     * @param timeout  연결 최대 유지 시간(이후 클라이언트가 재접속)
     */
    public record Sse(@DefaultValue("2s") Duration interval, @DefaultValue("5m") Duration timeout) {
    }

    /**
     * 사용자 요청 rate limit (클라이언트 주소별 고정 창).
     *
     * @param requestsPerMinute 분당 허용 요청 수
     */
    public record RateLimit(@DefaultValue("120") int requestsPerMinute) {
    }

    /**
     * 세션 쿠키.
     *
     * @param name   쿠키 이름
     * @param secure Secure 속성(운영 true, 로컬 http 개발 시만 false)
     */
    public record Cookie(@DefaultValue("oq_session") String name, @DefaultValue("true") boolean secure) {
    }
}
