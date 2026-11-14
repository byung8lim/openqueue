package com.wbl.openqueue.queue.infrastructure.redis;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 대기열 연산 Lua 스크립트 목록. 본문은 클래스패스 {@code /lua/*.lua} 에 두고 Java 문자열로 하드코딩하지 않는다
 * (SQL 분리 원칙을 Redis 스크립트에도 적용, 개발요청서 §6).
 *
 * <p>입력·출력·원자성 계약: docs/database/redis-keys.md §3
 */
public enum LuaScript {
    /** 등록·재접속·재진입. */
    REGISTER("register.lua"),
    /** 활동 신호(대기 TTL·lease 연장). */
    HEARTBEAT("heartbeat.lua"),
    /** 대기 포기·입장 포기. */
    CANCEL("cancel.lua"),
    /** 대기 TTL 만료 처리. */
    EXPIRE_WAITING("expire_waiting.lua"),
    /** 입장 lease 만료 처리. */
    EXPIRE_LEASES("expire_leases.lua"),
    /** 입장 후보 선정(회수·상한·입장률·멱등 포함). */
    ADMIT("admit.lua");

    private final String resourceName;
    private final String source;

    LuaScript(String resourceName) {
        this.resourceName = resourceName;
        this.source = load(resourceName);
    }

    /** 클래스패스 리소스 파일명. */
    public String resourceName() {
        return resourceName;
    }

    /** 스크립트 본문(UTF-8). */
    public String source() {
        return source;
    }

    private static String load(String name) {
        try (InputStream in = LuaScript.class.getResourceAsStream("/lua/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Lua 스크립트 리소스를 찾을 수 없습니다: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Lua 스크립트를 읽을 수 없습니다: " + name, e);
        }
    }
}
