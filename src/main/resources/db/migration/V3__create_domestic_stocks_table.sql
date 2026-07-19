CREATE TABLE domestic_stocks (
    symbol_code VARCHAR(10) PRIMARY KEY,   -- KRX 단축코드
    name        VARCHAR(100) NOT NULL,
    market      VARCHAR(10)  NOT NULL,     -- KOSPI / KOSDAQ
    updated_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_domestic_stocks_name ON domestic_stocks (name);