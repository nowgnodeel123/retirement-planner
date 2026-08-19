package com.nowgnodeel.retirement_planner.user.service;

import com.nowgnodeel.retirement_planner.auth.service.PhoneVerificationService;
import com.nowgnodeel.retirement_planner.common.exception.DuplicatePhoneException;
import com.nowgnodeel.retirement_planner.common.exception.InvalidCurrentPasswordException;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import com.nowgnodeel.retirement_planner.common.exception.PhoneNotVerifiedException;
import com.nowgnodeel.retirement_planner.common.security.PiiCipher;
import com.nowgnodeel.retirement_planner.user.entity.AuthProvider;
import com.nowgnodeel.retirement_planner.user.entity.User;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nowgnodeel.retirement_planner.user.dto.UserDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneVerificationService phoneVerificationService;
    private final PiiCipher piiCipher;

    public MeResponse getMe(Long userId) {
        User user = findUser(userId);
        return MeResponse.from(user);
    }

    @Transactional
    public MeResponse updateNickname(Long userId, UpdateNicknameRequest request) {
        User user = findUser(userId);
        user.updateNickname(request.nickname());
        return MeResponse.from(user);
    }

    @Transactional
    public MeResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.updateProfile(request.name(), request.birthDate(), request.gender());
        return MeResponse.from(user);
    }

    @Transactional
    public MeResponse updatePhone(Long userId, UpdatePhoneRequest request) {
        User user = findUser(userId);
        if (!phoneVerificationService.isVerified(request.phone())) {
            throw new PhoneNotVerifiedException();
        }
        String phoneHash = piiCipher.hmac(request.phone());
        if (userRepository.existsByPhoneHashAndIdNot(phoneHash, userId)) {
            throw new DuplicatePhoneException();
        }
        user.updatePhone(request.phone(), phoneHash);
        phoneVerificationService.consume(request.phone());
        return MeResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (user.getProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
            throw new IllegalArgumentException("카카오 로그인 계정은 비밀번호를 사용하지 않아요.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCurrentPasswordException();
        }
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
    }
}
