package com.wbl.openqueue.queue.infrastructure.redis;

import com.wbl.openqueue.queue.domain.QueueUnavailableException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * 설정으로부터 Queue Redis 연결을 만든다 (TLS/ACL, NET-003).
 *
 * <p>Standalone 과 Sentinel/Cluster: Phase 2 는 Standalone 엔드포인트(또는 Sentinel 이 가리키는 master 주소를 외부에서 해석해 넘기는 방식)를
 * 지원한다. Lettuce 의 Sentinel/Cluster 전용 클라이언트 연계는 Phase 7(HA) 에서 추가한다(D-QUE-008-6).
 */
public final class RedisConnectionFactory {

    private RedisConnectionFactory() {
    }

    /**
     * 설정을 Lettuce URI 로 변환한다. TLS 를 켜면 서버 인증서 검증도 켠다(비활성화 옵션을 두지 않는다).
     *
     * @param settings 연결 설정
     * @return Lettuce URI
     */
    public static RedisURI toRedisUri(RedisConnectionSettings settings) {
        return RedisURI.Builder.redis(settings.host(), settings.port())
            .withSsl(settings.tls())
            .withVerifyPeer(settings.tls())
            .withAuthentication(settings.credentials().username(), settings.credentials().password())
            .withTimeout(settings.commandTimeout())
            .build();
    }

    /**
     * 연결을 연다. 연결 실패는 저장소 장애로 보고한다.
     *
     * @param settings 연결 설정
     * @return 열린 연결 핸들(호출자가 닫는다)
     * @throws QueueUnavailableException 연결할 수 없는 경우. 메시지에 호스트·자격증명을 포함하지 않는다
     */
    public static RedisQueueConnection open(RedisConnectionSettings settings) {
        RedisClient client = RedisClient.create(toRedisUri(settings));
        try {
            StatefulRedisConnection<String, String> connection = client.connect();
            return new RedisQueueConnection(client, connection);
        } catch (RedisConnectionException e) {
            client.shutdown();
            throw new QueueUnavailableException("Queue Redis 에 연결할 수 없습니다.", e);
        }
    }
}
