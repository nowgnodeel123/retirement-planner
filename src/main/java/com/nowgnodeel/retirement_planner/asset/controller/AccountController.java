// asset/controller/AccountController.java
package com.nowgnodeel.retirement_planner.asset.controller;

import com.nowgnodeel.retirement_planner.asset.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.dto.AccountDtos.*;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<Response> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRequest request
    ) {
        return ResponseEntity.ok(accountService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<Response>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(accountService.findAllByUser(userId));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long accountId
    ) {
        accountService.delete(userId, accountId);
        return ResponseEntity.noContent().build();
    }
}