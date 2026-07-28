package com.nowgnodeel.retirement_planner.auth.service;

import com.nowgnodeel.retirement_planner.common.exception.DuplicateEmailException;
import com.nowgnodeel.retirement_planner.common.exception.InvalidCredentialsException;
import com.nowgnodeel.retirement_planner.common.exception.PhoneNotVerifiedException;
import com.nowgnodeel.retirement_planner.common.security.JwtTokenProvider;
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

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
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
                request.phone()
        );
        userRepository.save(user);
        phoneVerificationService.consume(request.phone());

        return new TokenResponse(jwtTokenProvider.createAccessToken(user.getId()));
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return new TokenResponse(jwtTokenProvider.createAccessToken(user.getId()));
    }
}
