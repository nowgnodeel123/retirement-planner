package com.nowgnodeel.retirement_planner.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// M14: V9(스키마만 변경)가 적용된 뒤, 기존 평문 name/phone/birth_date를 1회성으로
// 암호화하고 phone_hash를 채운다. SQL만으로는 애플리케이션이 관리하는 키로 암호화할 수
// 없어 Flyway 마이그레이션이 아니라 별도 러너로 뺐다 — Flyway 적용분은 절대 수정하지 않는다는
// 원칙(CLAUDE.md)과, "암호화 로직 변경 시 과거 마이그레이션을 고쳐야 하는" 상황을 피하기 위함.
// 이미 암호화된("enc:v1:" 접두사) 값은 건너뛰므로 여러 번 실행해도 안전(멱등)하다.
// 기본 비활성 — pii.migration.enabled=true(PII_MIGRATION_ENABLED)로 명시적으로 켜야 실행된다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pii.migration.enabled", havingValue = "true")
public class PiiMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PiiCipher piiCipher;

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList("SELECT id, name, phone, birth_date, phone_hash FROM users");

        int migrated = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String name = (String) row.get("name");
            String phone = (String) row.get("phone");
            String birthDate = (String) row.get("birth_date");
            String existingHash = (String) row.get("phone_hash");

            boolean needsUpdate = false;
            String newName = name;
            String newPhone = phone;
            String newBirthDate = birthDate;
            String newHash = existingHash;

            if (name != null && !piiCipher.isEncrypted(name)) {
                newName = piiCipher.encrypt(name);
                needsUpdate = true;
            }
            if (phone != null && !piiCipher.isEncrypted(phone)) {
                newHash = piiCipher.hmac(phone); // 평문 상태에서 해시를 먼저 뽑아야 한다
                newPhone = piiCipher.encrypt(phone);
                needsUpdate = true;
            }
            if (birthDate != null && !piiCipher.isEncrypted(birthDate)) {
                newBirthDate = piiCipher.encrypt(birthDate);
                needsUpdate = true;
            }

            if (needsUpdate) {
                jdbcTemplate.update(
                        "UPDATE users SET name = ?, phone = ?, phone_hash = ?, birth_date = ? WHERE id = ?",
                        newName, newPhone, newHash, newBirthDate, id);
                migrated++;
            }
        }
        log.info("PII 마이그레이션 완료 — 대상 {}건 중 {}건 암호화", rows.size(), migrated);
    }
}
