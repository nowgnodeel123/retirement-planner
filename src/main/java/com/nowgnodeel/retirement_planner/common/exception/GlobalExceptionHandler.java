package com.nowgnodeel.retirement_planner.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 검증 실패(400) 시 Spring 기본 응답 대신 "어떤 필드가 왜 실패했는지"를 그대로 보여준다.
 * WHY: 기본 응답은 status/error/path만 있고 원인이 없어서, 프론트 개발 중
 *      에러가 나도 어느 입력값이 문제인지 매번 수동으로 추적해야 했다.
 */
// @Order가 **필수**다. 아래 Exception.class 핸들러는 무엇이든 받으므로, 이 어드바이스가
// AuthExceptionHandler보다 먼저 조회되면 DuplicateEmailException(409) 같은 구체적 예외까지
// 여기서 500으로 삼켜버린다. Spring은 어드바이스를 순서대로 훑다가 매칭되는 메서드를 가진
// 첫 어드바이스에서 멈추기 때문이다. 순서를 안 주면 둘 다 LOWEST라 사실상 미정의였다.
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "입력값을 다시 확인해주세요.");
        body.put("fields", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "입력값을 다시 확인해주세요.");
        body.put("detail", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 필드 단위 검증으로 잡을 수 없는 필드 간 모순(예: 나이 대비 납입기간 초과)을
     * 서비스 레이어에서 던지면 여기서 400으로 변환한다. (검토 Q-1)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 본문을 객체로 만들기 전에 실패하는 경우 — 깨진 JSON, LocalDate로 못 바꾸는 날짜
     * 문자열("19660315"), enum에 없는 값 등. 필드 단위 검증(@Valid)까지 가지도 못하므로
     * MethodArgumentNotValidException으로는 잡히지 않는다.
     *
     * WHY: 이걸 비워 두면 Spring 기본 응답 {"timestamp","status","error":"Bad Request","path"}가
     * 그대로 나간다. 프론트(lib/api.ts)는 body.message ?? body.error 순으로 메시지를 고르므로
     * 한국어 UI 한복판에 영어 "Bad Request"가 그대로 떴다. 원인 문구는 내부 파서 메시지라
     * 사용자에게 보여줄 수 없으니 로그로만 남긴다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("요청 본문 파싱 실패", ex);
        return error(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해주세요.");
    }

    /** 경로/쿼리 파라미터가 선언 타입으로 안 바뀌는 경우(예: /api/assets/abc/transactions). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("요청 파라미터 타입 불일치: {}", ex.getName());
        return error(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해주세요.");
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * 위에서 못 잡은 모든 예외. 여기가 없으면 Spring 기본 500 응답의
     * "Internal Server Error"가 그대로 사용자 화면에 뜬다(영어 + 내부 용어).
     *
     * 삼키는 게 아니라 **error 레벨로 스택트레이스까지 남기고** 사용자에게는
     * 중립적인 한국어 한 줄만 준다 — 레포 규칙(스택트레이스·내부 클래스명·SQL 노출 금지).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("처리되지 않은 예외", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 문제가 발생했어요. 잠시 후 다시 시도해주세요.");
    }
}
