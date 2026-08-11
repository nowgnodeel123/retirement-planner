package com.nowgnodeel.retirement_planner.auth.service;

import com.nowgnodeel.retirement_planner.common.exception.DuplicateEmailException;
import com.nowgnodeel.retirement_planner.common.exception.DuplicatePhoneException;
import com.nowgnodeel.retirement_planner.common.exception.InvalidCredentialsException;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
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
        given(userRepository.existsByPhone("01012345678")).willReturn(false);
        given(phoneVerificationService.isVerified("01012345678")).willReturn(false);

        assertThatThrownBy(() -> authService.signup(new SignupRequest(
                "test@nest.com", "password123", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01012345678")))
                .isInstanceOf(PhoneNotVerifiedException.class);
    }

    @Test
    @DisplayName("이미 가입에 쓰인 휴대전화번호로 회원가입 시 DuplicatePhoneException")
    void signup_duplicatePhone() {
        given(userRepository.existsByEmail("test@nest.com")).willReturn(false);
        given(userRepository.existsByPhone("01012345678")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest(
                "test@nest.com", "password123", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01012345678")))
                .isInstanceOf(DuplicatePhoneException.class);
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

    @Test
    @DisplayName("아이디 찾기 성공 시 마스킹된 이메일을 반환한다")
    void findEmail_success() {
        User user = User.createLocal("testuser@nest.com", "encoded", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01012345678");
        given(phoneVerificationService.isVerified("01012345678")).willReturn(true);
        given(userRepository.findByPhone("01012345678")).willReturn(Optional.of(user));

        FindEmailResponse response = authService.findEmail(new FindEmailRequest("01012345678"));

        assertThat(response.maskedEmail()).isEqualTo("te******@nest.com");
    }

    @Test
    @DisplayName("아이디 찾기 시 휴대전화 인증 미완료면 PhoneNotVerifiedException")
    void findEmail_phoneNotVerified() {
        given(phoneVerificationService.isVerified("01012345678")).willReturn(false);

        assertThatThrownBy(() -> authService.findEmail(new FindEmailRequest("01012345678")))
                .isInstanceOf(PhoneNotVerifiedException.class);
    }

    @Test
    @DisplayName("아이디 찾기 시 해당 휴대전화로 가입된 계정이 없으면 NotFoundException")
    void findEmail_notFound() {
        given(phoneVerificationService.isVerified("01012345678")).willReturn(true);
        given(userRepository.findByPhone("01012345678")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.findEmail(new FindEmailRequest("01012345678")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("비밀번호 재설정 성공 시 새 비밀번호로 인코딩되어 저장된다")
    void resetPassword_success() {
        User user = User.createLocal("test@nest.com", "old-encoded", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01012345678");
        given(phoneVerificationService.isVerified("01012345678")).willReturn(true);
        given(userRepository.findByEmail("test@nest.com")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newPassword123")).willReturn("new-encoded");

        authService.resetPassword(new ResetPasswordRequest("test@nest.com", "01012345678", "newPassword123"));

        assertThat(user.getPassword()).isEqualTo("new-encoded");
    }

    @Test
    @DisplayName("비밀번호 재설정 시 휴대전화번호가 계정과 다르면 NotFoundException")
    void resetPassword_phoneMismatch() {
        User user = User.createLocal("test@nest.com", "old-encoded", "동원", "이동원", BIRTH_DATE, Gender.MALE, "01099998888");
        given(phoneVerificationService.isVerified("01012345678")).willReturn(true);
        given(userRepository.findByEmail("test@nest.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordRequest("test@nest.com", "01012345678", "newPassword123")))
                .isInstanceOf(NotFoundException.class);
    }
}
