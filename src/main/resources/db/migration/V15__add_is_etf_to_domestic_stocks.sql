-- 연금저축·IRP는 세제혜택 계좌라 개별주는 못 사고 ETF·펀드 등 지정 상품만 거래할 수 있다(D-198).
-- 그 판정을 하려면 종목이 ETF인지 알아야 하는데, 지금 캐시를 채우는 KRX상장종목정보 API는
-- 주권만 내려주고 ETF는 아예 들어있지 않았다(로컬 2,758건 중 KODEX/TIGER 0건으로 확인).
-- ETF는 별도 서비스(금융위원회_증권상품시세정보)에서 받아 같은 테이블에 이 플래그로 구분한다.
ALTER TABLE domestic_stocks ADD COLUMN is_etf BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_domestic_stocks_is_etf ON domestic_stocks (is_etf);
