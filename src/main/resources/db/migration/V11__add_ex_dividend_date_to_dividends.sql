-- 배당락일(ex-dividend date) 수동 입력 필드. 자동조회(OpenDART/data.go.kr)는
-- 라이선스 문제(공공누리 2유형, 상업적 이용금지)로 실사용자 노출 안 함 확정 —
-- 대신 사용자가 알고 있으면 직접 입력하는 선택 필드로 대체(R-018 종결).
ALTER TABLE dividends ADD COLUMN ex_dividend_date DATE;
