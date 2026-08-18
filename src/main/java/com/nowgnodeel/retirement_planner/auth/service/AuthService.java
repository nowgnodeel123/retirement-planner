package com.nowgnodeel.retirement_planner.auth.service;

import com.nowgnodeel.retirement_planner.common.exception.DuplicateEmailException;
import com.nowgnodeel.retirement_planner.common.exception.DuplicatePhoneException;
import com.nowgnodeel.retirement_planner.common.exception.InvalidCredentialsException;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import com.nowgnodeel.retirement_planner.common.exception.PhoneNotVerifiedException;
import com.nowgnodeel.retirement_planner.common.security.JwtTokenProvider;
import com.nowgnodeel.retirement_planner.common.security.PiiCipher;
import com.nowgnodeel.retirement_planner.user.entity.AuthProvider;
import com.nowgnodeel.retirement_planner.user.entity.User;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nowgnodeel.retirement_planner.auth.dto.AuthDtos.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PhoneVerificationService phoneVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final PiiCipher piiCipher;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }
        String phoneHash = piiCipher.hmac(request.phone());
        if (userRepository.existsByPhoneHash(phoneHash)) {
            throw new DuplicatePhoneException();
        }
        if (!phoneVerificationService.isVerified(request.phone())) {
            throw new PhoneNotVerifiedException();
        }

        User user = User.createLocal(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.name(),
                request.birthDate(),
                request.gender(),
                request.phone(),
                phoneHash
        );
        userRepository.save(user);
        phoneVerificationService.consume(request.phone());

        return issueTokenResponse(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return issueTokenResponse(user);
    }

    // RTR 회전(D-161/M14) — 기존 refreshToken을 소진하고 access+refresh 한 쌍을 새로 발급.
    // 재사용 감지 시 RefreshTokenService가 해당 유저의 모든 활성 토큰을 revoke하고 예외를 던진다.
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(refreshToken);
        return new TokenResponse(
                jwtTokenProvider.createAccessToken(result.userId()),
                result.newRawToken()
        );
    }

    // 로그아웃 시 이 기기 하나만이 아니라 유저의 모든 활성 refresh token을 무효화한다
    // (탈취된 다른 세션이 있었다면 이 기회에 같이 끊어내는 게 안전 — 재로그인 요구는 낮은 비용).
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revokeAllForUserByRawToken(refreshToken);
    }

    private TokenResponse issueTokenResponse(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = refreshTokenService.issue(user);
        return new TokenResponse(accessToken, refreshToken);
    }

    // 아이디(이메일) 찾기 — 휴대전화 인증을 먼저 마쳐야 조회 가능
    public FindEmailResponse findEmail(FindEmailRequest request) {
        if (!phoneVerificationService.isVerified(request.phone())) {
            throw new PhoneNotVerifiedException();
        }

        User user = userRepository.findByPhoneHash(piiCipher.hmac(request.phone()))
                .orElseThrow(() -> new NotFoundException("해당 휴대전화번호로 가입된 계정을 찾을 수 없어요."));
        phoneVerificationService.consume(request.phone());

        return new FindEmailResponse(maskEmail(user.getEmail()));
    }

    // 비밀번호 재설정 — 이메일+휴대전화가 같은 계정에 속해야 하고, 휴대전화 인증이 선행되어야 한다.
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!phoneVerificationService.isVerified(request.phone())) {
            throw new PhoneNotVerifiedException();
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new NotFoundException("이메일 또는 휴대전화번호 정보가 일치하지 않아요."));

        // WHY: 전화번호 불일치·카카오 계정(비밀번호 없음)을 같은 메시지로 묶어서
        // "이 이메일이 존재하는지"조차 추론할 수 없게 한다.
        if (user.getProvider() != AuthProvider.LOCAL || !request.phone().equals(user.getPhone())) {
            throw new NotFoundException("이메일 또는 휴대전화번호 정보가 일치하지 않아요.");
        }

        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        phoneVerificationService.consume(request.phone());
    }

    // WHY: 인증 후 화면에 이메일 전체를 그대로 보여주면 "본인이 맞는지" 확인이라는
    // 원래 목적과 달리 다른 사람의 이메일 전체를 노출하는 셈이 된다. 앞 2자만
    // 보여주고 나머지는 가려서 "본인이면 알아볼 수 있는" 정도로만 공개한다.
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return email;
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.substring(0, 2);
        String masked = "*".repeat(local.length() - 2);
        return visible + masked + domain;
    }
}
