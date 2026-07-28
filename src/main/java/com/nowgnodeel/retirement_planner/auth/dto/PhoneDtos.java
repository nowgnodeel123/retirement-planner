package com.nowgnodeel.retirement_planner.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PhoneDtos {

    public record SendCodeRequest(
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 휴대전화번호 형식이 아닙니다") String phone
    ) {}

    // devCode: 실제 SMS 벤더 연동 전까지의 개발용 노출 필드. 벤더 연동 시 제거 대상.
    public record SendCodeResponse(String devCode) {}

    public record VerifyCodeRequest(
            @NotBlank String phone,
            @NotBlank String code
    ) {}

    public record VerifyCodeResponse(boolean verified) {}
}
