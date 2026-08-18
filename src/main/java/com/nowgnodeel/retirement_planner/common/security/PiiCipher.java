package com.nowgnodeel.retirement_planner.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

// M14: PII(휴대전화/이름/생년월일) 컬럼 암호화 — AES-256-GCM(랜덤 IV, 매 호출마다 다른 암호문).
// "enc:v1:" 접두사로 이미 암호화된 값을 식별한다 — 마이그레이션 전 평문 데이터를 읽을 때
// (decrypt에서) 그대로 통과시키기 위한 과도기 대응이자, 재실행 시 이미 암호화된 값을
// 다시 암호화하지 않도록 하는 멱등성 마커이기도 하다(PiiMigrationRunner 참고).
// hmac()은 phone처럼 등호 조회가 필요한 필드의 조회 전용 결정적 해시 — 같은 값이면
// 항상 같은 해시가 나오므로(암호문과 달리) phone_hash 컬럼에 저장해 조회/유니크 제약에 쓴다.
@Component
public class PiiCipher {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    static final String VERSION_PREFIX = "enc:v1:";

    private final SecretKeySpec keySpec;
    private final byte[] rawKey;
    private final SecureRandom random = new SecureRandom();

    public PiiCipher(@Value("${pii.encryption-key}") String base64Key) {
        this.rawKey = Base64.getDecoder().decode(base64Key);
        if (rawKey.length != 32) {
            throw new IllegalStateException(
                    "PII_ENCRYPTION_KEY는 base64로 인코딩된 32바이트(256비트) AES 키여야 합니다. 실제 길이: " + rawKey.length);
        }
        this.keySpec = new SecretKeySpec(rawKey, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PII 암호화 실패", e);
        }
    }

    // WHY 평문 통과: V9 마이그레이션 직후 ~ PiiMigrationRunner 실행 전 사이에는 DB에
    // 아직 평문(또는 DATE→VARCHAR 캐스트만 된 값)이 남아있다. 이 구간에서도 로그인 등
    // 정상 조회가 깨지면 안 되므로, "enc:v1:" 접두사가 없으면 저장된 그대로 반환한다.
    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(VERSION_PREFIX)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PII 복호화 실패(키 불일치 또는 손상된 데이터)", e);
        }
    }

    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(VERSION_PREFIX);
    }

    public String hmac(String value) {
        if (value == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(rawKey, "HmacSHA256"));
            byte[] result = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 계산 실패", e);
        }
    }
}
