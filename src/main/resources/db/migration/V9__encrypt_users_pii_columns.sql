-- M14: name/phone/birth_date를 애플리케이션 레벨 AES-256-GCM으로 암호화하기 위한 스키마 변경.
-- 이 마이그레이션은 스키마만 바꾼다 — 기존 값의 실제 암호화는 PiiMigrationRunner(Java, 1회성)가 담당한다
-- (SQL로는 애플리케이션이 관리하는 암호화 키를 쓸 수 없다).

ALTER TABLE users
    ALTER COLUMN name TYPE VARCHAR(255),
    ALTER COLUMN phone TYPE VARCHAR(255),
    ALTER COLUMN birth_date TYPE VARCHAR(255) USING birth_date::text;

-- phone은 이제 랜덤 IV로 암호화되어 같은 번호도 매번 다른 값이 저장되므로
-- phone 컬럼 자체로는 중복 조회/제약이 불가능하다. 조회 전용 결정적 해시 컬럼을 추가한다.
ALTER TABLE users ADD COLUMN phone_hash VARCHAR(64);

DROP INDEX uk_users_phone;
CREATE UNIQUE INDEX uk_users_phone_hash ON users (phone_hash) WHERE phone_hash IS NOT NULL;
