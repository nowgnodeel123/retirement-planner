package com.nowgnodeel.retirement_planner.auth.sms;

/**
 * 휴대전화 인증 SMS 발송 추상화(R-017). 실제 벤더 계약 전까지는 {@link NoopSmsSender}만 존재하고,
 * {@link com.nowgnodeel.retirement_planner.auth.service.PhoneVerificationService}는 인증번호를
 * API 응답에 그대로 노출하는 목업으로 동작한다(D-122).
 *
 * 실제 벤더 연동 시 이 인터페이스의 구현체를 하나 추가하고 {@code isEnabled()}가 true를 반환하도록
 * 하면, PhoneVerificationService가 자동으로 응답에서 인증번호 노출을 중단한다(아래 참고).
 *
 * 벤더 후보 조사(S-044) 결과 — 둘 다 개인 개발자 명의로 가입 가능, 국내 표준:
 *  - 네이버클라우드 SENS: SMS 단문 약 8~9원/건, NCP 계정 생성 후 콘솔에서 발신번호 사전등록
 *    (통신사 심의, 신원확인 서류 필요 — 벤더 무관 공통 규제). 예상 환경변수:
 *    SENS_ACCESS_KEY / SENS_SECRET_KEY / SENS_SERVICE_ID / SENS_SENDER_PHONE
 *  - 알리고(Aligo): 가입 절차가 더 간단한 편, 요금대 유사. 예상 환경변수: ALIGO_API_KEY / ALIGO_SENDER_PHONE
 *  - Twilio는 국내 발신에 상대적으로 비싸고 발신번호 확보 절차가 더 번거로워 후순위 판단.
 * 최종 벤더 선정은 사용자 확인 필요(계약·비용 발생 항목이라 코드만으로 결정할 수 없음).
 */
public interface SmsSender {

    /** 실제 벤더가 연동되어 있으면 true. NoopSmsSender는 항상 false. */
    boolean isEnabled();

    /** phone: 하이픈 없는 숫자만. message: 발송할 문자 본문. */
    void send(String phone, String message);
}
