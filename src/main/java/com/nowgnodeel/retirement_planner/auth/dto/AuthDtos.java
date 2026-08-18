package com.nowgnodeel.retirement_planner.auth.dto;

import com.nowgnodeel.retirement_planner.user.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class AuthDtos {

    public record SignupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(max = 20) String nickname,
            @NotBlank @Size(max = 50) String name,
            @NotNull @Past LocalDate birthDate,
            @NotNull Gender gender,
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 휴대전화번호 형식이 아닙니다") String phone
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record TokenResponse(String accessToken, String refreshToken) {}

    // RTR(D-161/M14): 회전마다 새 refreshToken을 발급하므로 응답에 항상 새 값을 실어보낸다.
    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    // 아이디(이메일) 찾기 — 휴대전화 인증(PhoneVerificationService)이 선행되어야 한다.
    public record FindEmailRequest(
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 휴대전화번호 형식이 아닙니다") String phone
    ) {}

    public record FindEmailResponse(String maskedEmail) {}

    // 비밀번호 재설정 — 이메일+휴대전화가 같은 계정에 속해야 하고, 휴대전화 인증이 선행되어야 한다.
    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 휴대전화번호 형식이 아닙니다") String phone,
            @NotBlank @Size(min = 8, max = 64) String newPassword
    ) {}
}
