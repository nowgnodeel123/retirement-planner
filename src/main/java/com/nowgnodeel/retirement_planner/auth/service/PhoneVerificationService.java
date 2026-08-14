package com.nowgnodeel.retirement_planner.auth.service;

import com.nowgnodeel.retirement_planner.auth.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 휴대전화 인증 서비스. SmsSender가 실제 벤더로 연동되기 전까지는(NoopSmsSender, R-017)
 * 코드를 서버 메모리에만 보관하고 발송 응답에 코드를 그대로 실어 돌려준다(개발용 노출,
 * D-122). SmsSender.isEnabled()가 true가 되는 순간(실제 벤더 연동) sendCode()는 더 이상
 * 코드를 반환하지 않아 API 응답 노출이 자동으로 닫힌다.
 */
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

    private record CodeEntry(String code, Instant expiresAt) {}
    private record VerifiedEntry(Instant expiresAt) {}

    private final SmsSender smsSender;

    private final Map<String, CodeEntry> pendingCodes = new ConcurrentHashMap<>();
    private final Map<String, VerifiedEntry> verifiedPhones = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** 실제 벤더 연동 상태(smsSender.isEnabled())면 null 반환 — 컨트롤러가 devCode를 응답에 담지 않는다. */
    public String sendCode(String phone) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        pendingCodes.put(phone, new CodeEntry(code, Instant.now().plus(CODE_TTL)));
        verifiedPhones.remove(phone);
        smsSender.send(phone, "[네스트] 인증번호는 " + code + "입니다. 5분 내에 입력해주세요.");
        return smsSender.isEnabled() ? null : code;
    }

    public boolean verifyCode(String phone, String code) {
        CodeEntry entry = pendingCodes.get(phone);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return false;
        }
        if (!entry.code().equals(code)) {
            return false;
        }
        pendingCodes.remove(phone);
        verifiedPhones.put(phone, new VerifiedEntry(Instant.now().plus(VERIFIED_TTL)));
        return true;
    }

    public boolean isVerified(String phone) {
        VerifiedEntry entry = verifiedPhones.get(phone);
        return entry != null && Instant.now().isBefore(entry.expiresAt());
    }

    public void consume(String phone) {
        verifiedPhones.remove(phone);
    }
}
