package com.nowgnodeel.retirement_planner.auth.controller;

import com.nowgnodeel.retirement_planner.auth.service.PhoneVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.nowgnodeel.retirement_planner.auth.dto.PhoneDtos.*;

@RestController
@RequestMapping("/api/auth/phone")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    @PostMapping("/send-code")
    public ResponseEntity<SendCodeResponse> sendCode(@Valid @RequestBody SendCodeRequest request) {
        String code = phoneVerificationService.sendCode(request.phone());
        return ResponseEntity.ok(new SendCodeResponse(code));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<VerifyCodeResponse> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        boolean verified = phoneVerificationService.verifyCode(request.phone(), request.code());
        return ResponseEntity.ok(new VerifyCodeResponse(verified));
    }
}
