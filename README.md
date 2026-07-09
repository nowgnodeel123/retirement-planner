# 🏦 Retirement Planner
> 한국 직장인을 위한 은퇴 시점 시뮬레이터

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

<br>

## 📌 프로젝트 소개

국민연금, 퇴직연금(DB/DC형), IRP, 연금저축, 주식/ETF 자산을 통합해서
**"나는 몇 살에 은퇴할 수 있을까?"** 라는 질문에 직접 답하는 시뮬레이터입니다.

은퇴 나이를 입력받는 게 아니라 **역산으로 계산**합니다. 20대~74세 사이 모든 나이를 하나씩 검증해서, 목표 생활비를 끝까지(90세) 감당할 수 있는 **가장 이른 나이**를 찾습니다.

<br>

## 🎯 핵심 기능

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

<br>

## 🧮 계산 로직 — 3구간 Gap-Filling

이 서비스의 핵심 아이디어입니다. 은퇴 시점부터 90세까지를 자산 접근 가능 시점 기준으로 3구간으로 나눕니다.

```
은퇴 시점 ─────────────────────────────────────────────▶ 90세
   [구간 1: 주식/ETF만]   [구간 2: +퇴직연금·IRP·연금저축]  [구간 3: +국민연금]
   은퇴 ~ 55세             55세 ~ 국민연금 수령개시           수령개시 ~ 90세
```

각 나이마다 그 해에 필요한 금액(목표 생활비, 매년 물가만큼 커짐)에서 그 시점에 열려 있는 연금 소득을 빼고, **부족분(gap)을 주식/ETF에서 인출**합니다. 이 과정을 은퇴 후보 나이를 한 살씩 올려가며(`currentAge+1` ~ 74세) 반복해서, 처음으로 끝까지(90세) 버티는 나이를 찾습니다.

### 단위 원칙 (중요)

모든 금액 계산은 **명목(nominal) 기준**으로 통일되어 있습니다.

- 목표 생활비·국민연금: 매년 물가상승률(2.5%)만큼 커짐 — 미래 시점 실제 필요 금액
- 퇴직연금·IRP·연금저축·주식: 입력한 명목 수익률 그대로 적용 (실질 수익률로 변환하지 않음)

자산 쪽에 실질 수익률을 적용하면 목표 생활비(명목)와 단위가 어긋나 부족분 계산이 무의미해지기 때문에, 개발 과정에서 이 원칙을 여러 번 검증했습니다.

<br>

## 🛠 기술 스택

### Backend
- **Java 21** + **Spring Boot 4.1.0**
- **PostgreSQL 16** _(현재 시뮬레이션 플로우는 저장 없이 무상태로 동작 — 결과 저장/공유 기능에서 사용 예정)_
- **Gradle**, **Lombok**, **Jakarta Validation**

### Frontend
- **Next.js** + **TypeScript** + **Tailwind CSS** ([retirement-planner-web](https://github.com/nowgnodeel123/retirement-planner-web))

### Infrastructure
- **Railway** (Backend), **Vercel** (Frontend)

<br>

## 🏗 시스템 아키텍처

```
┌─────────────────┐         ┌──────────────────────────┐
│   Next.js        │  HTTPS  │   Spring Boot API         │
│   (Vercel)        │────────▶│   POST /api/v1/simulation │
│   3-Step Wizard   │         │        /calculate          │
└─────────────────┘         └──────────┬───────────────┘
                                        │  (무상태 계산, DB 미사용)
                             ┌──────────▼───────────────┐
                             │   PostgreSQL               │
                             │   (Railway, 향후 결과 저장용) │
                             └──────────────────────────┘
```

CORS는 로컬(`localhost:3000`)과 배포된 프론트 도메인만 허용하도록 제한되어 있습니다.

<br>

## 📁 프로젝트 구조

```
src/main/java/com/nowgnodeel/retirement_planner/
├── controller/
│   ├── SimulationController.java     # API 엔드포인트
│   └── GlobalExceptionHandler.java   # 검증 에러를 필드 단위로 상세화
├── service/
│   └── SimulationService.java        # 3구간 gap-filling 계산 엔진
├── dto/
│   ├── SimulationRequestDto.java
│   └── SimulationResponseDto.java
└── RetirementPlannerApplication.java
```

<br>

## 🚀 로컬 실행 방법

### 사전 요구사항
- Java 21, PostgreSQL 16, Gradle

### 1. 클론 & DB 생성

```bash
git clone https://github.com/nowgnodeel123/retirement-planner.git
cd retirement-planner
```

```sql
CREATE DATABASE retirement_planner;
```

### 2. 환경 설정

`src/main/resources/application.yaml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/retirement_planner
    username: {your_username}
    password: {your_password}

app:
  share-url: http://localhost:3000
  national-pension:
    a-value: 300.0  # 국민연금 전체 가입자 평균소득(A값), 매년 갱신되는 명목값
```

### 3. 실행

```bash
./gradlew bootRun
```

`http://localhost:8080` 에서 실행됩니다.

<br>

## 📡 API 명세

### 은퇴 시뮬레이션

```
POST /api/v1/simulation/calculate
Content-Type: application/json
```

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
    "message": "축하합니다! 지금 페이스라면 51세에 은퇴할 수 있습니다. 🎉",
    "shareMessage": "51세에 은퇴할 수 있대. 너는? → ..."
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

<br>

## ⚠️ 계산 가정 및 한계

- 모든 자산 계산은 명목 기준이며, 은퇴 후 수익률은 연 3%(명목)로 일괄 전환됩니다.
- 주식/ETF 세금은 **해외주식 기준**입니다. 국내 상장주식(소액주주)은 현재 양도세 비과세라, 국내주식 위주 포트폴리오는 세금이 과대 계산될 수 있습니다.
- 건강보험료는 국민연금 소득에만 부과하는 것으로 근사합니다. 실제로는 사적연금·금융소득·재산에도 부과되므로 실제 보험료는 더 클 수 있습니다.
- 국민연금 A값은 `application.yml`에 고정값으로 설정되며, 매년 갱신되는 실제 값을 반영하지 않습니다.
- 본 계산기는 실제 세법·연금 산식을 단순화 반영한 추산치이며, 실제 수령액과 다를 수 있습니다.

<br>

## 🗓 개발 로드맵

- [x] 3구간 gap-filling 계산 엔진
- [x] 국민연금 / DB·DC 퇴직연금 / IRP / 연금저축 / 주식 통합 계산
- [x] 검증 에러 상세화 (GlobalExceptionHandler)
- [x] Next.js 프론트엔드 (3단계 위저드 + 결과 화면)
- [ ] Railway 배포
- [ ] 결과 저장 및 링크 공유 (PostgreSQL 사용 시작)
- [ ] 세액공제 최적화 팁 화면 연동

<br>

## 📄 라이선스

MIT License — see [LICENSE](LICENSE)

<br>

## 👤 개발자

**이동원 (Dongwon Lee)** · [@nowgnodeel123](https://github.com/nowgnodeel123)

---

> ⚠️ 면책 조항: 본 서비스의 계산 결과는 단순 예측치이며 실제 수령액과 다를 수 있습니다. 정확한 상담은 금융 전문가에게 문의하시기 바랍니다.