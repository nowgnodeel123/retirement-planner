package com.nowgnodeel.retirement_planner.common.exception;

public class PhoneNotVerifiedException extends RuntimeException {
    public PhoneNotVerifiedException() {
        super("휴대전화 인증을 완료해주세요.");
    }
}
