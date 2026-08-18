package com.nowgnodeel.retirement_planner.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// M14: 계좌/자산/배당처럼 금융 데이터를 생성·수정·삭제하는 서비스 메서드에 붙이면
// AuditLogAspect가 요청 인자와 반환값을 감사 로그(audit_logs)에 남긴다.
// 성공한 호출만 기록한다(실패 시도 로깅은 이번 슬라이스 범위 밖, 백로그).
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditLogging {
    AuditAction action();
    String entityType();
}
