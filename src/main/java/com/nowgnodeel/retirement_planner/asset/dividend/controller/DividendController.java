// asset/dividend/controller/DividendController.java
package com.nowgnodeel.retirement_planner.asset.dividend.controller;

import com.nowgnodeel.retirement_planner.asset.dividend.service.DividendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.dividend.dto.DividendDtos.*;

@RestController
@RequestMapping("/api/assets/{assetId}/dividends")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService dividendService;

    @PostMapping
    public ResponseEntity<DividendResponse> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId,
            @Valid @RequestBody DividendCreateRequest request
    ) {
        return ResponseEntity.ok(dividendService.create(userId, assetId, request));
    }

    @GetMapping
    public ResponseEntity<List<DividendResponse>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId
    ) {
        return ResponseEntity.ok(dividendService.findByAsset(userId, assetId));
    }

    @DeleteMapping("/{dividendId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId,
            @PathVariable Long dividendId
    ) {
        dividendService.delete(userId, assetId, dividendId);
        return ResponseEntity.noContent().build();
    }
}
