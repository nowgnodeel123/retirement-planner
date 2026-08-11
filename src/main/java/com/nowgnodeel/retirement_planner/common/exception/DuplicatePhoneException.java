package com.nowgnodeel.retirement_planner.common.exception;

public class DuplicatePhoneException extends RuntimeException {
    public DuplicatePhoneException() {
        super("이미 가입에 사용된 휴대전화번호입니다.");
    }
}
