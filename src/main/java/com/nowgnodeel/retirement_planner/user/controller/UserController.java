package com.nowgnodeel.retirement_planner.user.controller;

import com.nowgnodeel.retirement_planner.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.nowgnodeel.retirement_planner.user.dto.UserDtos.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getMe(userId));
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<MeResponse> updateNickname(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateNicknameRequest request
    ) {
        return ResponseEntity.ok(userService.updateNickname(userId, request));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<MeResponse> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PatchMapping("/me/phone")
    public ResponseEntity<MeResponse> updatePhone(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdatePhoneRequest request
    ) {
        return ResponseEntity.ok(userService.updatePhone(userId, request));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}
