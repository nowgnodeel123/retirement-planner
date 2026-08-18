package com.nowgnodeel.retirement_planner.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// M14: birthDate 컬럼 암호화. DB 컬럼은 DATE에서 VARCHAR(255)로 변경됐다(V9,
// 기존 값은 USING birth_date::text로 ISO-8601 문자열로 보존) — 암호화된 값은
// 더 이상 날짜 타입 제약을 만족할 수 없어 타입 자체를 바꿔야 했다.
@Converter
@Component
@RequiredArgsConstructor
public class EncryptedLocalDateConverter implements AttributeConverter<LocalDate, String> {

    private final PiiCipher piiCipher;

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : piiCipher.encrypt(attribute.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        String decrypted = piiCipher.decrypt(dbData);
        return decrypted == null ? null : LocalDate.parse(decrypted);
    }
}
