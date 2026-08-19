-- 프로필 아바타. 개인정보가 아니라 프론트에 내장된 10종 아이콘 중 하나를 가리키는
-- 인덱스일 뿐이라 암호화 대상이 아니다(D-165 PII 암호화 스코프는 name/phone/birth_date만).
-- 기존 유저는 0(첫 번째 아바타)으로 채워지고, 신규 가입 시 서버가 0~9 중 무작위 배정한다.
ALTER TABLE users ADD COLUMN avatar_id SMALLINT NOT NULL DEFAULT 0;
