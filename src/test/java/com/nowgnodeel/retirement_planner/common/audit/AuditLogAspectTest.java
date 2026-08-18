package com.nowgnodeel.retirement_planner.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock ProceedingJoinPoint joinPoint;
    @InjectMocks AuditLogAspect aspect;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private record DummyResponse(Long id, String name) {}

    @Test
    @DisplayName("성공한 호출은 요청 인자/반환값을 JSON으로 감사 로그에 남긴다")
    void successfulCall_savesAuditLog() throws Throwable {
        // ObjectMapper는 @InjectMocks가 Mockito 목이 아닌 실제 필드 주입을 요구하므로 reflection으로 세팅
        org.springframework.test.util.ReflectionTestUtils.setField(aspect, "objectMapper", objectMapper);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, Collections.emptyList()));

        Object[] args = {7L, "요청바디"};
        DummyResponse response = new DummyResponse(99L, "생성된계좌");
        given(joinPoint.getArgs()).willReturn(args);
        given(joinPoint.proceed()).willReturn(response);

        AuditLogging annotation = annotation(AuditAction.CREATE, "Account");
        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(saved.getEntityType()).isEqualTo("Account");
        assertThat(saved.getRequestSnapshot()).contains("요청바디");
        assertThat(saved.getResultSnapshot()).contains("생성된계좌").contains("99");
    }

    @Test
    @DisplayName("실패(예외)한 호출은 감사 로그를 남기지 않고 예외를 그대로 전파한다")
    void failedCall_propagatesException_withoutSavingLog() throws Throwable {
        org.springframework.test.util.ReflectionTestUtils.setField(aspect, "objectMapper", objectMapper);
        given(joinPoint.proceed()).willThrow(new IllegalStateException("boom"));

        AuditLogging annotation = annotation(AuditAction.DELETE, "Account");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> aspect.around(joinPoint, annotation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(auditLogRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("인증 정보가 없으면(비로그인 컨텍스트) userId는 null로 기록된다")
    void noAuthentication_recordsNullUserId() throws Throwable {
        org.springframework.test.util.ReflectionTestUtils.setField(aspect, "objectMapper", objectMapper);
        given(joinPoint.getArgs()).willReturn(new Object[]{});
        given(joinPoint.proceed()).willReturn(null);

        aspect.around(joinPoint, annotation(AuditAction.UPDATE, "Account"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    private AuditLogging annotation(AuditAction action, String entityType) {
        return new AuditLogging() {
            @Override
            public AuditAction action() {
                return action;
            }

            @Override
            public String entityType() {
                return entityType;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return AuditLogging.class;
            }
        };
    }
}
