package com.nowgnodeel.retirement_planner.auth.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제 SMS 벤더가 연동되기 전까지의 기본 구현(R-017). 발송하지 않고 로그만 남긴다 —
 * 인증번호 자체는 PhoneVerificationService가 API 응답에 노출하므로 이 클래스는 값을 다루지 않는다.
 * sms.sens.enabled=true(SENS_ENABLED 환경변수)가 설정되면 {@link NaverCloudSensSmsSender}가
 * 대신 활성화되고 이 빈은 등록되지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sms.sens", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopSmsSender implements SmsSender {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void send(String phone, String message) {
        log.debug("SMS 미연동 상태 — 실제 발송 생략(수신자 번호는 로그에 남기지 않음)");
    }
}
