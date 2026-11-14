package com.wbl.openqueue.queue.api.config;

import com.wbl.openqueue.queue.application.AdmissionCycle;
import com.wbl.openqueue.queue.application.InMemoryQueueRegistry;
import com.wbl.openqueue.queue.application.QueueRegistry;
import com.wbl.openqueue.queue.application.SessionIdGenerator;
import com.wbl.openqueue.queue.application.WaitingRoomService;
import com.wbl.openqueue.queue.domain.AdmissionRateLimit;
import com.wbl.openqueue.queue.domain.QueueId;
import com.wbl.openqueue.queue.domain.QueueOperations;
import com.wbl.openqueue.queue.domain.QueuePolicy;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.SiteId;
import com.wbl.openqueue.queue.domain.WaitingQueue;
import com.wbl.openqueue.queue.infrastructure.redis.RedisConnectionFactory;
import com.wbl.openqueue.queue.infrastructure.redis.RedisConnectionSettings;
import com.wbl.openqueue.queue.infrastructure.redis.RedisCredentials;
import com.wbl.openqueue.queue.infrastructure.redis.RedisQueueConnection;
import com.wbl.openqueue.queue.infrastructure.redis.RedisRetentionPolicy;
import com.wbl.openqueue.queue.infrastructure.redis.RedisWaitingQueue;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 설정으로부터 대기열 레지스트리·유스케이스 빈을 조립한다.
 *
 * <p>저장소가 REDIS 면 연결 하나를 모든 대기열이 공유한다(Lettuce 공유 연결은 스레드 안전). MEMORY 는 단일 노드 개발·테스트 전용이다.
 */
@Configuration
public class QueueEngineConfiguration {

    /** UTC 시계. 도메인은 시계를 갖지 않으므로 여기서 주입한다(D-QUE-004-1). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SessionIdGenerator sessionIdGenerator() {
        return new SessionIdGenerator();
    }

    /**
     * Redis 연결 — {@code openqueue.store=REDIS} 일 때만 빈이 생성되고 컨테이너 종료 시 close 된다.
     * MEMORY 저장소에서는 빈이 없으므로 {@link #queueRegistry} 가 {@code Optional.empty()} 를 받는다.
     * (Optional 을 직접 빈으로 두면 destroyMethod="close" 를 찾지 못해 컨텍스트가 뜨지 않는다.)
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "openqueue", name = "store", havingValue = "REDIS")
    public RedisQueueConnection redisQueueConnection(OpenQueueProperties properties) {
        OpenQueueProperties.Redis redis = properties.redis();
        if (redis == null) {
            throw new IllegalStateException("openqueue.store=REDIS 이면 openqueue.redis 설정이 필요합니다.");
        }
        RedisConnectionSettings settings = new RedisConnectionSettings(redis.host(), redis.port(), redis.tls(),
            new RedisCredentials(redis.username(), redis.password()), redis.commandTimeout());
        return RedisConnectionFactory.open(settings);
    }

    @Bean
    public QueueRegistry queueRegistry(OpenQueueProperties properties, Optional<RedisQueueConnection> redisConnection) {
        List<InMemoryQueueRegistry.Entry> entries = new ArrayList<>();
        for (OpenQueueProperties.Queue queue : properties.queues()) {
            QueueScope scope = new QueueScope(new SiteId(queue.siteId()), new QueueId(queue.queueId()));
            QueuePolicy policy = toPolicy(queue.policy());
            QueueOperations operations = redisConnection
                .<QueueOperations>map(connection ->
                    new RedisWaitingQueue(scope, policy, retention(properties.redis()), connection.commands()))
                .orElseGet(() -> new WaitingQueue(scope, policy));
            entries.add(new InMemoryQueueRegistry.Entry(operations, queue.failurePolicy()));
        }
        return new InMemoryQueueRegistry(entries);
    }

    @Bean
    public WaitingRoomService waitingRoomService(QueueRegistry registry, SessionIdGenerator ids, Clock clock) {
        return new WaitingRoomService(registry, ids, clock);
    }

    @Bean
    public AdmissionCycle admissionCycle(QueueRegistry registry, SessionIdGenerator ids, Clock clock) {
        return new AdmissionCycle(registry, ids, clock);
    }

    private static QueuePolicy toPolicy(OpenQueueProperties.Policy p) {
        if (p == null) {
            throw new IllegalStateException("대기열 정책(openqueue.queues[].policy)이 없습니다.");
        }
        AdmissionRateLimit rate = p.rateMaxAdmissions() > 0
            ? AdmissionRateLimit.perWindow(p.rateMaxAdmissions(), p.rateWindow())
            : AdmissionRateLimit.unlimited();
        return new QueuePolicy(p.waitingSessionTtl(), p.reentry(), p.concurrencyLimit(), p.admissionLeaseTtl(), rate);
    }

    private static RedisRetentionPolicy retention(OpenQueueProperties.Redis redis) {
        return new RedisRetentionPolicy(redis.terminalSessionRetention(), redis.admissionResultRetention());
    }
}
