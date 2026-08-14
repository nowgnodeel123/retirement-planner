package com.nowgnodeel.retirement_planner.auth.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 네이버클라우드 SENS SMS v2 연동(R-017 실제 벤더 구현). sms.sens.enabled=true(SENS_ENABLED
 * 환경변수)일 때만 이 빈이 활성화되고, PhoneVerificationService는 isEnabled()=true를 확인해
 * 인증번호를 API 응답에 더 이상 노출하지 않는다.
 *
 * 활성화 방법: 환경변수 SENS_ENABLED=true, SENS_ACCESS_KEY, SENS_SECRET_KEY, SENS_SERVICE_ID,
 * SENS_SENDER_PHONE(NCP 콘솔에 사전등록된 발신번호, 통신사 심의 필요) 설정.
 *
 * 주의: 아직 실제 키로 검증되지 않은 상태다(계약 전이라 키가 없음) — 벤더 계약 후 최소 1건
 * 실제 발송으로 검증 필요(백엔드 CLAUDE.md "외부 API 연동: 실제 호출 1회 검증" 원칙).
 * API 스펙 출처: 공식 문서 접속 불가로 공개된 예제(velog 등)를 근거로 작성 — 검증 전까지는
 * 신뢰도가 이 레포의 다른 확정 연동(data.go.kr/Finnhub/Upbit/한국수출입은행)과 다르다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sms.sens", name = "enabled", havingValue = "true")
public class NaverCloudSensSmsSender implements SmsSender {

    private static final String URI_PATH_TEMPLATE = "/sms/v2/services/%s/messages";

    private final RestClient restClient = RestClient.create("https://sens.apigw.ntruss.com");

    @Value("${sms.sens.access-key}")
    private String accessKey;

    @Value("${sms.sens.secret-key}")
    private String secretKey;

    @Value("${sms.sens.service-id}")
    private String serviceId;

    @Value("${sms.sens.sender-phone}")
    private String senderPhone;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(String phone, String message) {
        String uriPath = String.format(URI_PATH_TEMPLATE, serviceId);
        long timestamp = System.currentTimeMillis();

        Map<String, Object> body = Map.of(
                "type", "SMS",
                "contentType", "COMM",
                "countryCode", "82",
                "from", senderPhone,
                "content", message,
                "messages", List.of(Map.of("to", phone))
        );

        try {
            restClient.post()
                    .uri(uriPath)
                    .header("x-ncp-apigw-timestamp", String.valueOf(timestamp))
                    .header("x-ncp-iam-access-key", accessKey)
                    .header("x-ncp-apigw-signature-v2", sign(uriPath, timestamp))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // 발송 실패해도 인증 흐름 자체를 막지 않는다 — 수신자 번호는 로그에 남기지 않는다.
            log.warn("SENS SMS 발송 실패", e);
        }
    }

    private String sign(String uriPath, long timestamp) {
        try {
            String message = "POST" + "\n" + uriPath + "\n" + timestamp + "\n" + accessKey;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("SENS 서명 생성 실패", e);
        }
    }
}
