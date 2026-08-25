package com.nowgnodeel.retirement_planner.user.dto;

import com.nowgnodeel.retirement_planner.user.entity.Gender;
import com.nowgnodeel.retirement_planner.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class UserDtos {

    public record UpdateNicknameRequest(
            @NotBlank @Size(max = 20) String nickname
    ) {}

    // 마이페이지 개인정보 수정 — 이름/생년월일/성별. 회원가입 폼(SignupRequest)과 동일한
    // 검증 규칙을 그대로 재사용한다.
    public record UpdateProfileRequest(
            @NotBlank @Size(max = 50) String name,
            @NotNull @Past LocalDate birthDate,
            @NotNull Gender gender
    ) {}

    // 휴대전화번호 변경 — 요청 전 /api/auth/phone/verify-code로 이 번호를 먼저 인증해둬야 한다.
    public record UpdatePhoneRequest(
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 휴대전화번호 형식이 아닙니다") String phone
    ) {}

    // 이메일(로그인 아이디) 변경 — LOCAL 계정 전용. 이메일 자체를 인증할 수단이 없어(이
    // 프로젝트엔 이메일 인증 발송 인프라가 없음), 최소한의 본인확인으로 현재 비밀번호를 요구한다.
    public record UpdateEmailRequest(
            @NotBlank @Email String email,
            @NotBlank String currentPassword
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 64) String newPassword
    ) {}

    public record MeResponse(
            Long id,
            String email,
            String nickname,
            String provider,
            Integer avatarId,
            String name,
            LocalDate birthDate,
            String gender,
            String phone
    ) {
        public static MeResponse from(User user) {
            return new MeResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getProvider().name(),
                    user.getAvatarId(),
                    user.getName(),
                    user.getBirthDate(),
                    user.getGender() != null ? user.getGender().name() : null,
                    user.getPhone()
            );
        }
    }
}
