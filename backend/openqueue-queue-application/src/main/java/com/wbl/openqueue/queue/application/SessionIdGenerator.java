package com.wbl.openqueue.queue.application;

import com.wbl.openqueue.queue.domain.AdmissionRequestId;
import com.wbl.openqueue.queue.domain.WaitingSessionId;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 추측 불가능한 세션 ID·입장 요청 멱등 키 생성기 (SEC-002, D-QUE-001-3 형식).
 *
 * <p>세션 ID 소지가 곧 세션 소유 권한이므로(QUE-003 보안 고려사항) 256bit 난수를 base64url 로 인코딩한다(43자, 패딩 없음).
 * 순차 값·시각·클라이언트 정보는 섞지 않는다(추측·열거 방지).
 */
public final class SessionIdGenerator {

    private static final int SESSION_ID_BYTES = 32;
    private static final int REQUEST_ID_BYTES = 16;

    private final SecureRandom random;

    /** 기본 {@link SecureRandom} 사용. */
    public SessionIdGenerator() {
        this(new SecureRandom());
    }

    /**
     * @param random 난수원(테스트에서 주입)
     */
    public SessionIdGenerator(SecureRandom random) {
        this.random = random;
    }

    /** 새 대기 세션 ID. */
    public WaitingSessionId newSessionId() {
        return new WaitingSessionId(randomToken(SESSION_ID_BYTES));
    }

    /** 새 입장 요청 멱등 키(입장 주기 1회에 1개). */
    public AdmissionRequestId newAdmissionRequestId() {
        return new AdmissionRequestId(randomToken(REQUEST_ID_BYTES));
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
