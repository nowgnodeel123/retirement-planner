package com.nowgnodeel.retirement_planner.auth.service;

import com.nowgnodeel.retirement_planner.common.exception.DuplicateEmailException;
import com.nowgnodeel.retirement_planner.common.exception.InvalidCredentialsException;
import com.nowgnodeel.retirement_planner.common.exception.PhoneNotVerifiedException;
import com.nowgnodeel.retirement_planner.common.security.JwtTokenProvider;
import com.nowgnodeel.retirement_planner.user.entity.Gender;
import com.nowgnodeel.retirement_planner.user.entity.User;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static com.nowgnodeel.retirement_planner.auth.dto.AuthDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock PhoneVerificationService phoneVerificationService;
    @InjectMocks AuthService authService;

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 1, 1);

    @Test
    @DisplayName("회원가입 성공 시 액세스 토큰을 반환한다")
    void signup_success() {
        given(userRepository.existsByEmail("test@nest.com")).willReturn(false);
        given(phoneVerificationService.isVerified("01012345678")).willReturn(true);
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtTokenProvider.createAccessToken(any())).willReturn("access-token");

        TokenResponse response = authService.signup(new SignupRequest(
                "test@nest.com", "password123", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01012345678"));

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("이미 가입된 이메일로 회원가입 시 DuplicateEmailException")
    void signup_duplicateEmail() {
        given(userRepository.existsByEmail("test@nest.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest(
                "test@nest.com", "password123", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01012345678")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("휴대전화 인증 미완료 시 PhoneNotVerifiedException")
    void signup_phoneNotVerified() {
        given(userRepository.existsByEmail("test@nest.com")).willReturn(false);
        given(phoneVerificationService.isVerified("01012345678")).willReturn(false);

        assertThatThrownBy(() -> authService.signup(new SignupRequest(
                "test@nest.com", "password123", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01012345678")))
                .isInstanceOf(PhoneNotVerifiedException.class);
    }

    @Test
    @DisplayName("로그인 성공 시 액세스 토큰을 반환한다")
    void login_success() {
        User user = User.createLocal("test@nest.com", "encoded", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01012345678");
        given(userRepository.findByEmail("test@nest.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encoded")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(any())).willReturn("access-token");

        TokenResponse response = authService.login(new LoginRequest("test@nest.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("비밀번호 불일치 시 InvalidCredentialsException")
    void login_wrongPassword() {
        User user = User.createLocal("test@nest.com", "encoded", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01012345678");
        given(userRepository.findByEmail("test@nest.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@nest.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
