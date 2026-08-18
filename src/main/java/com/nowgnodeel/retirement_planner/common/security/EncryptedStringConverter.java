package com.nowgnodeel.retirement_planner.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// M14: name/phone 등 문자열 PII 컬럼용. @Component로 등록해야 Spring이 관리하는
// PiiCipher를 생성자 주입받을 수 있다(Hibernate가 기본 생성자로 직접 new하지 않고
// Spring 빈 컨테이너에서 조회하도록 위임).
@Converter
@Component
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final PiiCipher piiCipher;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return piiCipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return piiCipher.decrypt(dbData);
    }
}
