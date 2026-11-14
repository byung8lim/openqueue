package com.wbl.openqueue.queue.infrastructure.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * Lettuce 클라이언트와 연결의 수명을 함께 관리하는 핸들. {@link RedisConnectionFactory#open} 으로 얻는다.
 *
 * <p>Lettuce 연결은 끊기면 자동 재연결한다. 재연결 중 명령은 {@code commandTimeout} 까지 대기 후 실패하며,
 * 어댑터는 이를 {@link com.wbl.openqueue.queue.domain.QueueUnavailableException} 으로 바꿔 던진다.
 */
public final class RedisQueueConnection implements AutoCloseable {

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;

    RedisQueueConnection(RedisClient client, StatefulRedisConnection<String, String> connection) {
        this.client = client;
        this.connection = connection;
    }

    /** 동기 명령 API. */
    public RedisCommands<String, String> commands() {
        return connection.sync();
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }
}
