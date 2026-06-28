# 🏦 Retirement Planner
> 한국 직장인을 위한 은퇴 소득 시뮬레이터

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

<br>

## 📌 프로젝트 소개

국민연금, 퇴직연금(DC형), IRP를 통합하여 **은퇴 후 예상 소득과 부족분을 5분 안에 계산**해주는 시뮬레이터입니다.

기존 금융 앱들이 자산 조회에 초점을 맞추는 것과 달리, 이 서비스는 **"나는 몇 살에 은퇴할 수 있는가"** 라는 질문에 한국 세법과 연금 산식을 기반으로 직접적인 답을 제공합니다.

<br>

## 🎯 핵심 기능

| 기능 | 설명 |
|------|------|
| 국민연금 시뮬레이션 | 2026년 기준 A값(319.35만원), 소득대체율 40% 적용 |
| 퇴직연금 시뮬레이션 | DC형 기준 FV 공식으로 적립액 및 월 수령액 계산 |
| IRP 시뮬레이션 | 납입액 기반 적립액, 월 수령액, 연간 세액공제 혜택 산출 |
| 통합 부족분 분석 | 목표 생활비 대비 부족분 계산 및 조정 시나리오 제안 |

<br>

## 🛠 기술 스택

### Backend
- **Java 21** + **Spring Boot 4.1.0**
- **Spring Data JPA** + **Hibernate 7.4**
- **PostgreSQL 16**
- **Gradle**
- **Lombok**, **Jakarta Validation**

### Frontend _(개발 예정)_
- **Next.js** (React)
- **Vercel** 배포

### Infrastructure
- **Railway** (Backend 배포)
- **PostgreSQL** (Database)

<br>

## 🏗 시스템 아키텍처

```
┌─────────────────┐         ┌──────────────────────────┐
│   Next.js       │  HTTPS  │   Spring Boot API         │
│   (Vercel)      │────────▶│   POST /api/v1/simulation │
└─────────────────┘         │        /calculate          │
                            └──────────┬───────────────┘
                                       │
                            ┌──────────▼───────────────┐
                            │   PostgreSQL              │
                            │   (Railway)               │
                            └──────────────────────────┘
```

<br>

## 📁 프로젝트 구조

```
src/main/java/com/nowgnodeel/retirement_planner/
├── controller/
│   └── SimulationController.java     # API 엔드포인트
├── service/
│   └── SimulationService.java        # 핵심 계산 로직
├── dto/
│   ├── SimulationRequestDto.java     # 요청 파라미터
│   └── SimulationResponseDto.java    # 응답 구조
└── RetirementPlannerApplication.java
```

<br>

## 🚀 로컬 실행 방법

### 사전 요구사항
- Java 21
- PostgreSQL 16
- Gradle

### 1. 레포지토리 클론

```bash
git clone https://github.com/nowgnodeel/retirement-planner.git
cd retirement-planner
```

### 2. 데이터베이스 생성

```sql
CREATE DATABASE retirement_planner;
```

### 3. 환경 설정

`src/main/resources/application.yaml` 수정

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/retirement_planner
    username: {your_username}
    password: {your_password}
```

### 4. 실행

```bash
./gradlew bootRun
```

서버가 `http://localhost:8080` 에서 실행됩니다.

<br>

## 📡 API 명세

### 은퇴 소득 계산

```
POST /api/v1/simulation/calculate
Content-Type: application/json
```

**Request**

```json
{
  "currentAge": 28,
  "retirementAge": 60,
  "monthlyIncome": 300,
  "pensionYearsPaid": 6,
  "monthlyIrpContribution": 30,
  "targetMonthlyExpense": 300,
  "irpReturnRate": 0.05,
  "pensionReturnRate": 0.04
}
```

**Response**

```json
{
  "summary": {
    "totalMonthlyIncome": 271,
    "targetMonthlyExpense": 300,
    "monthlyShortfall": -29
  },
  "breakdown": {
    "nationalPension": 118,
    "retirementPension": 63,
    "irp": 90
  },
  "meta": {
    "yearsUntilRetirement": 32,
    "totalPensionYears": 38
  }
}
```

<br>

## 📐 핵심 계산 공식

### 국민연금
```
기본연금액 = 0.1 × (A + B) × (1 + 0.05 × (가입연수 - 20))

A = 319.35만원 (2026년 전체 가입자 평균소득)
B = 본인 월 소득
최소 가입기간 = 10년
```

### 퇴직연금 (DC형)
```
FV = 연간적립액 × ((1 + r)^n - 1) / r
월 수령액 = FV / (수령기간 × 12)
```

### IRP
```
FV = 연간납입액 × ((1 + r)^n - 1) / r
월 수령액 = FV / (수령기간 × 12)
연간 세액공제 = MIN(연납입액, 900만원) × 16.5%
```

> ※ 본 계산기는 2026년 국민연금 산식 기준 단순 추산입니다. 실제 수령액과 다를 수 있습니다.

<br>

## 🗓 개발 로드맵

- [x] Spring Boot 백엔드 API 구현
- [x] 국민연금 / 퇴직연금 / IRP 계산 로직
- [ ] Next.js 프론트엔드 개발
- [ ] 결과 공유 기능 (카카오톡)
- [ ] Railway 배포
- [ ] 세금 계산기 추가
- [ ] 프리미엄 구독 모델

<br>

## 🤝 기여 방법

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

<br>

## 📄 라이선스

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

<br>

## 👤 개발자

**이동원 (Dongwon Lee)**
- GitHub: [@nowgnodeel](https://github.com/nowgnodeel)

---

> ⚠️ 면책 조항: 본 서비스의 계산 결과는 단순 예측치이며 실제 수령액과 다를 수 있습니다. 정확한 상담은 금융 전문가에게 문의하시기 바랍니다.