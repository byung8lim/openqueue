package com.wbl.openqueue.queue.domain;

import java.util.List;

/**
 * 입장 후보 선정 결과.
 *
 * <p>불변 객체다. 멱등 키로 저장되어 재전송 시 그대로 반환되므로, 외부에서 목록을 변경하면
 * 이후 재전송 응답이 오염된다. 따라서 생성 시 방어적 복사(불변 목록)를 수행한다.
 *
 * @param requestId        입장 요청 멱등 키
 * @param admittedSessions 선정된 세션 목록(대기 순서 그대로, 0개 이상)
 */
public record AdmissionResult(AdmissionRequestId requestId, List<WaitingSessionId> admittedSessions) {

    public AdmissionResult {
        IdentifierRules.requireNonNull(requestId, "admission_request_id");
        admittedSessions = List.copyOf(IdentifierRules.requireNonNull(admittedSessions, "admitted_sessions"));
    }
}
