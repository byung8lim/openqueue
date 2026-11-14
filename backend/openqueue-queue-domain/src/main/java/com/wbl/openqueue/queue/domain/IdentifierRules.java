package com.wbl.openqueue.queue.domain;

import java.util.regex.Pattern;

/**
 * 도메인 식별자 형식 검증 규칙 (SRS D-QUE-001-3, SEC-002).
 *
 * <p>왜 필요한가: 식별자는 이후 Redis key namespace, 로그, URL 경로에 그대로 사용된다.
 * 허용 문자를 좁게 제한해 key 충돌, 경로 조작(path traversal), 로그 인젝션(개행 삽입)을
 * 입력 단계에서 차단한다.
 *
 * <p>보안 주의: 검증 실패 메시지에는 입력 원문을 절대 포함하지 않는다(SEC-001).
 * 공격자가 넣은 개행·제어문자가 예외 메시지를 통해 로그에 기록되는 것을 막기 위함이다.
 */
final class IdentifierRules {

    /** site_id, queue_id: 소문자/숫자로 시작, 소문자/숫자/하이픈, 최대 64자. */
    static final Pattern SCOPE_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    /** waiting_session_id: 추측 불가능한 opaque 값(base64url), 16~128자. */
    static final Pattern SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");

    /** admission_request_id: 멱등 키(base64url), 8~128자. */
    static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");

    private IdentifierRules() {
    }

    /**
     * 값이 null이 아니고 패턴과 전체 일치하는지 검증한다.
     *
     * @param value     검증할 외부 입력
     * @param pattern   허용 형식
     * @param fieldName 오류 메시지에 사용할 필드명(입력값이 아님)
     * @return 검증을 통과한 원래 값
     * @throws IllegalArgumentException null 이거나 형식이 맞지 않는 경우
     */
    static String requireValid(String value, Pattern pattern, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 값이 없습니다.");
        }
        if (!pattern.matcher(value).matches()) {
            // 입력 원문은 메시지에 포함하지 않는다(로그 인젝션 방지).
            throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다.");
        }
        return value;
    }

    /**
     * null 이 아닌지 검증한다. 도메인 규칙상 NPE 대신 입력 오류로 통일하기 위해 사용한다.
     *
     * @param value     검증할 값
     * @param fieldName 오류 메시지에 사용할 필드명
     * @param <T>       값 타입
     * @return 원래 값
     * @throws IllegalArgumentException 값이 null 인 경우
     */
    static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 값이 없습니다.");
        }
        return value;
    }
}
