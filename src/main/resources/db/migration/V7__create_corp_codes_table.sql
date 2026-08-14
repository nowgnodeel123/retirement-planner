CREATE TABLE corp_codes (
    stock_code VARCHAR(10) PRIMARY KEY,   -- KRX 단축코드(OpenDART corpCode.xml 기준)
    corp_code  VARCHAR(8)  NOT NULL,      -- OpenDART 고유번호
    corp_name  VARCHAR(200) NOT NULL,
    jurir_no   VARCHAR(13),               -- 법인등록번호. company.json 조회 전까지 NULL
    updated_at TIMESTAMP   NOT NULL
);
