package com.nowgnodeel.retirement_planner.common.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("이미 가입된 이메일입니다.");
    }
}
