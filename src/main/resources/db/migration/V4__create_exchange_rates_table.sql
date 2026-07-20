-- src/main/resources/db/migration/V4__create_exchange_rates_table.sql

CREATE TABLE exchange_rates (
    currency_code VARCHAR(10)    PRIMARY KEY,   -- 예: USD (MVP는 USD만 사용)
    deal_bas_r    NUMERIC(12,4)  NOT NULL,       -- 매매기준율(원화 환산 기준)
    base_date     DATE           NOT NULL,       -- API가 실제 반환한 기준일자(오늘이 아닐 수 있음)
    updated_at    TIMESTAMP      NOT NULL
);