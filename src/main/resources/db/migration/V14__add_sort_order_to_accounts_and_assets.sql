-- 사용자가 직접 끌어서 정한 표시 순서.
-- 금액순·수익률순·이름순은 기존처럼 조회 시점에 계산하는 파생 정렬이고,
-- 이 컬럼은 그 방식으로는 만들어낼 수 없는 사용자 입력 원본이다(D-050 파생값 캐싱 금지와 무관).
-- NULL = 아직 순서를 지정한 적 없음 → 목록에서 지정된 것들 뒤로 보낸다.
ALTER TABLE accounts ADD COLUMN sort_order INTEGER;
ALTER TABLE assets   ADD COLUMN sort_order INTEGER;
