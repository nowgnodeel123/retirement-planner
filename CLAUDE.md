# retirement-planner (백엔드)

이 파일은 이 레포 전용 규칙이다. 워크스페이스 공통 규칙은 상위 폴더 `../CLAUDE.md`를 함께 참조할 것.

## 스택

Spring Boot 3.4.1 / Java 21 / PostgreSQL / Flyway. 패키지 루트 `com.nowgnodeel.retirement_planner`. 실행: `./gradlew bootRun`.

## 패키지 구조

package-by-feature + 기능 패키지 내부 계층형(entity/repository/service/controller/dto). 은퇴시뮬레이터(controller/service/dto가 최상위에 있는 레거시 구조)는 지금 리팩터링하지 않는다 — 백로그.

## 네이밍

- 엔티티=단수, 테이블=복수 snake_case
- DTO: `XxxRequest` / `XxxResponse`, 중간객체는 `XxxResult`
- enum: 대문자 스네이크 + `EnumType.STRING` 고정. **ORDINAL 금지**

## 계층 책임

- Controller: 검증 + DTO 변환만
- Service: `@Transactional`(조회는 `readOnly = true`), 엔티티를 컨트롤러 밖으로 반환 금지
- 목록 조회의 연관관계는 fetch join / `@EntityGraph`로 N+1 방지 — 최적화가 아니라 결함으로 취급

## 소유자 검증 (예외 없음)

계좌·자산·거래·배당·입금을 다루는 모든 서비스 메서드는 요청자 소유 여부를 검증한다. 서비스 계층에 둘 것. 실패 시 403(존재 자체를 숨겨야 하면 404).

## Flyway

- 적용된 마이그레이션 파일 절대 수정 금지, 새 `V{n}__` 파일로만 추가
- 작성 전 실제 레포의 최신 버전 번호를 확인
- 엔티티 필드 타입과 DDL 타입이 일치하는지 대조 (과거 CHAR(3) vs VARCHAR(3) 불일치 버그 있었음)

## API 응답 / 에러 처리

- 상태코드: 200 조회/수정 · 201 생성 · 204 삭제 · 400 검증실패 · 401 인증실패 · 403 권한없음 · 404 없음 · 409 상태충돌
- 에러 응답은 `@RestControllerAdvice` 하나로 통일: `{ "code": "...", "message": "한국어 문장" }`. 스택트레이스·내부 클래스명·SQL 노출 금지
- 성공 응답은 래퍼 없이 DTO 그대로. 신규 목록 응답만 객체로 감싸기(기존 배열 응답은 소급 변경 안 함)
- 시세·환율 실패는 예외 흡수 후 `Optional.empty()`. 가격 필드는 nullable. 마지막 조회 성공 시각을 함께 내려 UI 기준일 표기용으로 사용

## 로깅

외부 API 실패=warn, 로직 오류=error, 흐름=debug. `System.out.println` 금지. 로그에 키·개인정보 금지.

## 환경변수

커밋 금지. 신규 도입 시 워크스페이스 루트 STATE.md의 "다음 행동"에 배포(Railway) 등록 필요 사항을 명시하도록 알려줄 것.

## 테스트 방침

- 은퇴 시뮬레이터, 세금 추정 로직: 단위 테스트 필수 (경계 케이스 포함)
- CRUD/조회 API: 실동작 검증(로컬 기동 → 실제 호출 → 수치 대조)으로 갈음, 모킹 단위 테스트는 만들지 않음
- 외부 API 연동: 실제 호출 1회 검증 + 실패 경로(키오류·휴장일·null) 의도적 유발해 degrade 확인

## 외부 API 연동 확정 사항

- 국내주식: data.go.kr (D+1 종가, 자동완성 없음 → KRX 전체 목록 로컬 캐싱+매칭)
- 해외주식: Finnhub (무료 티어, 심볼 검색 포함)
- 코인: Upbit 공개 REST 티커 (키 불요)
- 환율: 한국수출입은행 오픈API (매매기준율만 저장, ttb/tts 저장 안 함)
