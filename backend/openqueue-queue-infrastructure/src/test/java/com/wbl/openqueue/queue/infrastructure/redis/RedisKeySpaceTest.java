package com.wbl.openqueue.queue.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.wbl.openqueue.queue.domain.AdmissionRequestId;
import com.wbl.openqueue.queue.domain.QueueId;
import com.wbl.openqueue.queue.domain.QueueScope;
import com.wbl.openqueue.queue.domain.SiteId;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** QUE-008 key 설계(D-QUE-008-1/2): 한 대기열의 모든 key 는 같은 hash tag 를 공유한다. */
@DisplayName("QUE-008 RedisKeySpace")
class RedisKeySpaceTest {

    private final RedisKeySpace keys = new RedisKeySpace(new QueueScope(new SiteId("shop"), new QueueId("main")));

    @Test
    @DisplayName("AC-QUE-008-1 모든 key 가 oq:{site:queue}: 접두어(hash tag)를 공유한다")
    void allKeysShareHashTag() {
        List<String> all = List.of(keys.sequence(), keys.waiting(), keys.waitingSeen(), keys.admitted(), keys.admissions(),
            keys.sessionPrefix(), keys.session(new WaitingSessionId("sessionA-0000000001")),
            keys.admissionResult(new AdmissionRequestId("request-0001")));

        assertThat(all).allSatisfy(key -> assertThat(key).startsWith("oq:{shop:main}:"));
        assertThat(all).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("세션·멱등 키는 식별자 값을 그대로 붙인다(스크립트가 prefix + id 로 재구성 가능)")
    void sessionAndResultKeys() {
        assertThat(keys.session(new WaitingSessionId("sessionA-0000000001"))).isEqualTo("oq:{shop:main}:session:sessionA-0000000001");
        assertThat(keys.sessionPrefix()).isEqualTo("oq:{shop:main}:session:");
        assertThat(keys.admissionResult(new AdmissionRequestId("request-0001"))).isEqualTo("oq:{shop:main}:admit:request-0001");
    }
}
