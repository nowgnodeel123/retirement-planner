package com.nowgnodeel.retirement_planner.controller;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 검증 실패(400) 시 Spring 기본 응답 대신 "어떤 필드가 왜 실패했는지"를 그대로 보여준다.
 * WHY: 기본 응답은 status/error/path만 있고 원인이 없어서, 프론트 개발 중
 *      에러가 나도 어느 입력값이 문제인지 매번 수동으로 추적해야 했다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    // controller/GlobalExceptionHandler.java 에 핸들러 1개 추가
    @ExceptionHandler(com.nowgnodeel.retirement_planner.common.exception.NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            com.nowgnodeel.retirement_planner.common.exception.NotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}