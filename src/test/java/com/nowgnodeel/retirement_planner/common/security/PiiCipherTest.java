package com.nowgnodeel.retirement_planner.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiCipherTest {

    private PiiCipher piiCipher;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) i;
        piiCipher = new PiiCipher(Base64.getEncoder().encodeToString(key));
    }

    @Test
    @DisplayName("암호화한 값을 그대로 복호화하면 원문이 나온다")
    void encryptThenDecrypt_roundTrips() {
        String plaintext = "01012345678";

        String encrypted = piiCipher.encrypt(plaintext);
        String decrypted = piiCipher.decrypt(encrypted);

        assertThat(encrypted).startsWith("enc:v1:").doesNotContain(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("같은 평문을 여러 번 암호화하면 매번 다른 암호문이 나온다 (랜덤 IV)")
    void sameInputEncryptedTwice_producesDifferentCiphertext() {
        String plaintext = "홍길동";

        String first = piiCipher.encrypt(plaintext);
        String second = piiCipher.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(piiCipher.decrypt(first)).isEqualTo(plaintext);
        assertThat(piiCipher.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("마이그레이션 전 평문(enc:v1: 접두사 없음)은 decrypt에서 그대로 통과한다")
    void decrypt_passesThroughUnmigratedPlaintext() {
        assertThat(piiCipher.decrypt("01012345678")).isEqualTo("01012345678");
        assertThat(piiCipher.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("hmac은 같은 입력에 항상 같은 값을 반환한다 (조회용 결정적 해시)")
    void hmac_isDeterministic() {
        String hash1 = piiCipher.hmac("01012345678");
        String hash2 = piiCipher.hmac("01012345678");
        String hash3 = piiCipher.hmac("01099998888");

        assertThat(hash1).isEqualTo(hash2).isNotEqualTo(hash3);
        assertThat(hash1).hasSize(64); // SHA-256 hex
    }

    @Test
    @DisplayName("isEncrypted는 enc:v1: 접두사 유무만 확인한다")
    void isEncrypted_checksPrefix() {
        assertThat(piiCipher.isEncrypted(piiCipher.encrypt("x"))).isTrue();
        assertThat(piiCipher.isEncrypted("plain-value")).isFalse();
        assertThat(piiCipher.isEncrypted(null)).isFalse();
    }

    @Test
    @DisplayName("32바이트가 아닌 키로 생성하면 즉시 실패한다")
    void constructor_rejectsWrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new PiiCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
