package com.wbl.openqueue.queue.domain;

/**
 * 입장 요청의 멱등 키.
 *
 * <p>같은 키로 재전송된 입장 요청은 최초 결과를 그대로 돌려받는다(AC-QUE-001-1).
 * 네트워크 재시도·중복 클릭으로 동일 세션이 중복 입장 처리되는 것을 막는 근거가 된다.
 *
 * @param value 검증된 멱등 키 (base64url, 8~128자)
 * @throws IllegalArgumentException null 이거나 형식이 맞지 않는 경우
 */
public record AdmissionRequestId(String value) {

    public AdmissionRequestId {
        IdentifierRules.requireValid(value, IdentifierRules.REQUEST_ID, "admission_request_id");
    }
}
