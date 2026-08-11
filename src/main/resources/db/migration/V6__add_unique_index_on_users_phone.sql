-- 카카오 가입자는 phone이 NULL이라 부분 유니크 인덱스로 NULL은 허용하고 값이 있는 경우만 유일하게 강제한다.
CREATE UNIQUE INDEX uk_users_phone ON users (phone) WHERE phone IS NOT NULL;
