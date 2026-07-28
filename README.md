# 🏦 네스트 (Nest) — Retirement Planner

> 지금 자산으로 은퇴 시점에 얼마나 준비되는지 역산해서 확인하는, 20~40대 직장인을 위한 개인 재무 관리 앱

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-schema--managed-CC0200?style=flat&logo=flyway&logoColor=white)](https://flywaydb.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

<br>

## 📌 프로젝트 소개

도미노, 더리치, 뱅크샐러드 같은 기존 앱들이 "지금 내 자산이 얼마인지" **조회**를 보여준다면, 네스트는 국민연금·퇴직연금(DB/DC)·IRP·연금저축·주식/ETF·암호화폐 자산을 통합해서 **"나는 몇 살에 은퇴할 수 있을까?"** 라는 질문에 직접 **답**합니다. (도미노·더리치=데이터, 네스트=답)

은퇴 나이를 입력받는 게 아니라 **역산으로 계산**합니다. 20대~74세 사이 모든 나이를 하나씩 검증해서, 목표 생활비를 끝까지(90세) 감당할 수 있는 **가장 이른 나이**를 찾습니다.

포트폴리오(자산관리) 탭은 이 역산 계산에 필요한 입력값을 정확하게 채우기 위한 인프라이며, 계좌·매수/매도 거래·배당·시세·환율 연동과 수익/세금 조회를 포함합니다.

개발 6개월 MVP 전 마일스톤(M1~M13)이 완료된 상태이며, 솔로 개발자의 이직 포트폴리오 겸 실사용 앱으로 진행되었습니다.

<br>

## 🎯 핵심 기능

### 은퇴 시뮬레이터 (로그인 필요)
| 기능 | 설명 |
|------|------|
| 은퇴 가능 나이 자동 계산 | 입력하지 않고, 자산·소득 조건으로 역산 |
| 3구간 Gap-Filling 시뮬레이션 | 아래 "계산 로직" 참고 |
| 국민연금 | 조기/정상/연기수령, 물가연동, 군복무·자녀 가입기간 추가 |
| 퇴직연금 DB/DC | DB형(최종월급×근속연수), DC형(기존잔액+매년적립 복리) |
| IRP·연금저축 | 세액공제 한도·구간별 공제율 반영 |
| 주식/ETF | 양도소득세(22%, 연 250만원 공제) gross-up 인출 |
| 연도별 소득 타임라인 | 은퇴~90세까지 1년 단위 소득 구성 (`incomeTimeline`) |
| 입력값 모순 검증 | 나이 대비 과도한 납입기간 등 필드 간 정합성 체크 |

### 로그인/인증
| 기능 | 설명 |
|------|------|
| 카카오 소셜 로그인 | Spring Security OAuth2 Client 기반, 닉네임/이메일 동의항목 연동 |
| 이메일 회원가입/로그인 | BCrypt 암호화, 이메일 중복 검증 |
| JWT 인증 | 액세스 토큰 7일 단일 발급 (리프레시 토큰 미도입 — MVP 단순화, D-081) |

### 포트폴리오 — 계좌·자산·매매·배당
| 기능 | 설명 |
|------|------|
| 계좌 관리 | 은행/증권사/거래소, 상세유형(일반/ISA/IRP/연금저축) |
| 거래기록 기반 자산 | 국내·해외주식/암호화폐는 수량·평단·손익을 직접 저장하지 않고, 매매 히스토리(`transactions`)에서 조회 시점에 파생 계산 (D-050, ★핵심) |
| 매수/매도 등록 | 종목 검색 → 최초 매수로 자산 생성(D-053), 보유 수량 초과 매도 차단(D-057) |
| 매매 히스토리 조회 | 자산별 거래내역(매수/매도) 최신순 목록 |
| 배당 추적 | 국내·해외주식 전용(서비스 레벨 강제), 해외주식은 USD+환율 필수 기록, 매매와 통합 히스토리로 표시 |

### 포트폴리오 대시보드 / 수익 / 세금
| 기능 | 설명 |
|------|------|
| 대시보드 | 총자산·손익 통합, 카테고리별 집계(도넛), 이번 달 매매+배당 인사이트 |
| 수익 탭 | 계좌 스코프 기간(일/주/월/년/전체)×카테고리 필터 실현손익+배당 조회 |
| 세금 탭 | 해외주식 양도소득세 추정치(기본공제 250만원·22%), 배당소득세 분리과세/종합신고 판정 — 국내주식 양도세는 조회하지 않음(D-064), 항상 "세무 전문가 검증 필요" 노출 |

### 시세·환율 연동
| 기능 | 설명 |
|------|------|
| 국내주식 시세 | data.go.kr, 전일 종가(D+1) 기준 — 실시간 아님 |
| 국내주식 종목검색 | KRX 상장종목 로컬 캐시(`domestic_stocks`) + 이름 검색, 주간 자동 갱신 + 관리자 수동 트리거 |
| 해외주식 시세 | Finnhub |
| 코인 시세 | Upbit 공개 REST 티커 (키 불요) |
| 환율(USD/KRW) | 한국수출입은행 오픈API, 매매기준율 캐시 + 영업일 11:30(KST) 자동 갱신 + 관리자 수동 트리거 |
| 원화 이중표시 | 해외주식 평가금액·손익만 원화 환산 병기(손익률 자체는 USD 기준 유지, D-087) |
| 조회 실패 시 처리 | 시세·환율 API 실패해도 화면은 정상 렌더, 조용히 degrade(D-058), 계산·추정·보간하지 않음 |

### 계정
| 기능 | 설명 |
|------|------|
| 마이페이지 | 닉네임 조회/수정 |

<br>

## 🧮 계산 로직 — 3구간 Gap-Filling

이 서비스의 핵심 아이디어입니다. 은퇴 시점부터 90세까지를 자산 접근 가능 시점 기준으로 3구간으로 나눕니다.

```
은퇴 시점 ─────────────────────────────────────────────▶ 90세
   [구간 1: 주식/ETF만]   [구간 2: +퇴직연금·IRP·연금저축]  [구간 3: +국민연금]
   은퇴 ~ 55세             55세 ~ 국민연금 수령개시           수령개시 ~ 90세
```

각 나이마다 그 해에 필요한 금액(목표 생활비, 매년 물가만큼 커짐)에서 그 시점에 열려 있는 연금 소득을 빼고, **부족분(gap)을 주식/ETF에서 인출**합니다. 이 과정을 은퇴 후보 나이를 한 살씩 올려가며(`currentAge+1` ~ 75세) 반복해서, 처음으로 끝까지(90세) 버티는 나이를 찾습니다.

### 단위 원칙 (중요)

모든 금액 계산은 **명목(nominal) 기준**으로 통일되어 있습니다.

- 목표 생활비·국민연금: 매년 물가상승률(2.5%)만큼 커짐 — 미래 시점 실제 필요 금액
- 퇴직연금·IRP·연금저축·주식: 입력한 명목 수익률 그대로 적용 (실질 수익률로 변환하지 않음)

자산 쪽에 실질 수익률을 적용하면 목표 생활비(명목)와 단위가 어긋나 부족분 계산이 무의미해지기 때문에, 개발 과정에서 이 원칙을 여러 번 검증했습니다.

<br>

## 🗳 이직 포트폴리오 열람자를 위한 주요 설계 결정

전체 이력은 워크스페이스 `STATE.md`의 Decision Log(D-001~D-114)에 남아있습니다. 그중 되돌리기 비용이 높거나 이후 결정의 전제가 된 ★핵심 항목만 요약합니다.

- **D-050 — 매매 히스토리 기반 파생값 아키텍처.** 자산의 수량·평단·손익률을 별도 컬럼에 저장하지 않고 `transactions` 테이블에서 조회 시점에 계산합니다. 실제 자산관리 앱의 표준 구조이며, 이후 M6(매매 히스토리)·M10(수익 탭)·M11(세금 탭)이 모두 이 위에서 만들어졌습니다.
- **D-079 — 개발 착수 순서를 로그인/인증(M1)으로 재배치.** `user_id` 기준으로 테이블을 설계한 뒤 나머지 기능을 얹는 순서로 바꿔, 나중에 소유자 검증을 소급 추가하는 재작업을 방지했습니다. 세션 상한도 마일스톤 수(13개)에 1:1로 맞춰 재산정했습니다.
- **D-107 — 실현손익 계산식 확정.** 수익 탭의 실현손익은 기존 평단(FIFO/이동평균이 아닌 MVP 단순화, D-050) 기준으로 `(매도단가−평단)×매도수량`을 사용하기로 확정했습니다. 정교한 재계산은 의도적으로 보류된 스코프입니다.
- **D-109 — 세금 탭 실현손익을 수익 탭과 동일 공식으로 단일화.** 세금 탭 착수 전 "수익 탭과 같은 방식으로 갈지, 별도(이동평균/FIFO)로 갈지"를 사용자에게 직접 확인해 재사용으로 확정했고, `AssetService.calculateRealizedProfitKrw()`를 단일 출처로 추출해 두 화면(`asset/profit`, `asset/tax`)이 물리적으로 같은 코드를 실행하도록 리팩터링했습니다. 두 화면이 각자 계산하다 수치가 어긋나는 문제를 근본적으로 차단합니다.
- **D-114 — 은퇴 시뮬레이터 로그인 연동 범위를 "접근 가드"로 한정(M13).** 시뮬레이터는 계산 자체가 여전히 완전 무상태이며, 로그인은 화면·API 접근을 막는 용도로만 사용합니다. 계산 결과를 user에 귀속해 저장하는 기능(이력 조회 등)은 이번 스코프에 포함하지 않았습니다 — 향후 필요성이 생기면 별도 마일스톤으로 재검토합니다.

<br>

## 🛠 기술 스택

### Backend
- **Java 21** + **Spring Boot 3.4.1**
- **Spring Security** + **OAuth2 Client** (카카오 로그인), **JJWT** (JWT 발급/검증)
- **PostgreSQL 16** + **Flyway** (스키마 버전 관리 — `ddl-auto: validate`)
- **Gradle**, **Lombok**, **Jakarta Validation**

### 외부 API
- **data.go.kr** — 국내주식 시세(D+1), KRX 상장종목 정보(종목검색 캐시용)
- **Finnhub** — 해외주식 시세
- **Upbit 공개 REST** — 코인 시세 (키 불요)
- **한국수출입은행 오픈API** — USD/KRW 매매기준율

### Frontend
- **Next.js** + **TypeScript** + **Tailwind CSS** ([retirement-planner-web](https://github.com/nowgnodeel123/retirement-planner-web))

### Infrastructure
- **Railway** (Backend), **Vercel** (Frontend)

<br>

## 🏗 시스템 아키텍처

```
┌─────────────────┐         ┌───────────────────────────────────────┐
│   Next.js       │  HTTPS  │   Spring Boot API                     │
│   (Vercel)      │───────▶│   /api/auth/**, /api/users/**          │
│                 │         │   /api/accounts, /api/assets/**       │
│                 │         │   /api/assets/{id}/dividends          │
│                 │         │   /api/accounts/{id}/profit, /tax     │
│                 │         │   /api/portfolio/summary, /insights   │
│                 │         │   /api/domestic-stocks/search         │
│                 │         │   /api/v1/simulation/calculate (인증) │
└─────────────────┘         └──────────┬─────────────────────────────┘
                                       │
                             ┌──────────▼────────────────┐
                             │   PostgreSQL (Railway)    │
                             │   Flyway로 스키마 버전 관리 │
                             └──────────┬────────────────┘
                                        │
                    ┌───────────────────┼────────────────────┐
                    ▼                   ▼                    ▼
             data.go.kr           Finnhub / Upbit      한국수출입은행
          (국내주식·종목마스터)     (해외주식·코인 시세)      (환율)
```

CORS는 로컬(`localhost:3000`)과 배포된 프론트 도메인만 허용하도록 제한되어 있습니다. `/api/auth/**`, `/oauth2/**`, `/login/**`을 제외한 모든 API는 JWT 인증이 필요합니다(은퇴 시뮬레이터 포함, M13).

<br>

## 📁 프로젝트 구조

패키지 단위(package-by-feature)로 나누고, 각 기능 패키지 내부는 계층형(entity/repository/service/controller/dto)으로 구성합니다.

```
src/main/java/com/nowgnodeel/retirement_planner/
├── user/                  # User, AuthProvider + 마이페이지(닉네임 수정)
├── auth/
│   ├── controller/        # AuthController (이메일 회원가입/로그인)
│   ├── service/           # AuthService
│   ├── dto/                # AuthDtos
│   └── oauth/              # 카카오 OAuth2 흐름 전용
├── asset/
│   ├── entity/             # Account, Asset, Transaction, Deposit + enum
│   ├── repository/ · service/ · controller/ · dto/   # AccountService, AssetService(매수/매도/보유조회/거래내역)
│   ├── dividend/           # 배당 등록/조회/삭제 (M8) — 국내·해외주식 전용, 해외는 fx 필수
│   ├── dashboard/          # 포트폴리오 전체 집계: summary/insights (M9, entity 없음)
│   ├── profit/             # 계좌 스코프 실현손익+배당 조회 (M10, entity 없음)
│   ├── tax/                # 양도소득세 추정 + 배당소득세 판정 (M11, entity 없음)
│   ├── price/              # PriceProvider 구현체 3종 + PriceService (시세 조회 디스패치, M4)
│   ├── stock/               # 국내주식 종목마스터 캐시 + 검색
│   └── fx/                  # 환율(한국수출입은행) 연동 (M5)
├── common/
│   ├── config/             # SecurityConfig, RestClientConfig
│   ├── security/            # JwtTokenProvider, JwtAuthenticationFilter
│   └── exception/           # 공통/인증 예외 + 핸들러
├── controller/
│   ├── SimulationController.java     # 은퇴 시뮬레이터 API 엔드포인트 (M13: 인증 필수)
│   └── GlobalExceptionHandler.java   # 검증 에러를 필드 단위로 상세화
├── service/
│   └── SimulationService.java        # 3구간 gap-filling 계산 엔진 (여전히 완전 무상태)
├── dto/
│   ├── SimulationRequestDto.java
│   └── SimulationResponseDto.java
└── RetirementPlannerApplication.java

src/main/resources/
├── application.yaml
└── db/migration/
    ├── V1__create_users_table.sql
    ├── V2__create_portfolio_tables.sql
    ├── V3__create_domestic_stocks_table.sql
    └── V4__create_exchange_rates_table.sql
```

> 은퇴 시뮬레이터(`controller`/`service`/`dto` 최상위 패키지)는 레거시 구조로 남아있습니다. 향후 `retirement` 기능 패키지로 재편 예정(백로그) — 지금 리팩터링하지 않기로 확정된 항목입니다.

<br>

## 🚀 로컬 실행 방법

### 사전 요구사항
- Java 21, PostgreSQL 16, Gradle
- 카카오 디벨로퍼스 앱 등록 (REST API 키, 클라이언트 시크릿, 리다이렉트 URI `{baseUrl}/login/oauth2/code/kakao`)
- data.go.kr(국내주식 시세 + KRX 상장종목정보 2종 활용신청), Finnhub, 한국수출입은행 오픈API(현재환율) 키 발급 — Upbit는 키 불요

### 1. 클론 & DB 생성

```bash
git clone https://github.com/nowgnodeel123/retirement-planner.git
cd retirement-planner
```

```sql
CREATE DATABASE retirement_planner;
```

### 2. 환경변수 설정

Flyway가 스키마를 자동으로 생성하므로(V1~V4), DB만 비어있는 상태로 준비하면 됩니다.

| 변수명 | 설명 | 예시 |
|---|---|---|
| `DB_URL` | (선택) 기본값 `jdbc:postgresql://localhost:5432/retirement_planner` | |
| `DB_USERNAME` / `DB_PASSWORD` | (선택) 로컬 PostgreSQL 계정 | |
| `KAKAO_CLIENT_ID` | **필수** — 카카오 REST API 키 | |
| `KAKAO_CLIENT_SECRET` | (선택) 카카오 클라이언트 시크릿 | |
| `JWT_SECRET` | **필수** — 32자 이상 임의 문자열 | |
| `FRONTEND_CALLBACK_URL` | (선택) 기본값 `http://localhost:3000/auth/callback` | |
| `DATA_GO_KR_API_KEY` | **필수** — 국내주식 시세/종목마스터 조회 | |
| `FINNHUB_API_KEY` | **필수** — 해외주식 시세 조회 | |
| `KOREAEXIM_API_KEY` | **필수** — 원/달러 환율 조회 | |

IntelliJ 사용 시 Run/Debug Configurations → Environment variables에 필수값을 추가하세요.

> ⚠️ Railway 배포 시 `KOREAEXIM_API_KEY`/`KAKAO_CLIENT_ID`/`KAKAO_CLIENT_SECRET`/`FRONTEND_CALLBACK_URL`(운영 도메인) 환경변수 등록이 아직 안 되어 있습니다 — 현재 로컬 검증만 완료된 상태입니다. 카카오 개발자 콘솔에도 운영 redirect URI 추가가 필요합니다.

### 3. 실행

```bash
./gradlew bootRun
```

`http://localhost:8080` 에서 실행됩니다.

<br>

## 📡 API 명세

### 인증

```
POST /api/auth/signup     이메일 회원가입 → { accessToken }
POST /api/auth/login      이메일 로그인   → { accessToken }
GET  /oauth2/authorization/kakao   카카오 로그인 시작 (브라우저 리다이렉트)
```

로그인 성공 시 JWT는 `Authorization: Bearer {accessToken}` 헤더로 이후 요청에 실어 보냅니다. 카카오 로그인은 성공 시 프론트 콜백 URL(`?accessToken=...`)로 리다이렉트됩니다. 그 외 모든 엔드포인트는 인증이 필요합니다.

### 계좌 / 자산 / 매매

```
GET    /api/accounts                       내 계좌 목록
POST   /api/accounts                       계좌 생성
DELETE /api/accounts/{id}                  계좌 삭제

GET    /api/assets?accountId={id}          계좌별 보유자산(파생값 계산 + 시세/환율 반영)
POST   /api/assets/buy                     매수 등록 (신규 종목이면 자산 생성까지 겸함, D-053)
POST   /api/assets/sell                    매도 등록 (보유수량 초과 시 400, D-057)
GET    /api/assets/{assetId}/transactions  자산별 매매 히스토리 (최신순)
```

**BuyRequest / SellRequest 공통 규칙**
- `FOREIGN_STOCK`은 `fx`(거래 시점 환율) 필수, 그 외 카테고리는 무시
- `tradeDate`는 오늘보다 미래일 수 없음(D-061)

### 배당

```
POST   /api/assets/{assetId}/dividends              배당 등록 (국내·해외주식만, 해외는 fx 필수)
GET    /api/assets/{assetId}/dividends              배당 목록 조회 (최신순)
DELETE /api/assets/{assetId}/dividends/{dividendId} 배당 삭제
```

### 포트폴리오 대시보드 / 수익 / 세금

```
GET /api/portfolio/summary                 총자산/손익 통합 + 카테고리별 집계 (시세 조회 실패 자산 제외, D-102)
GET /api/portfolio/insights/monthly        이번 달 매매+배당 요약 (저장된 fx 재사용, 실시간 재조회 없음, D-104)
GET /api/accounts/{accountId}/profit?period=&category=   기간(일/주/월/년/전체)×카테고리 실현손익+배당 조회
GET /api/accounts/{accountId}/tax?year=    양도소득세 추정(해외주식만) + 배당소득세 판정(기본값: 올해)
```

### 시세·환율

```
GET  /api/domestic-stocks/search?keyword={q}         국내주식 종목검색(로컬 캐시, 상위 20건)
POST /api/admin/domestic-stocks/refresh               국내주식 종목마스터 수동 갱신
POST /api/admin/exchange-rates/refresh                환율 수동 갱신
```

### 은퇴 시뮬레이션 (M13: 인증 필수)

```
POST /api/v1/simulation/calculate
Authorization: Bearer {accessToken}
Content-Type: application/json
```

계산 자체는 여전히 완전 무상태입니다(요청→계산→응답, DB 저장 없음). 인증은 화면·API 접근을 막는 용도로만 쓰이며, `userId`는 계산에 사용되지 않습니다(D-114).

**Request** (필수 항목만 표시, 전체 필드는 `SimulationRequestDto` 참고)

```json
{
  "currentAge": 28,
  "monthlyIncome": 300,
  "pensionYearsPaid": 5,
  "pensionType": "DC",
  "yearsOfService": 0,
  "dcCurrentBalance": 1200,
  "monthlyIrpContribution": 20,
  "monthlyPensionSavingsContribution": 30,
  "currentPensionSavingsBalance": 500,
  "targetMonthlyExpense": 300,
  "stockAssetBalance": 5000,
  "stockReturnRate": 0.07,
  "monthlyStockInvestment": 50
}
```

**Response** (핵심 필드만)

```json
{
  "summary": {
    "totalMonthlyIncome": 556,
    "targetMonthlyExpense": 300,
    "monthlyShortfall": 256,
    "estimatedRetirementAge": 51,
    "feasible": true,
    "message": "지금 페이스가 유지된다면 51세에 은퇴가 가능할 것으로 추정돼요.",
    "shareMessage": "시뮬레이션 해보니 51세 은퇴 가능성이 나왔어! 너는? → ..."
  },
  "breakdown": {
    "nationalPension": 0,
    "retirementPension": 0,
    "stockAsset": 556
  },
  "meta": {
    "yearsUntilRetirement": 23,
    "nationalPensionReceiptAge": 65,
    "lifeExpectancy": 90
  },
  "incomeTimeline": [
    { "age": 51, "nationalAfterTax": 0, "midAfterTax": 0, "liquidWithdrawalAfterTax": 529, "targetExpense": 529 },
    { "age": 55, "nationalAfterTax": 0, "midAfterTax": 462, "liquidWithdrawalAfterTax": 122, "targetExpense": 584 }
  ]
}
```

`estimatedRetirementAge`가 75세까지도 목표를 채우지 못하면 `feasible: false`가 반환되며, 프론트는 이 값으로 축하 화면 대신 안내 화면을 표시합니다.

**검증 실패 응답 예시** (400)

```json
{
  "error": "입력값을 다시 확인해주세요.",
  "fields": { "currentAge": "must be less than or equal to 74" }
}
```

**리소스 없음 / 비즈니스 규칙 위반 응답 형태** (404 / 400)

```json
{ "error": "계좌를 찾을 수 없습니다." }
```
```json
{ "error": "보유 수량(3)보다 많은 수량은 매도할 수 없습니다." }
```

<br>

## 📐 핵심 계산 공식

### 국민연금
```
기본연금액 = 0.1075 × (A값 + B값) × (1 + 0.05 × (가입연수 - 20))
조기수령: 정상수령나이(65세) 대비 1년당 -6%
연기수령: 정상수령나이 대비 1년당 +7.2%
최소 가입기간 = 10년
```

### 퇴직연금
```
DB형: 최종월급 × (과거 근속연수 + 앞으로 근속연수)
DC형: 기존잔액×(1+r)^n + Σ(매년 월급 1개월치 × (1+r)^남은연수)
```

### 연금화 (목돈 → 월 지급액)
```
월지급액 = 목돈 × 월이율 / (1 - (1+월이율)^(-지급개월수))
```

### 주식/ETF 인출 (양도소득세 gross-up)
```
필요세후금액이 정해졌을 때, 세금(22%, 연 250만원 공제) 뗀 후에도
그 금액이 남도록 매도액을 역산
```

### 실현손익 (수익 탭 / 세금 탭 공통, D-107 / D-109)
```
실현손익 = (매도단가 − 평단) × 매도수량
평단은 매도 시 이동평균/FIFO로 재계산하지 않고, 전체 매수 내역 기준을 유지(MVP 단순화)
해외주식은 매도 시점에 저장된 환율(fx)로 원화 환산
```

<br>

## ⚠️ 계산 가정 및 한계

- 모든 자산 계산은 명목 기준이며, 은퇴 후 수익률은 연 3%(명목)로 일괄 전환됩니다.
- 주식/ETF 세금은 **해외주식 기준**입니다. 국내 상장주식(소액주주)은 현재 양도세 비과세라, 국내주식 위주 포트폴리오는 세금이 과대 계산될 수 있습니다.
- 건강보험료는 국민연금 소득에만 부과하는 것으로 근사합니다. 실제로는 사적연금·금융소득·재산에도 부과되므로 실제 보험료는 더 클 수 있습니다.
- 국민연금 A값은 `application.yaml`에 고정값으로 설정되며, 매년 갱신되는 실제 값을 반영하지 않습니다.
- 본 계산기는 실제 세법·연금 산식을 단순화 반영한 추산치이며, 실제 수령액과 다를 수 있습니다.
- 국내주식 시세는 전일 종가(D+1) 기준이며 실시간이 아닙니다.
- 평균단가는 매도 시 이동평균법으로 재계산하지 않고, 전체 매수 내역 기준을 그대로 유지합니다(MVP 단순화, D-050).
- 양도소득세·배당소득세 추정 기능은 정식 세무 자문이 아니며, UI에 "세무 전문가 검증 필요" 문구가 상시 노출됩니다. 배당소득세 판정은 국내주식 배당 저장값(세후 순액)을 그대로 합산해 실제 세전 금융소득보다 과소산정될 수 있습니다(R-016, 출시 전 재검토 예정).

<br>

## 🗓 개발 로드맵

MVP 개발 페이즈 M1~M13이 모두 완료되었습니다.

- [x] **M1** — 카카오 OAuth2 + 이메일 로그인, JWT 인증
- [x] **M2** — 계좌/자산/거래/배당/입금 데이터 모델 (Flyway V1~V2)
- [x] **M3** — 자산입력 API + 프론트 연동 (매수·계좌 CRUD)
- [x] **M4** — 시세 API(국내·해외·코인) 연동 + 종목마스터 캐시
- [x] **M5** — 환율 API(한국수출입은행) 연동, 해외주식 원화 이중표시
- [x] **M6** — 매매 히스토리(매도 API + D-057 검증 + 거래내역 조회)
- [x] **M7** — 자산목록 정렬 + 정리한 자산(전량매도) 접이식 분리
- [x] **M8** — 배당 추적 (등록/조회/삭제, 국내·해외주식 전용)
- [x] **M9** — 포트폴리오 대시보드 (총자산/손익 집계, 카테고리 도넛, 월간 인사이트)
- [x] **M10** — 수익 탭 (기간×카테고리 실현손익+배당 조회)
- [x] **M11** — 세금 탭 (양도소득세 추정 + 배당소득세 판정)
- [x] **M12** — 로그인 프론트 연동 (DevTokenGate 제거, 카카오+이메일 실제 로그인)
- [x] **M13** — 은퇴시뮬레이터 로그인 연동(접근 가드) + README 최종화
- [ ] Railway 배포 (`KOREAEXIM_API_KEY`/`KAKAO_CLIENT_ID`/`KAKAO_CLIENT_SECRET`/`FRONTEND_CALLBACK_URL` 환경변수 등록 필요)

<br>

## 📄 라이선스

MIT License — see [LICENSE](LICENSE)

<br>

## 👤 개발자

**이동원 (Dongwon Lee)** · [@nowgnodeel123](https://github.com/nowgnodeel123)

---

> ⚠️ 면책 조항: 본 서비스의 계산 결과는 단순 예측치이며 실제 수령액과 다를 수 있습니다. 정확한 상담은 금융 전문가에게 문의하시기 바랍니다.
