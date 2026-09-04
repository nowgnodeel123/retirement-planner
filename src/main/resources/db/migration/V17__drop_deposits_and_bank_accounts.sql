-- 은행 계좌 유형 제거.
--
-- 배경: 은행 계좌는 예적금 입력을 전제로 만들어졌으나(D-060) 실제로는 컨트롤러·서비스·
-- 화면이 한 번도 구현되지 않았다. deposits 테이블은 삽입 이력이 0건이고, 은행 계좌는
-- allowedCategories가 빈 배열이라 자산을 아무것도 담을 수 없는 빈 껍데기였다.
-- 적금 만기·금리 계산을 정확히 하려면 상품별 이율·복리주기·과세를 다뤄야 하는데,
-- 그건 이 앱의 범위가 아니라고 판단해 기능 자체를 접는다.

-- 은행 계좌가 남아 있으면 InstitutionType enum 파싱이 실패한다(BANK 값 제거됨).
-- assets/transactions/dividends/deposits는 V2의 ON DELETE CASCADE로 함께 정리된다.
DELETE FROM accounts WHERE institution_type = 'BANK';

DROP TABLE IF EXISTS deposits;
