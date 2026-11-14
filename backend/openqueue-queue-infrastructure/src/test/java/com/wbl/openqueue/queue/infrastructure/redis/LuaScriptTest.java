package com.wbl.openqueue.queue.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lua 스크립트가 리소스 파일에서 로드되고(SQL 분리 원칙 적용) 계약 주석을 갖는지 확인한다. */
@DisplayName("QUE-008 Lua 스크립트 리소스")
class LuaScriptTest {

    @Test
    @DisplayName("6개 스크립트가 모두 로드되고 KEYS/ARGV 계약 주석과 반환 설명을 포함한다")
    void allScriptsLoadWithContractHeader() {
        assertThat(LuaScript.values()).hasSize(6);
        for (LuaScript script : LuaScript.values()) {
            assertThat(script.source()).as(script.resourceName()).isNotBlank().contains("KEYS[1]").contains("ARGV[1]").contains("반환");
        }
    }

    @Test
    @DisplayName("admit 스크립트는 멱등 확인 → lease 회수 → 대기 만료 → 입장률 → 선정 순서를 따른다")
    void admitScriptOrdersStepsLikeDomainModel() {
        String source = LuaScript.ADMIT.source();
        int idempotency = source.indexOf("redis.call('GET', KEYS[5])");
        int lease = source.indexOf("expire_members(KEYS[3]");
        int waiting = source.indexOf("expire_members(KEYS[2]");
        int rate = source.indexOf("ZREMRANGEBYSCORE");
        int select = source.indexOf("redis.call('ZRANGE', KEYS[1]");

        assertThat(idempotency).isLessThan(lease);
        assertThat(lease).isLessThan(waiting);
        assertThat(waiting).isLessThan(rate);
        assertThat(rate).isLessThan(select);
    }
}
