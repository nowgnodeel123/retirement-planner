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

- **은퇴 시뮬레이터** — 은퇴 가능 나이 자동 계산, 국민연금/퇴직연금(DB·DC)/IRP·연금저축/주식·ETF/ISA 반영, 몬테카를로 시뮬레이션 기반 성공률, 건강보험 피부양자 자격상실 가능성 경고, 연도별 소득 타임라인
- **로그인/인증** — 카카오 소셜 로그인 + 이메일 회원가입/로그인, JWT 인증(리프레시 토큰 회전), 휴대전화 인증, 아이디 찾기/비밀번호 재설정, 회원탈퇴
- **포트폴리오** — 계좌(은행/증권사/거래소) 관리, 매수/매도 등록, 국내·해외주식/암호화폐 종목 검색 자동완성, 배당 추적, 보유자산 대시보드
- **시세·환율 연동** — 국내·해외주식/암호화폐 시세, 원/달러 환율 자동 갱신, 조회 실패 시 안전하게 degrade
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
