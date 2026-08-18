package com.nowgnodeel.retirement_planner.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// M14: @AuditLogging 붙은 서비스 메서드를 감싸 성공한 호출을 audit_logs에 남긴다.
// 실패(예외)한 시도는 이번 슬라이스 범위 밖 — proceed()가 던지면 그대로 전파하고
// 로그는 남기지 않는다. 요청 인자/반환값을 JSON으로 통째로 직렬화해 저장하므로
// (엔티티별 필드명이 제각각이라 범용 "변경 전/후 diff"는 만들지 않았다) 정확한
// before/after 컬럼 비교가 필요하면 이 JSON을 파싱해야 한다 — v1 스코프의 의도적 한계.
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLogging)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLogging auditLogging) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            AuditLog log = AuditLog.builder()
                    .userId(currentUserId())
                    .action(auditLogging.action())
                    .entityType(auditLogging.entityType())
                    .requestSnapshot(serialize(joinPoint.getArgs()))
                    .resultSnapshot(serialize(result))
                    .ipAddress(currentIp())
                    .build();
            auditLogRepository.save(log);
        } catch (Exception e) {
            // WHY: 감사 로그 저장 실패가 실제 비즈니스 트랜잭션(이미 커밋됐거나 커밋 진행 중)을
            // 막으면 안 된다 — 로깅만 하고 삼킨다.
            log.warn("감사 로그 저장 실패: {} {}", auditLogging.entityType(), auditLogging.action(), e);
        }

        return result;
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }

    private String serialize(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "직렬화 실패: " + e.getMessage();
        }
    }
}
