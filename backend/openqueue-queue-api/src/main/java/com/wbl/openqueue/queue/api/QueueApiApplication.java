package com.wbl.openqueue.queue.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Queue Engine 공개 API 프로세스 (DMZ). 대기 등록·조회·heartbeat·취소, SSE 상태 스트림, 입장 주기 스케줄러를 제공한다.
 *
 * <p>이 프로세스는 MariaDB·관리 API 를 포함하지 않는다(개발요청서 §4.2). 정책은 설정(Phase 3)으로 주입되며 Phase 6 에서 서명 스냅샷으로 대체된다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class QueueApiApplication {

    /** Spring 이 설정 클래스로 인스턴스화할 수 있도록 protected 로 둔다(유틸리티 클래스 규칙 충족). */
    protected QueueApiApplication() {
    }

    /** 진입점. */
    public static void main(String[] args) {
        SpringApplication.run(QueueApiApplication.class, args);
    }
}
