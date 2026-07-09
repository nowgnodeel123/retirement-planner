package com.nowgnodeel.retirement_planner.auth;

import com.nowgnodeel.retirement_planner.common.exception.DuplicateEmailException;
import com.nowgnodeel.retirement_planner.common.exception.InvalidCredentialsException;
import com.nowgnodeel.retirement_planner.common.security.JwtTokenProvider;
import com.nowgnodeel.retirement_planner.user.User;
import com.nowgnodeel.retirement_planner.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nowgnodeel.retirement_planner.auth.AuthDtos.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }

        User user = User.createLocal(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname()
        );
        userRepository.save(user);

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
