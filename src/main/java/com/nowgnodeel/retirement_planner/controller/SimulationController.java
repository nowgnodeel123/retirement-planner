package com.nowgnodeel.retirement_planner.controller;

import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
import com.nowgnodeel.retirement_planner.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/calculate")
    public ResponseEntity<SimulationResponseDto> calculate(
            @Valid @RequestBody SimulationRequestDto request) {
        return ResponseEntity.ok(simulationService.calculate(request));
    }
}