# 🏦 네스트 (Nest) — Retirement Planner

> 지금 자산으로 은퇴 시점에 얼마나 준비되는지 역산해서 확인하는, 20~40대 직장인을 위한 개인 재무 관리 앱

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-schema--managed-CC0200?style=flat&logo=flyway&logoColor=white)](https://flywaydb.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

<br>

## 📌 프로젝트 소개

기존 자산관리 앱들이 "지금 내 자산이 얼마인지" 조회를 보여준다면, 네스트는 국민연금·퇴직연금·IRP·연금저축·주식/ETF·암호화폐 자산을 통합해서 **"나는 몇 살에 은퇴할 수 있을까?"** 라는 질문에 직접 답합니다.

포트폴리오(자산관리) 영역은 이 계산에 필요한 입력값을 정확하게 채우기 위한 인프라이며, 계좌·매수/매도 거래·배당·시세·환율 연동과 수익/세금 조회를 포함합니다.

<br>

## 🎯 핵심 기능

- **은퇴 시뮬레이터** — 은퇴 가능 나이 자동 계산, 국민연금/퇴직연금(DB·DC)/IRP·연금저축/주식·ETF 반영, 몬테카를로 시뮬레이션 기반 성공률, 건강보험 피부양자 자격상실 가능성 경고, 연도별 소득 타임라인
- **포트폴리오 ↔ 시뮬레이터 연결** — 위저드를 열면 실제 보유 자산(IRP·연금저축 잔액, 주식·ETF 평가금액)과 생년월일 기준 나이가 자동으로 채워지고, 시세를 못 가져온 자산은 제외 사실을 함께 안내합니다. 제출한 입력은 사용자 프로필로 저장되어, **포트폴리오 메인에서 위저드를 다시 열지 않아도 "지금 자산이면 몇 살에 은퇴 가능한지"를 상시 확인**할 수 있습니다. 계산 결과 자체는 저장하지 않고 조회할 때마다 다시 계산하므로, 자산이 늘면 다음 조회에서 나이가 앞당겨집니다
- **로그인/인증** — 카카오 소셜 로그인 + 이메일 회원가입/로그인, JWT 인증(리프레시 토큰 회전), 휴대전화 인증, 아이디 찾기/비밀번호 재설정, 회원탈퇴
- **포트폴리오** — 계좌(은행/증권사/거래소) 관리, 매수/매도 등록·정정·삭제, 국내·해외주식/암호화폐/ETF 종목 검색 자동완성, 현금·외화(원화/달러) 잔액 관리, 배당 추적, 자산 이름 수정·삭제, 계좌·자산 사용자 지정 순서, 보유자산 대시보드
- **연금저축·IRP** — 세제혜택 계좌는 개별 종목 대신 ETF만 매수 가능하도록 서버에서 강제, ETF 마스터 별도 적재
- **시세·환율 연동** — 국내주식(D+1 종가)·해외주식·암호화폐·ETF 시세, 거래일 기준 원/달러 매매기준율 조회, 조회 실패 시 안전하게 degrade
- **수익/세금** — 계좌별 실현손익 조회, 양도소득세·배당소득세 추정(전문 세무 자문 아님, 참고용)
- **보안/인프라** — 외부 API 서킷브레이커, API 레이트리밋, 개인정보 암호화 저장, 주요 자산 변경 이력 감사 로그

<br>

## 🛠 기술 스택

**Backend**: Java 21, Spring Boot 3.4.1, Spring Security + OAuth2 Client, JWT, PostgreSQL 16 + Flyway, Resilience4j, Bucket4j

**외부 연동**: 국내주식 시세(data.go.kr), 해외주식 시세(Finnhub), 암호화폐 시세(Upbit), 원/달러 환율(한국수출입은행), 카카오 로그인

**Frontend**: Next.js + TypeScript + Tailwind CSS ([retirement-planner-web](https://github.com/nowgnodeel123/retirement-planner-web))

**Infrastructure**: Railway(Backend), Vercel(Frontend)

<br>

## 🏗 시스템 아키텍처

```
┌─────────────────┐         ┌───────────────────────────────────────┐
│   Next.js       │  HTTPS  │   Spring Boot API                     │
│   (Vercel)      │───────▶│   인증 / 계좌·자산 / 시세·환율          │
│                 │         │   포트폴리오 대시보드 / 수익·세금       │
│                 │         │   은퇴 시뮬레이션(로그인 필요)          │
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

인증은 JWT 기반이며, 로그인/회원가입 관련 엔드포인트를 제외한 모든 API는 인증이 필요합니다(은퇴 시뮬레이터 포함). 외부 시세 API 호출은 서킷브레이커로 보호되고, 모든 API는 레이트리밋을 거칩니다. CORS는 허용된 프론트 도메인으로 제한됩니다.

액세스 토큰은 짧게(15분) 두고 리프레시 토큰 회전(RTR)으로 갱신합니다. 이때 **`/api/**` 의 미인증 응답은 소셜 로그인 화면으로 리다이렉트하지 않고 `401`과 JSON 본문을 반환합니다** — 리다이렉트를 보내면 브라우저 `fetch`가 그것을 따라가다 외부 도메인에서 CORS에 막혀, 프론트가 "인증 만료"와 "네트워크 장애"를 구분할 수 없게 되고 자동 갱신이 동작하지 못합니다. 브라우저가 직접 진입하는 OAuth 경로(`/oauth2/**`, `/login/**`)만 기존대로 리다이렉트를 유지합니다.

<br>

## 🚀 로컬 실행 방법

### 사전 요구사항
- Java 21, PostgreSQL 16, Gradle
- 카카오 디벨로퍼스 앱 등록 (REST API 키, 클라이언트 시크릿)
- data.go.kr, Finnhub, 한국수출입은행 오픈API 키 발급 — Upbit는 키 불요

### 1. 클론 & DB 생성

```bash
git clone https://github.com/nowgnodeel123/retirement-planner.git
cd retirement-planner
```

```sql
CREATE DATABASE retirement_planner;
```

### 2. 환경변수 설정

Flyway가 스키마를 자동으로 생성하므로 DB만 비어있는 상태로 준비하면 됩니다. 필수 환경변수는 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, `KAKAO_CLIENT_ID`, `JWT_SECRET`, `PII_ENCRYPTION_KEY`, `DATA_GO_KR_API_KEY`, `FINNHUB_API_KEY`, `KOREAEXIM_API_KEY`이며, IntelliJ 사용 시 Run/Debug Configurations → Environment variables에 등록합니다.

`DATA_GO_KR_API_KEY`는 data.go.kr에서 **서비스별로 활용신청**이 필요합니다 — KRX상장종목정보, 주식시세정보, 그리고 ETF를 위한 **증권상품시세정보** 세 가지. ETF 서비스를 신청하지 않으면 연금저축·IRP 화면에서 ETF를 찾을 수 없고, 기동 로그로 그 상태를 알려줍니다. 신청 승인 후 `POST /api/admin/etfs/refresh`를 한 번 호출하면 전체 ETF가 적재됩니다.

### 3. 실행

```bash
./gradlew bootRun
```

`http://localhost:8080` 에서 실행됩니다.

<br>

## 📄 라이선스

MIT License — see [LICENSE](LICENSE)

<br>

## 👤 개발자

**이동원 (Dongwon Lee)** · [@nowgnodeel123](https://github.com/nowgnodeel123)

---

> ⚠️ 면책 조항: 본 서비스의 계산 결과는 단순 예측치이며 실제 수령액과 다를 수 있습니다. 정확한 상담은 금융 전문가에게 문의하시기 바랍니다.
