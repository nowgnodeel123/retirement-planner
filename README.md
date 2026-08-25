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

개발 6개월 MVP 전 마일스톤(M1~M13)이 완료된 뒤, 2026-08-18부로 목표를 "이직 포트폴리오"에서 **실제 상용 서비스 수익화**로 전환해 금융 정밀도·보안 하드닝(M14), 몬테카를로 시뮬레이션(M16) 등을 이어서 개발하고 있습니다.

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
| ISA(개인종합자산관리계좌) | 주식/ETF 투자 전체가 ISA 안에 있다고 가정, 은퇴 시점에 누적 이익을 1회 정산(일반형 비과세 200만원·서민형·농어민형 400만원, 초과분 9.9% 분리과세) 후 일반 양도소득세 체계로 전환(신규) |
| 건강보험 피부양자 자격상실 추정 | 연금 정상 수령 시점(steady state) 기준 공적+사적연금 합산이 연 2,000만원을 넘는지 단순 추정, 확정 판정 아님(M15) |
| 몬테카를로 시뮬레이션 | 은퇴 후 주식/ETF 수익률만 확률분포(평균 3%, 표준편차 8%)로 대체해 1,000회 반복 — 90세까지 자산이 버틸 확률(P10/P50/P90 잔고 포함, M16) |
| 연도별 소득 타임라인 | 은퇴~90세까지 1년 단위 소득 구성 (`incomeTimeline`) |
| 입력값 모순 검증 | 나이 대비 과도한 납입기간 등 필드 간 정합성 체크 |

### 로그인/인증
| 기능 | 설명 |
|------|------|
| 카카오 소셜 로그인 | Spring Security OAuth2 Client 기반, 닉네임/이메일 동의항목 연동 |
| 이메일 회원가입/로그인 | BCrypt 암호화, 이메일+휴대전화번호 중복 검증(부분 유니크 인덱스, D-135) |
| 회원가입 프로필 필드 | 이메일/비밀번호/이름/생년월일/성별/휴대전화(인증) — MVP 스코프보다 넓은 개인정보 수집(D-121, ★핵심) |
| 휴대전화 인증 | 발송/확인 API. 네이버클라우드 SENS 실제 구현체(`NaverCloudSensSmsSender`)까지 작성 완료, 기본은 여전히 목업(`NoopSmsSender`, 인증번호가 응답에 노출) — 실제 벤더 계약은 서비스 런칭 시점까지 보류(D-149/D-150), 환경변수 4개만 설정하면 즉시 전환 |
| 아이디 찾기 / 비밀번호 재설정 | 휴대전화 OTP 인증 후 마스킹된 이메일 조회, 이메일+휴대전화 일치 확인 후 재설정(D-133) |
| JWT 인증 (RTR) | Access 토큰 15분 + Refresh 토큰 14일, 회전(Rotation) 방식 — 재발급마다 새 Refresh 토큰 발급, 재사용 탐지 시 해당 유저의 모든 활성 토큰 즉시 무효화(M14) |
| 아바타 | 가입 시 서버가 추상 기하 마크 10종 중 무작위 배정(`avatarId`, PII 아님) |
| 마이페이지 개인정보 수정 | 닉네임/이름/생년월일/성별/휴대전화(재인증)/비밀번호 변경 — 카카오 계정은 비밀번호·개인정보 섹션 숨김 |

### 포트폴리오 — 계좌·자산·매매·배당
| 기능 | 설명 |
|------|------|
| 계좌 관리 | 은행/증권사/거래소, 상세유형(일반/ISA/IRP/연금저축), 이름 수정(PATCH) |
| 거래기록 기반 자산 | 국내·해외주식/암호화폐는 수량·평단·손익을 직접 저장하지 않고, 매매 히스토리(`transactions`)에서 조회 시점에 파생 계산 (D-050, ★핵심) |
| 매수/매도 등록 | 종목 검색 → 최초 매수로 자산 생성(D-053), 보유 수량 초과 매도 차단(D-057), 기존 보유 자산에 재고 추가 매수 지원 |
| 카테고리-기관유형 무결성 검증 | 매수 시 계좌 기관유형에 허용된 자산 카테고리인지 서버에서 강제(D-135, ★핵심) — 프론트 필터만으로는 malformed 요청을 막지 못해 증권사 계좌에 암호화폐가 저장되던 버그를 발견해 수정 |
| 연금저축·IRP 개별 매수 차단 | 연금저축·IRP 계좌는 지정 상품만 거래 가능해 개별 국내·해외주식 매수 자체가 안 되는데, 이 검증이 전혀 없었음 — `AssetService.buy()`에서 계좌 `detailType`이 IRP/PENSION_SAVINGS면 400 거부(D-190, ★핵심). 실제 로컬 DB에서 연금저축 계좌에 매수·매도된 개별주(해외주식) 이력을 발견해 문제를 확인했으며, ETF/펀드 여부를 구분할 데이터가 없어 "개별주만 차단"은 못 하고 두 계좌 유형 모두 매수 자체를 막기로 확정 |
| 매매 히스토리 조회 | 자산별 거래내역(매수/매도) 최신순 목록 |
| 배당 추적 | 국내·해외주식 전용(서비스 레벨 강제), 해외주식은 USD+환율 필수 기록, 매매와 통합 히스토리로 표시. 배당락일(ex-date)은 선택 입력(수동, 지급일보다 늦으면 400 거부) — 자동조회 인프라는 라이선스 문제로 미노출(아래 참고) |

### 포트폴리오 대시보드 / 수익 / 세금
| 기능 | 설명 |
|------|------|
| 대시보드 | 총자산·손익 통합, **종목별 비중 집계**(도넛, D-136 — 계좌를 넘나들며 심볼로 합산), 계좌별 평가금액·손익률, 이번 달 매매+배당 인사이트 |
| 수익 탭 | 계좌 스코프 기간(일/주/월/년/전체)×카테고리 필터 실현손익+배당 조회 |
| 세금 탭 | 해외주식 양도소득세 추정치(기본공제 250만원·22%), 배당소득세 분리과세/종합신고 판정 — 국내주식 양도세는 조회하지 않음(D-064). 국내주식 배당(저장값=세후)은 15.4% 역환산해 세전 기준으로 판정(D-146, ★핵심), 항상 "세무 전문가 검증 필요" 노출 |

### 시세·환율 연동
| 기능 | 설명 |
|------|------|
| 국내주식 시세 | data.go.kr, 전일 종가(D+1) 기준 — 실시간 아님 |
| 국내주식 종목검색 | KRX 상장종목 로컬 캐시(`domestic_stocks`) + 이름 검색(이름 길이순 우선 정렬로 지주사·자회사보다 본체 상장사가 위로 오도록 근사, D-170), 주간 자동 갱신 + 관리자 수동 트리거 |
| 해외주식 시세 | Finnhub |
| 해외주식 종목검색 | Finnhub 심볼 검색(`/api/v1/search`) 프록시, 로컬 캐시 없음(D-139) — 국내주식과 달리 API 자체가 검색을 지원해 캐싱 불필요 |
| 코인 시세 | Upbit 공개 REST 티커 (키 불요) |
| 코인 종목검색 | Upbit `/v1/market/all`(KRW마켓만) 프록시, 목록 자체를 5분 메모리 캐싱 후 이름/심볼 매칭은 매 요청 수행(D-189) — 해외주식과 달리 Upbit는 검색 엔드포인트가 없어 전체 목록을 캐싱해두는 방식 |
| 환율(USD/KRW) | 한국수출입은행 오픈API, 매매기준율 캐시 + 영업일 11:30(KST) 자동 갱신 + 관리자 수동 트리거. 일반 인증 사용자용 조회 엔드포인트 별도 제공 |
| 원화 이중표시 | 해외주식 평가금액·손익만 원화 환산 병기(손익률 자체는 USD 기준 유지, D-087) |
| 조회 실패 시 처리 | 시세·환율 API 실패해도 화면은 정상 렌더, 조용히 degrade(D-058), 계산·추정·보간하지 않음 |

### 보안 / 인프라 하드닝 (M14)
| 기능 | 설명 |
|------|------|
| 시세 API 서킷브레이커 | Resilience4j — 국내·해외주식·코인 시세 조회 각각에 적용(실패율 50% 이상 시 30초간 open, 이후 반개방 3회 시도) |
| API 레이트리밋 | Bucket4j 기반 서블릿 필터 — 조회(GET) 계열 분당 60회, 쓰기(POST/PUT/PATCH/DELETE) 계열 분당 30회. 로그인 사용자는 userId, 비로그인은 IP 기준. 초과 시 429 + `Retry-After` |
| PII 암호화 | `users.name`/`phone`/`birth_date`를 AES-256-GCM으로 암호화 저장(JPA `AttributeConverter`, 호출마다 랜덤 IV). 전화번호 중복 조회는 별도 `phone_hash`(HMAC-SHA256, 결정적) 컬럼으로 수행 |
| CUD 감사 로그 | 계좌·자산(매수/매도)·배당 생성/수정/삭제를 `@AuditLogging` + AOP로 `audit_logs` 테이블에 기록(요청·응답 스냅샷, IP, 실패 시 로그 없음) |

### 계정
| 기능 | 설명 |
|------|------|
| 마이페이지 | 닉네임/이름/생년월일/성별/휴대전화/비밀번호 수정, 아바타 표시 |

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

## 🗳 주요 설계 결정 하이라이트

전체 이력은 워크스페이스 `STATE.md`의 Decision Log(D-001~D-177+)에 남아있습니다. 그중 되돌리기 비용이 높거나 이후 결정의 전제가 된 ★핵심 항목만 요약합니다.

- **D-050 — 매매 히스토리 기반 파생값 아키텍처.** 자산의 수량·평단·손익률을 별도 컬럼에 저장하지 않고 `transactions` 테이블에서 조회 시점에 계산합니다. 실제 자산관리 앱의 표준 구조이며, 이후 M6(매매 히스토리)·M10(수익 탭)·M11(세금 탭)이 모두 이 위에서 만들어졌습니다.
- **D-079 — 개발 착수 순서를 로그인/인증(M1)으로 재배치.** `user_id` 기준으로 테이블을 설계한 뒤 나머지 기능을 얹는 순서로 바꿔, 나중에 소유자 검증을 소급 추가하는 재작업을 방지했습니다. 세션 상한도 마일스톤 수(13개)에 1:1로 맞춰 재산정했습니다.
- **D-107 — 실현손익 계산식 확정.** 수익 탭의 실현손익은 기존 평단(FIFO/이동평균이 아닌 MVP 단순화, D-050) 기준으로 `(매도단가−평단)×매도수량`을 사용하기로 확정했습니다. 정교한 재계산은 의도적으로 보류된 스코프입니다.
- **D-109 — 세금 탭 실현손익을 수익 탭과 동일 공식으로 단일화.** 세금 탭 착수 전 "수익 탭과 같은 방식으로 갈지, 별도(이동평균/FIFO)로 갈지"를 사용자에게 직접 확인해 재사용으로 확정했고, `AssetService.calculateRealizedProfitKrw()`를 단일 출처로 추출해 두 화면(`asset/profit`, `asset/tax`)이 물리적으로 같은 코드를 실행하도록 리팩터링했습니다. 두 화면이 각자 계산하다 수치가 어긋나는 문제를 근본적으로 차단합니다.
- **D-114 — 은퇴 시뮬레이터 로그인 연동 범위를 "접근 가드"로 한정(M13).** 시뮬레이터는 계산 자체가 여전히 완전 무상태이며, 로그인은 화면·API 접근을 막는 용도로만 사용합니다. 계산 결과를 user에 귀속해 저장하는 기능(이력 조회 등)은 이번 스코프에 포함하지 않았습니다 — 향후 필요성이 생기면 별도 마일스톤으로 재검토합니다.
- **D-135 — 프론트 필터만으로는 데이터 무결성을 보장할 수 없다는 걸 실제 오염 데이터로 확인.** 증권사 계좌에 허용되지 않는 암호화폐 자산이 저장돼 있는 걸 로컬 DB에서 발견했습니다. 원인은 카테고리 제한(D-037)이 프론트 UI 단에만 있고 서버에는 없었던 것 — `AssetService.buy()`에 서버사이드 검증을 추가해 malformed 요청으로도 무결성이 깨지지 않도록 막았습니다.
- **D-140 — "API가 있다"와 "지금 쓸 수 있다"는 다르다는 걸 확인 후 배당 ex-date 자동조회를 보류.** data.go.kr에 배당 정보 API 자체는 실존했지만, 조회 키가 이 프로젝트가 저장해둔 종목코드가 아니라 법인등록번호/회사명뿐이었습니다. 회사명 문자열 매칭으로 우회하면 잘못된 종목의 배당 정보를 연결할 위험이 있어, 안전한 매핑 소스를 확보하기 전까지는 구현하지 않기로 했습니다 — "확정 가능한 데이터만 보여준다"는 원칙(D-058)을 새 기능에도 그대로 적용한 사례입니다.
- **D-146 — 배당소득세 판정의 세후/세전 불일치 보완.** 국내주식 배당은 저장값 자체가 세후 순액(D-067)이라, 이를 그대로 합산해 금융소득종합과세 2천만원 기준과 비교하면 실제보다 과소산정될 수 있었습니다(R-016). 국내주식 배당에 한해 15.4%(소득세 14%+지방소득세 1.4%, 법정 고정 원천징수율) 역환산 후 합산하도록 수정했습니다.
- **D-148/D-151/D-154/D-167 — 배당 ex-date 자동조회를 코드로는 완성했으나, 데이터 라이선스 문제로 결국 수동입력으로 대체.** D-140에서 보류했던 "종목코드→법인등록번호 매핑 소스 없음" 전제를 OpenDART 기업개황 API로 재조사해 뒤집고(D-148), 실제 매핑 서비스(`OpenDartCorpCodeService`)와 data.go.kr 배당정보 연동(`DividendScheduleService`)까지 구현했습니다(D-154). 그런데 이 data.go.kr 데이터셋이 **공공누리 2유형(출처표시+상업적 이용금지)** 라이선스라는 사실을 확인 — 상업화 목표(D-161) 전환 이후에도 이 문제가 해소되지 않아, 최종적으로 자동조회는 프로덕션에 노출하지 않고 사용자가 직접 입력하는 선택 필드(`ex_dividend_date`, V11)로 대체했습니다(D-167). "코드가 완성됐다"와 "출시해도 된다"는 다르다는 걸 보여준 사례입니다.
- **D-161 — 프로젝트 목표를 "이직 포트폴리오"에서 "실제 상용 서비스 수익화"로 전환(2026-08-18).** 이 전환을 계기로 M14(RTR·서킷브레이커·레이트리밋·PII 암호화·감사로그)를 진행했습니다. 전환 직후 GitHub에 로컬 흔적 없는 대량 커밋이 유입된 사건(D-160)이 있었고, 되돌린 뒤 유사 제안을 재검토하는 과정에서 세법·연금 수치를 실제로 검증해 실질 오류 1건(건보료 피부양자 판정 계수)을 발견해 반영했습니다.
- **D-162~D-166 — M14 보안 하드닝 5개 슬라이스.** RTR(Access 15분/Refresh 14일 회전, 재사용 탐지), 시세 API 서킷브레이커+국민연금 A값 정확도 수정, API 레이트리밋(Bucket4j), PII(이름·생년월일·휴대전화) AES-256-GCM 암호화, 계좌/자산/배당 CUD 감사로그. 이 세션에서 그동안 실행이 깨져 있던 `SimulationServiceTest`도 특성화 테스트로 복구해, 이 레포 사상 처음으로 전체 테스트가 그린 상태가 됐습니다.
- **D-171 — [사고] PII 암호화 마이그레이션 테스트 중 로컬 개발 DB의 실제 계정 14개가 일시적으로 로그인 불가 상태에 빠졌던 사건.** 원인은 테스트에 쓴 임시 암호화 키가 사용자의 실제 IntelliJ 실행 설정 키와 달랐기 때문 — 같은 임시 키로 즉시 복구해 데이터 손실은 없었지만, "로컬이라도 사용자의 영구 개발 DB에 데이터를 변형하는 작업(암호화 마이그레이션 등)을 할 때는 실행에 쓸 키를 미리 사용자와 맞추거나 명시적으로 확인 후 진행한다"는 교훈으로 남겼습니다.
- **D-169 — M16 몬테카를로 시뮬레이션은 은퇴 후 LIQUID(주식/ETF) 수익률만 확률분포로 대체.** 국민연금·퇴직연금·IRP·연금저축은 이 모델에서 확정 산식이라 그대로 재사용하고, 주식/ETF만 평균 3%·표준편차 8%(보수적 가정, 응답에 항상 노출)로 1,000회 반복합니다. D-157에서 "스코프 과대"로 명시적으로 제외했던 항목이지만, 상용화 목표 전환(D-161) 이후 재요청받아 진행했습니다.
- **D-190 — 연금저축·IRP 계좌의 개별 매수를 실제 데이터로 확인 후 서버에서 원천 차단.** 프론트 UX 재검토 세션 중 로컬 DB의 실제 연금저축 계좌에 해외 개별주(애플) 매수·전량매도 이력이 남아있는 걸 발견했습니다 — 연금저축·IRP는 실제로는 지정 상품(ETF·펀드 등)만 거래 가능해 개별주 매수 자체가 안 되는데, 이 프로젝트는 D-135(카테고리-기관유형 검증)까지만 서버에서 강제하고 `detailType`(연금저축/IRP/ISA/일반) 기준 제약은 전혀 없었던 게 원인이었습니다. 지금 `domestic_stocks` 캐시엔 ETF/펀드 여부를 구분할 컬럼이 없어 "개별주만 막고 ETF는 허용" 같은 정교한 제한은 데이터상 불가능하다는 걸 사용자에게 먼저 알리고, 두 계좌 유형 모두 매수 자체를 막는 쪽으로 확정했습니다(`AssetService.buy()`, ★핵심). D-135와 마찬가지로 "프론트만 막으면 malformed 요청에 뚫린다"는 원칙을 재확인한 사례이며, API를 직접 호출하는 우회 시도로 400 거부를 실제 검증했습니다.

<br>

## 🛠 기술 스택

### Backend
- **Java 21** + **Spring Boot 3.4.1**
- **Spring Security** + **OAuth2 Client** (카카오 로그인), **JJWT** (JWT 발급/검증, RTR)
- **PostgreSQL 16** + **Flyway** (스키마 버전 관리 — `ddl-auto: validate`)
- **Resilience4j** (시세 API 서킷브레이커), **Bucket4j** (API 레이트리밋, 서블릿 필터 기반)
- **Gradle**, **Lombok**, **Jakarta Validation**

### 외부 API
- **data.go.kr** — 국내주식 시세(D+1), KRX 상장종목 정보(종목검색 캐시용), 배당 ex-date(개발단계 전용, 아래 참고)
- **Finnhub** — 해외주식 시세, 심볼 검색
- **Upbit 공개 REST** — 코인 시세 + KRW마켓 목록(종목검색용, D-189) (키 불요)
- **한국수출입은행 오픈API** — USD/KRW 매매기준율
- **OpenDART(금융감독원 전자공시)** — 종목코드→법인등록번호 매핑(`corp_codes` 캐시). 배당 ex-date 매핑용 인프라, 관리자 전용(D-148/D-151)
- **네이버클라우드 SENS** — 휴대전화 인증 SMS 발송(코드 준비 완료, 계약은 배포 시점까지 보류(D-149/D-150))

> ⚠️ **배당 ex-date 자동조회는 관리자 전용 테스트 엔드포인트로만 존재하며 프론트에 연결돼 있지 않습니다.** data.go.kr 배당정보 데이터셋이 공공누리 2유형(상업적 이용금지)이라, 상업 서비스로 전환 시 한국예탁결제원과 별도 계약이 필요합니다(D-154, R-018).

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

CORS는 로컬(`localhost:3000`)과 배포된 프론트 도메인만 허용하도록 제한되어 있습니다. `/api/auth/**`, `/oauth2/**`, `/login/**`을 제외한 모든 API는 JWT 인증이 필요합니다(은퇴 시뮬레이터 포함, M13). 외부 시세 API(data.go.kr/Finnhub/Upbit) 호출은 각각 서킷브레이커로 보호되고(M14), 모든 API는 레이트리밋 필터를 거칩니다(M14).

<br>

## 📁 프로젝트 구조

패키지 단위(package-by-feature)로 나누고, 각 기능 패키지 내부는 계층형(entity/repository/service/controller/dto)으로 구성합니다.

```
src/main/java/com/nowgnodeel/retirement_planner/
├── user/                  # User(name/birthDate/gender/phone/avatarId), Gender enum, AuthProvider
│   ├── controller/         # UserController — GET /me, PATCH /me/nickname·/me/profile·/me/phone·/me/password (D-177), DELETE /me(회원탈퇴, D-190)
│   └── service/             # UserService
├── auth/
│   ├── controller/        # AuthController(회원가입/로그인/아이디찾기/비번재설정/refresh/logout), PhoneVerificationController(인증 발송/확인)
│   ├── service/           # AuthService, RefreshTokenService(RTR 회전+재사용 탐지, M14), PhoneVerificationService(SmsSender.isEnabled()로 목업/실발송 자동 전환, D-149)
│   ├── entity/              # RefreshToken(tokenHash만 저장, M14)
│   ├── sms/                # SmsSender 인터페이스, NoopSmsSender(기본, 목업), NaverCloudSensSmsSender(실구현, sms.sens.enabled로 활성화, D-149)
│   ├── dto/                # AuthDtos, PhoneDtos
│   └── oauth/              # 카카오 OAuth2 흐름 전용
├── asset/
│   ├── entity/             # Account, Asset, Transaction, Deposit + enum
│   ├── repository/ · service/ · controller/ · dto/   # AccountService(생성/삭제/이름수정), AssetService(매수/매도/보유조회/거래내역/카테고리-기관유형 검증/연금저축·IRP 매수 차단, D-190)
│   ├── dividend/           # 배당 등록/조회/삭제 (M8) — 국내·해외주식 전용, 해외는 fx 필수, ex_dividend_date 선택 입력(D-167)
│   ├── dashboard/          # 포트폴리오 전체 집계: summary/insights, 계좌별·종목별 집계 (M9, D-136, entity 없음)
│   ├── profit/             # 계좌 스코프 실현손익+배당 조회 (M10, entity 없음)
│   ├── tax/                # 양도소득세 추정 + 배당소득세 판정, 국내주식 배당 세전 역환산(D-146) (M11, entity 없음)
│   ├── price/              # PriceProvider 구현체 3종(각각 @CircuitBreaker, M14) + PriceService (시세 조회 디스패치, M4)
│   ├── stock/               # 국내주식 종목마스터 캐시+검색(이름 길이순 정렬, D-170), 해외주식 Finnhub 심볼 검색(D-139)
│   │                        # + CorpCode 엔티티/OpenDartCorpCodeService(종목코드→법인등록번호, D-151), DividendScheduleService(배당 ex-date, 관리자 전용·미노출, D-154)
│   ├── crypto/              # CryptoSearchService(Upbit market/all 5분 캐싱+이름/심볼 매칭) + CryptoController (D-189, entity 없음)
│   └── fx/                  # 환율(한국수출입은행) 연동 (M5) + 일반 사용자용 조회 엔드포인트
├── common/
│   ├── config/             # SecurityConfig, RestClientConfig(대용량 다운로드용 bulkDownloadRestClient 포함, D-151)
│   ├── security/            # JwtTokenProvider, JwtAuthenticationFilter, RateLimitFilter(Bucket4j, M14),
│   │                        # PiiCipher/EncryptedStringConverter/EncryptedLocalDateConverter(AES-256-GCM, M14), PiiMigrationRunner(1회성)
│   ├── audit/               # @AuditLogging 어노테이션 + AuditLogAspect(AOP) + AuditLog 엔티티 (계좌/자산/배당 CUD, M14)
│   └── exception/           # 공통/인증 예외 + 핸들러 (PhoneNotVerifiedException, DuplicatePhoneException, RefreshTokenReuseDetectedException 등)
├── controller/
│   ├── SimulationController.java     # 은퇴 시뮬레이터 API 엔드포인트 (M13: 인증 필수)
│   └── GlobalExceptionHandler.java   # 검증 에러를 필드 단위로 상세화
├── service/
│   └── SimulationService.java        # 3구간 gap-filling 계산 엔진 (여전히 완전 무상태) + ISA 1회 정산 + M15/M16
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
    ├── V4__create_exchange_rates_table.sql
    ├── V5__add_signup_profile_fields_to_users.sql   # name/birth_date/gender/phone (D-121)
    ├── V6__add_unique_index_on_users_phone.sql      # phone 부분 유니크 인덱스 (D-135)
    ├── V7__create_corp_codes_table.sql              # 종목코드→법인등록번호 매핑 캐시 (D-151)
    ├── V8__create_refresh_tokens_table.sql          # RTR (M14)
    ├── V9__encrypt_users_pii_columns.sql            # PII 컬럼 확장 + phone_hash + 부분 유니크 인덱스 (M14)
    ├── V10__create_audit_logs_table.sql             # CUD 감사 로그 (M14)
    ├── V11__add_ex_dividend_date_to_dividends.sql   # 배당락일 선택 입력 (D-167)
    ├── V12__add_avatar_id_to_users.sql              # 아바타 배정 (SMALLINT)
    └── V13__widen_avatar_id_to_integer.sql          # SMALLINT→INTEGER (Hibernate 검증 실패 수정)
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
| `PII_ENCRYPTION_KEY` | **필수** — base64 인코딩된 32바이트(256비트) AES 키(`openssl rand -base64 32`로 생성). users.name/phone/birth_date 암호화에 사용(D-165). **한번 정한 값을 로컬 개발 DB 전체에서 계속 같은 값으로 써야 한다** — 도중에 바꾸면 기존에 암호화된 계정의 이름/전화번호/생년월일을 다시는 복호화할 수 없다(D-171 사고 사례, 데이터 손실은 아니지만 해당 계정의 로그인·계좌조회 등이 전부 깨짐). 팀/세션이 바뀌어도 재사용할 수 있도록 값 하나를 정해 `.idea/workspace.xml` 등 로컬 실행 설정에 고정해둘 것 | |
| `FRONTEND_CALLBACK_URL` | (선택) 기본값 `http://localhost:3000/auth/callback` | |
| `DATA_GO_KR_API_KEY` | **필수** — 국내주식 시세/종목마스터 조회 | |
| `FINNHUB_API_KEY` | **필수** — 해외주식 시세 조회 | |
| `KOREAEXIM_API_KEY` | **필수** — 원/달러 환율 조회 | |
| `OPENDART_API_KEY` | (선택) 배당 ex-date 매핑 인프라용, 없으면 관련 기능 전체가 Optional.empty()로 조용히 degrade(D-151) — 배포 시점까지 발급 보류(D-152) | |
| `SENS_ENABLED` / `SENS_*` | (선택) `true`로 설정 시 실제 SMS 발송(NaverCloudSensSmsSender), 기본은 목업 — 계약은 서비스 런칭 시점까지 보류(D-150) | |

IntelliJ 사용 시 Run/Debug Configurations → Environment variables에 필수값을 추가하세요.

> ⚠️ Railway 배포 시 아래 환경변수 등록이 아직 안 되어 있습니다 — 현재 로컬 검증만 완료된 상태입니다: `KAKAO_CLIENT_ID`/`KAKAO_CLIENT_SECRET`/`FRONTEND_CALLBACK_URL`(운영 도메인)/`JWT_SECRET`/`DATA_GO_KR_API_KEY`/`FINNHUB_API_KEY`/`KOREAEXIM_API_KEY`/`PII_ENCRYPTION_KEY`/`DB_URL`·`DB_USERNAME`·`DB_PASSWORD`(Railway Postgres 플러그인 자동 주입 변수명과 매핑 확인 필요). 카카오 개발자 콘솔에도 운영 redirect URI 추가가 필요합니다. `OPENDART_API_KEY`/`SENS_*`는 배포 시점에 함께 등록 예정(D-150/D-152, 코드는 이미 준비됨). **`PII_ENCRYPTION_KEY`는 로컬 개발용과 배포용을 반드시 다른 값으로 발급할 것** — 로컬 값을 그대로 운영에 쓰지 말고, 운영은 운영대로 한 번 정하면 바꾸지 않는다(D-171).

### 3. 실행

```bash
./gradlew bootRun
```

`http://localhost:8080` 에서 실행됩니다.

<br>

## 📡 API 명세

### 인증

```
POST /api/auth/signup             이메일 회원가입(8필드, D-121) → { accessToken, refreshToken } — 휴대전화 인증 미완료 시 400
POST /api/auth/login              이메일 로그인   → { accessToken, refreshToken }
POST /api/auth/refresh            리프레시 토큰 회전 → 새 { accessToken, refreshToken } — 재사용 탐지 시 해당 유저 전체 토큰 무효화(M14)
POST /api/auth/logout             리프레시 토큰 폐기 → 204 (존재 여부와 무관하게 조용히 성공)
POST /api/auth/find-email         휴대전화 OTP 인증 후 마스킹된 이메일 조회 (D-133)
POST /api/auth/reset-password     이메일+휴대전화 일치 확인 후 비밀번호 재설정 (D-133)
POST /api/auth/phone/send-code    휴대전화 인증번호 발송 — 목업, 응답에 인증번호 그대로 노출(R-017)
POST /api/auth/phone/verify-code  휴대전화 인증번호 확인
GET  /oauth2/authorization/kakao  카카오 로그인 시작 (브라우저 리다이렉트)

GET    /api/users/me               내 정보 조회(닉네임/이메일/이름/생년월일/성별/휴대전화/아바타)
PATCH  /api/users/me/nickname      닉네임 수정
PATCH  /api/users/me/profile       이름/생년월일/성별 수정
PATCH  /api/users/me/phone         휴대전화 수정 — 사전 인증 필요(미인증 시 400), 중복 시 409
PATCH  /api/users/me/password      비밀번호 변경 — 현재 비밀번호 검증(오답 401), 카카오 계정은 거부
DELETE /api/users/me               회원탈퇴 — LOCAL은 body에 currentPassword 필요(오답/누락 400), 카카오는 확인만(D-190). 즉시 하드 삭제: refresh_tokens → accounts(DB ON DELETE CASCADE로 assets/transactions/dividends/deposits까지 전파) → users 순서로 삭제
```

Access 토큰은 `Authorization: Bearer {accessToken}` 헤더로 이후 요청에 실어 보내며 15분 후 만료됩니다(M14/RTR). 만료 전 `/api/auth/refresh`로 재발급받고, 응답의 새 refreshToken으로 항상 교체 저장해야 합니다(회전 방식이라 기존 refreshToken은 1회용). 카카오 로그인은 성공 시 프론트 콜백 URL(`?accessToken=...`)로 리다이렉트됩니다. 그 외 모든 엔드포인트는 인증이 필요합니다.

**레이트리밋(M14):** 조회(GET) 계열은 분당 60회, 쓰기(POST/PUT/PATCH/DELETE) 계열은 분당 30회로 제한됩니다. 초과 시 `429 Too Many Requests` + `Retry-After` 헤더가 반환됩니다.

### 계좌 / 자산 / 매매

```
GET    /api/accounts                       내 계좌 목록
POST   /api/accounts                       계좌 생성
PATCH  /api/accounts/{id}/name             계좌 이름 수정
DELETE /api/accounts/{id}                  계좌 삭제

GET    /api/assets?accountId={id}          계좌별 보유자산(파생값 계산 + 시세/환율 반영)
POST   /api/assets/buy                     매수 등록 (신규 종목이면 자산 생성까지 겸함, D-053) — 계좌 기관유형-카테고리 검증(D-135)
POST   /api/assets/sell                    매도 등록 (보유수량 초과 시 400, D-057)
GET    /api/assets/{assetId}/transactions  자산별 매매 히스토리 (최신순)
```

**BuyRequest / SellRequest 공통 규칙**
- `FOREIGN_STOCK`은 `fx`(거래 시점 환율) 필수, 그 외 카테고리는 무시
- `tradeDate`는 오늘보다 미래일 수 없음(D-061)
- 계좌 `detailType`이 `IRP`/`PENSION_SAVINGS`면 카테고리와 무관하게 매수 자체가 400으로 거부됨(D-190, ★핵심) — 지정 상품만 거래 가능한 계좌라 개별 국내·해외주식 매수를 원천 차단

### 배당

```
POST   /api/assets/{assetId}/dividends              배당 등록 (국내·해외주식만, 해외는 fx 필수, exDividendDate 선택·D-167)
GET    /api/assets/{assetId}/dividends              배당 목록 조회 (최신순)
DELETE /api/assets/{assetId}/dividends/{dividendId} 배당 삭제
```

`exDividendDate`(배당락일)는 지급일(`payDate`)보다 늦을 수 없으며, 위반 시 400이 반환됩니다.

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
GET  /api/foreign-stocks/search?keyword={q}           해외주식 종목검색(Finnhub 프록시, 상위 20건, D-139)
GET  /api/crypto/search?keyword={q}                    코인 종목검색(Upbit KRW마켓 목록 5분 캐싱, 상위 20건, D-189)
GET  /api/exchange-rates/{currency}                    최근 매매기준율 조회(일반 사용자용, 읽기 전용)
POST /api/admin/domestic-stocks/refresh               국내주식 종목마스터 수동 갱신
POST /api/admin/exchange-rates/refresh                환율 수동 갱신
POST /api/admin/corp-codes/refresh                     종목코드→법인등록번호 매핑 캐시 수동 갱신 (D-151)
GET  /api/admin/domestic-stocks/{symbolCode}/dividend-schedule   배당 ex-date 조회 — 관리자 전용 테스트용, 실사용자 노출 안 함(D-154, 라이선스 이슈)
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
  "monthlyStockInvestment": 50,
  "isaType": "NONE"
}
```

`isaType`은 `NONE`(기본) / `GENERAL`(일반형, 비과세 200만원) / `SEOMIN`(서민형·농어민형, 비과세 400만원) 중 하나입니다. 주식/ETF 자산 전체가 이 계좌 안에 있다고 가정하며, 은퇴 시점에 그때까지의 누적 이익을 1회 정산(비과세 한도 초과분 9.9% 분리과세)한 뒤 이후 인출은 일반 양도소득세(22%) 체계로 전환됩니다(신규).

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
  "dependentStatusWarning": {
    "atRisk": true,
    "estimatedAnnualIncome": 11724,
    "thresholdAnnualIncome": 2000,
    "message": "추정: 연금을 정상 수령하기 시작하면 ... 국민건강보험공단에 확인하세요."
  },
  "monteCarloResult": {
    "successRatePercent": 57,
    "p10EndingBalance": 0,
    "p50EndingBalance": 3200,
    "p90EndingBalance": 15400,
    "runs": 1000,
    "assumedReturnStddev": 0.08
  },
  "meta": {
    "yearsUntilRetirement": 23,
    "nationalPensionReceiptAge": 65,
    "lifeExpectancy": 90,
    "isaType": "NONE"
  },
  "incomeTimeline": [
    { "age": 51, "nationalAfterTax": 0, "retirementPensionAfterTax": 0, "privatePensionAfterTax": 0, "liquidWithdrawalAfterTax": 529, "targetExpense": 529 },
    { "age": 55, "nationalAfterTax": 0, "retirementPensionAfterTax": 462, "privatePensionAfterTax": 0, "liquidWithdrawalAfterTax": 122, "targetExpense": 584 }
  ]
}
```

`dependentStatusWarning`(M15)은 연금 정상 수령 시점 기준 단순 추정치이며 확정 판정이 아닙니다. `monteCarloResult`(M16)는 `feasible: false`이면 `null`입니다.

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

### ISA 만기 정산 (은퇴 시점 1회)
```
누적이익 = 은퇴시점잔액 − 원금
과세대상 = max(0, 누적이익 − 비과세한도)  (일반형 200만원 / 서민형·농어민형 400만원)
세금 = 과세대상 × 9.9%
정산 후에는 이 시점부터 발생하는 이익만 일반 양도소득세(22%) 대상
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
- 양도소득세·배당소득세 추정 기능은 정식 세무 자문이 아니며, UI에 "세무 전문가 검증 필요" 문구가 상시 노출됩니다. 배당소득세 판정은 국내주식 배당 저장값(세후 순액)을 15.4% 역환산한 세전 기준으로 판정합니다(D-146).
- ISA 세제 혜택은 은퇴 시점에 그동안의 누적 이익을 1회만 정산하는 방식으로 근사합니다. 실제 ISA는 3~5년 만기마다 정산·재가입이 가능하나, 이 시뮬레이터는 "매년 반복되는 비과세 한도"로 계산하지 않습니다 — 장기 보유·재가입을 반복하는 사용자는 실제 세제 혜택이 이 추정치보다 클 수 있습니다.
- 건강보험 피부양자 자격상실 추정(M15)은 공적연금+사적연금(둘 다 세전 100% 반영)만 더한 단순 추정치이며, 금융소득·근로소득·사업소득 등 다른 소득원은 반영하지 않습니다. 실제로는 이보다 더 일찍 기준을 넘을 수 있습니다.
- 몬테카를로 시뮬레이션(M16)의 표준편차(8%)는 은퇴 후 보수적 자산배분을 가정한 모델링 값이며, 실제 포트폴리오의 변동성과 다를 수 있습니다.

<br>

## 🗓 개발 로드맵

MVP 개발 페이즈 M1~M13이 모두 완료된 뒤, 상용화 목표 전환(D-161) 이후 M14~M16이 이어졌습니다.

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

M13 이후 마일스톤 외 추가 개선(백로그 소진, QA 페이즈 착수 전):
- [x] 회원가입 필드 확장(이름/생년월일/성별/휴대전화 인증) + 아이디 찾기/비밀번호 재설정
- [x] 매수/매도 카테고리-기관유형 서버 무결성 검증, 계좌 이름 수정
- [x] 대시보드 종목별 비중 재설계, 계좌별/종목별 집계
- [x] 해외주식 종목 자동완성(Finnhub 심볼 검색)
- [x] 국내주식 시세 조회 간헐적 null 원인 수정 + 실제 API 키로 라이브 검증(D-145/D-153)
- [x] 배당소득세 세후/세전 불일치 보완 — 국내주식 배당 15.4% 역환산(D-146)
- [x] 매수/매도 폼 완전 재사용 통합(D-147/D-155)
- [x] SMS 벤더(SENS) 실제 구현체 작성 — 계약은 배포 시점까지 보류(D-149/D-150)
- [x] 배당 ex-date 종목코드→법인등록번호 매핑(OpenDART) + data.go.kr 실연동 완성 — 라이선스 문제로 프로덕션 미노출, 관리자 전용으로만 유지(D-148/D-151/D-154)
- [ ] 국내주식 실시간 시세 확장 — 증권사 API+실계좌 연동 필요, Phase 2 후보(D-141)

2026-08-18부로 목표를 "이직 포트폴리오"에서 **실제 상용 서비스 수익화**로 전환(D-161) — 이후 마일스톤:
- [x] **M14 — 금융 정밀도 + 보안 하드닝.** RTR(Access 15분/Refresh 14일 회전+재사용 탐지), 시세 API 서킷브레이커(Resilience4j)+국민연금 A값 정확도 수정, API 레이트리밋(Bucket4j), PII(이름·생년월일·휴대전화) AES-256-GCM 암호화, 계좌/자산/배당 CUD 감사로그(D-162~D-166)
- [x] 배당 ex-date 자동조회 최종 종결 — 라이선스 문제로 프로덕션 미노출 확정, 사용자 수동입력(`ex_dividend_date`)으로 대체(D-167)
- [x] **M15 — 건강보험 피부양자 자격상실 가능성 추정.** 연금 정상 수령 시점 기준 공적+사적연금 합산 추정, 확정 판정 아님을 항상 명시(D-168)
- [x] **M16 — 몬테카를로 시뮬레이션(1,000회).** 은퇴 후 주식/ETF 수익률만 확률분포로 대체, 90세까지 자산이 버틸 확률(P10/P50/P90) 반환(D-169)
- [x] 국내주식 검색 랭킹 개선(이름 길이순) + 국내/해외 통합 종목 검색(D-170)
- [x] [사고 대응] PII 암호화 마이그레이션 키 불일치로 로컬 계정 로그인 불가 발생 → 즉시 복구, 재발 방지 원칙 수립(D-171)
- [x] 마이페이지 개인정보 수정 화면 신규 — 이름/생년월일/성별/휴대전화(재인증)/비밀번호 변경, 아바타 시스템(D-177)
- [x] **ISA(개인종합자산관리계좌) 세제 혜택 반영** — 은퇴 시점 1회 정산(비과세 200/400만원 + 초과분 9.9% 분리과세)

같은 흐름, 사용자 UI 재검토 요청으로 진행한 애드혹 개선:
- [x] 코인 종목검색 신규(`asset/crypto`, `GET /api/crypto/search`) — Upbit `/v1/market/all` KRW마켓 목록 5분 캐싱, 국내·해외주식과 동일하게 이름 검색으로 통일(D-189)
- [x] **회원탈퇴 API 신규(`DELETE /api/users/me`, ★핵심)** — LOCAL은 현재 비밀번호 재확인, 카카오는 확인만. refresh_tokens→accounts(DB cascade)→users 순서로 즉시 하드 삭제
- [x] **연금저축·IRP 계좌 개별 매수 차단(`AssetService.buy()`, ★핵심, D-190)** — 실제 로컬 DB에서 연금저축 계좌에 매수된 개별 해외주식 이력을 발견해 문제를 확인, 계좌 `detailType` 검증 추가. ETF/펀드 구분 데이터가 없어 두 계좌 유형 모두 매수 자체를 차단하는 쪽으로 확정
- [ ] Railway 배포 (`KAKAO_CLIENT_ID`/`KAKAO_CLIENT_SECRET`/`FRONTEND_CALLBACK_URL`/`JWT_SECRET`/`DATA_GO_KR_API_KEY`/`FINNHUB_API_KEY`/`KOREAEXIM_API_KEY`/`PII_ENCRYPTION_KEY`/`DB_*` 환경변수 등록 필요)

<br>

## 📄 라이선스

MIT License — see [LICENSE](LICENSE)

<br>

## 👤 개발자

**이동원 (Dongwon Lee)** · [@nowgnodeel123](https://github.com/nowgnodeel123)

---

> ⚠️ 면책 조항: 본 서비스의 계산 결과는 단순 예측치이며 실제 수령액과 다를 수 있습니다. 정확한 상담은 금융 전문가에게 문의하시기 바랍니다.
