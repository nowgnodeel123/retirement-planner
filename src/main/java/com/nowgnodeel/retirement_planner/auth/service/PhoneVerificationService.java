package com.nowgnodeel.retirement_planner.auth.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 휴대전화 인증 목업(스텁) 서비스. 실제 SMS 발송 벤더가 아직 없어 코드를
 * 서버 메모리에만 보관하고, 발송 응답에 코드를 그대로 실어 돌려준다
 * (개발용 노출 — 실제 SMS 연동 전까지의 임시 조치, 재부팅 시 초기화됨).
 */
@Service
public class PhoneVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

    private record CodeEntry(String code, Instant expiresAt) {}
    private record VerifiedEntry(Instant expiresAt) {}

    private final Map<String, CodeEntry> pendingCodes = new ConcurrentHashMap<>();
    private final Map<String, VerifiedEntry> verifiedPhones = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String sendCode(String phone) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        pendingCodes.put(phone, new CodeEntry(code, Instant.now().plus(CODE_TTL)));
        verifiedPhones.remove(phone);
        return code;
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
