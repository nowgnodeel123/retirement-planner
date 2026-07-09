-- M2: 포트폴리오 데이터 모델 (지침 8장 v4.3)
-- D-050: 거래 기반 자산의 수량·평단·손익은 저장하지 않는다 — transactions의 파생값.

CREATE TABLE accounts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    name             VARCHAR(50)  NOT NULL,
    institution_type VARCHAR(20)  NOT NULL,
    detail_type      VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);

CREATE TABLE assets (
    id                  BIGSERIAL PRIMARY KEY,
    account_id          BIGINT       NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    category            VARCHAR(20)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    symbol              VARCHAR(30),
    currency            VARCHAR(3)   NOT NULL DEFAULT 'KRW',
    cash                NUMERIC(18, 2),
    cash_cost           NUMERIC(18, 2),
    source              VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    external_account_id VARCHAR(100),
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_assets_account_id ON assets (account_id);

CREATE TABLE transactions (
    id         BIGSERIAL PRIMARY KEY,
    asset_id   BIGINT        NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    type       VARCHAR(10)   NOT NULL,
    trade_date DATE          NOT NULL,
    quantity   NUMERIC(20, 8) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(20, 4) NOT NULL CHECK (unit_price >= 0),
    fx         NUMERIC(10, 4),
    created_at TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_asset_id_trade_date ON transactions (asset_id, trade_date);

CREATE TABLE dividends (
    id         BIGSERIAL PRIMARY KEY,
    asset_id   BIGINT        NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    pay_date   DATE          NOT NULL,
    amount     NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    fx         NUMERIC(10, 4),
    created_at TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dividends_asset_id_pay_date ON dividends (asset_id, pay_date);

CREATE TABLE deposits (
    id           BIGSERIAL PRIMARY KEY,
    asset_id     BIGINT        NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    deposit_date DATE          NOT NULL,
    amount       NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    created_at   TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_deposits_asset_id_deposit_date ON deposits (asset_id, deposit_date);