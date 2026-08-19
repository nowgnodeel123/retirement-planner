-- V12에서 avatar_id를 SMALLINT로 만들었는데, User 엔티티 필드가 Integer라 Hibernate
-- 스키마 검증(ddl-auto=validate)이 INTEGER를 기대해 부팅 시 SchemaManagementException으로
-- 즉시 깨졌다(V12는 이미 적용된 마이그레이션이라 직접 수정하지 않고 새 버전으로 고친다).
ALTER TABLE users ALTER COLUMN avatar_id TYPE INTEGER;
