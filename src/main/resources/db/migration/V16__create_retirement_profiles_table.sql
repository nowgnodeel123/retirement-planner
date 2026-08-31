-- D-219: 은퇴 시뮬레이터 입력값 저장. 포트폴리오 메인의 "은퇴 가능 나이" 카드가
-- 위저드를 다시 열지 않고도 계산할 수 있게 하기 위한 사용자 입력 원본이다.
--
-- D-050(파생값 캐싱 금지)과의 관계 — V14 accounts.sort_order와 같은 논리로 무관하다:
-- 여기 담기는 값(월소득·목표생활비·납입액·기대수익률 등)은 transactions에서도,
-- 다른 어디에서도 파생되지 않는 "사용자만 아는 입력"이다.
-- 그리고 파생 가능한 것은 명시적으로 배제한다:
--   * 계산 결과(은퇴 가능 나이, 월 소득 구성, 세금 등)는 컬럼 자체를 만들지 않는다.
--     캐싱하고 싶은 압력은 반드시 생기므로, 넣을 자리를 아예 없애 문을 닫아둔다.
--   * 자산 잔액은 *_manual로 저장하되 조회 시 포트폴리오 프리필이 항상 우선한다.
--     즉 포트폴리오가 아는 자산의 단일 소스는 여전히 transactions이고, 이 컬럼은
--     "앱에 등록하지 않은 계좌"를 사용자가 위저드에 직접 적어둔 값의 보관소일 뿐이다.
--     (저장하지 않으면 미등록 IRP 보유자의 카드가 조용히 0으로 계산돼 은퇴 나이가
--      실제보다 늦게 나온다 — 시뮬레이터에서 가장 나쁜 실패 방식)
CREATE TABLE retirement_profiles (
    id                                    BIGSERIAL PRIMARY KEY,
    user_id                               BIGINT       NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,

    -- 나이는 시간이 지나면 낡는다. 기록 시점을 함께 남겨 조회 시 경과 연수를 더한다.
    -- 카카오 계정은 프로필에 생년월일이 없어(카카오가 관리) 이 경로로만 나이를 안다.
    current_age                           INTEGER      NOT NULL,
    age_as_of                             DATE         NOT NULL,

    monthly_income                        DOUBLE PRECISION NOT NULL,
    target_monthly_expense                DOUBLE PRECISION NOT NULL,

    pension_years_paid                    INTEGER      NOT NULL,
    pension_type                          VARCHAR(10)  NOT NULL,
    years_of_service                      INTEGER      NOT NULL DEFAULT 0,
    national_pension_receipt_type         VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    national_pension_receipt_age          INTEGER,
    military_service_months               INTEGER      NOT NULL DEFAULT 0,
    children_count                        INTEGER      NOT NULL DEFAULT 0,

    monthly_irp_contribution              DOUBLE PRECISION NOT NULL DEFAULT 0,
    monthly_pension_savings_contribution  DOUBLE PRECISION NOT NULL DEFAULT 0,
    monthly_stock_investment              DOUBLE PRECISION NOT NULL DEFAULT 0,

    irp_return_rate                       DOUBLE PRECISION NOT NULL DEFAULT 0.05,
    pension_return_rate                   DOUBLE PRECISION NOT NULL DEFAULT 0.04,
    pension_savings_return_rate           DOUBLE PRECISION NOT NULL DEFAULT 0.06,
    stock_return_rate                     DOUBLE PRECISION NOT NULL DEFAULT 0.07,

    -- 포트폴리오 프리필이 0일 때만 쓰이는 대체값(위 주석 참조). 프리필이 값을 주면 무시된다.
    dc_current_balance_manual             DOUBLE PRECISION NOT NULL DEFAULT 0,
    irp_balance_manual                    DOUBLE PRECISION NOT NULL DEFAULT 0,
    pension_savings_balance_manual        DOUBLE PRECISION NOT NULL DEFAULT 0,
    stock_asset_balance_manual            DOUBLE PRECISION NOT NULL DEFAULT 0,

    use_precise_health_insurance          BOOLEAN      NOT NULL DEFAULT FALSE,
    real_estate_value                     DOUBLE PRECISION NOT NULL DEFAULT 0,
    financial_asset_value                 DOUBLE PRECISION NOT NULL DEFAULT 0,

    created_at                            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                            TIMESTAMP    NOT NULL DEFAULT now()
);
