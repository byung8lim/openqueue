package com.wbl.openqueue.queue.api.admission;

import com.wbl.openqueue.queue.application.AdmissionCycle;
import com.wbl.openqueue.queue.application.QueueRegistry;
import com.wbl.openqueue.queue.domain.AdmissionResult;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.QueueUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 입장 주기 트리거. 설정 주기마다 모든 대기열에 대해 {@link AdmissionCycle#run} 을 호출한다.
 *
 * <p>한 대기열의 저장소 장애가 다른 대기열 처리를 막지 않도록 대기열별로 예외를 처리한다. 로그에는 범위와 건수만 남기고 세션 ID 는 남기지 않는다.
 * 테스트에서는 {@code openqueue.admission.enabled=false} 로 끈다(결정적 검증).
 */
@Component
@ConditionalOnProperty(prefix = "openqueue.admission", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdmissionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AdmissionScheduler.class);

    private final AdmissionCycle cycle;
    private final QueueRegistry registry;

    public AdmissionScheduler(AdmissionCycle cycle, QueueRegistry registry) {
        this.cycle = cycle;
        this.registry = registry;
    }

    /** 주기 실행. */
    @Scheduled(fixedDelayString = "${openqueue.admission.interval:1s}")
    public void tick() {
        for (QueueScope scope : registry.scopes()) {
            try {
                AdmissionResult result = cycle.run(scope);
                if (!result.admittedSessions().isEmpty()) {
                    LOG.info("event=admission site={} queue={} admitted={}", scope.siteId().value(), scope.queueId().value(),
                        result.admittedSessions().size());
                }
            } catch (QueueUnavailableException e) {
                // 저장소 장애: 다음 주기에 재시도한다. 상세 원인은 debug 로만(접속 정보 노출 방지).
                LOG.warn("event=admission_skipped site={} queue={} reason=store_unavailable",
                    scope.siteId().value(), scope.queueId().value());
                LOG.debug("store unavailable", e);
            }
        }
    }
}
