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

    public record TokenResponse(String accessToken) {}
}
