package com.wbl.openqueue.queue.infrastructure.redis;

import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lua 스크립트를 EVALSHA 로 실행하고 SHA 를 캐시한다.
 *
 * <p>Redis 재시작·failover 로 스크립트 캐시가 비면 {@code NOSCRIPT} 오류가 나므로, 그때 한 번 SCRIPT LOAD 후 재시도한다.
 * 스크립트 본문은 매 호출마다 전송하지 않는다(네트워크 비용). 이 클래스는 연결 하나에 묶여 쓰이며 스레드 안전하지 않다.
 */
final class RedisQueueScripts {

    private final RedisCommands<String, String> redis;
    private final Map<LuaScript, String> shaCache = new EnumMap<>(LuaScript.class);

    RedisQueueScripts(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    /**
     * 스크립트를 실행한다.
     *
     * @param script 실행할 스크립트
     * @param type   Redis 응답을 어떤 Java 타입으로 받을지
     * @param keys   KEYS
     * @param args   ARGV
     * @param <T>    응답 타입
     * @return 스크립트 반환값
     */
    <T> T execute(LuaScript script, ScriptOutputType type, String[] keys, String... args) {
        String sha = shaCache.computeIfAbsent(script, s -> redis.scriptLoad(s.source()));
        try {
            return redis.evalsha(sha, type, keys, args);
        } catch (RedisNoScriptException e) {
            // 서버 스크립트 캐시가 비었다(재시작·failover). 다시 올리고 한 번만 재시도한다.
            String reloaded = redis.scriptLoad(script.source());
            shaCache.put(script, reloaded);
            return redis.evalsha(reloaded, type, keys, args);
        }
    }
}
