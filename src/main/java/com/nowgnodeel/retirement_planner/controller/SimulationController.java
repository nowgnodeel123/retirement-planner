package com.nowgnodeel.retirement_planner.controller;

import com.nowgnodeel.retirement_planner.dto.SimulationPrefillResponseDto;
import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
import com.nowgnodeel.retirement_planner.service.SimulationPrefillService;
import com.nowgnodeel.retirement_planner.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
// WHY: origins="*"는 로컬 개발용. 배포 시 아무 사이트나 이 API를 호출해
// 트래픽을 소모시킬 수 있으므로 실제 프론트 도메인으로 제한한다. (검토 A-2)
// TODO: Vercel 배포 후 실제 도메인으로 교체
@CrossOrigin(origins = {
        "http://localhost:3000",
        "https://YOUR-APP.vercel.app"
})
public class SimulationController {

    private final SimulationService simulationService;
    private final SimulationPrefillService simulationPrefillService;

    @PostMapping("/calculate")
    public ResponseEntity<SimulationResponseDto> calculate(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SimulationRequestDto request) {
        return ResponseEntity.ok(simulationService.calculate(request));
    }

    /**
     * D-218: 위저드 진입 시 포트폴리오에 이미 입력된 자산으로 폼 기본값을 채운다.
     * 계산(/calculate)은 여전히 요청 바디만 보는 무상태 경로이고, 사용자 데이터 조회는
     * 이 읽기 전용 엔드포인트에만 있다(D-116 축소 적용).
     */
    @GetMapping("/prefill")
    public ResponseEntity<SimulationPrefillResponseDto> prefill(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(simulationPrefillService.getPrefill(userId));
    }
}