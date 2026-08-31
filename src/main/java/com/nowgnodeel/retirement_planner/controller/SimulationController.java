package com.nowgnodeel.retirement_planner.controller;

import com.nowgnodeel.retirement_planner.dto.RetirementAgeCardDto;
import com.nowgnodeel.retirement_planner.dto.SimulationPrefillResponseDto;
import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
import com.nowgnodeel.retirement_planner.service.RetirementProfileService;
import com.nowgnodeel.retirement_planner.service.SimulationPrefillService;
import com.nowgnodeel.retirement_planner.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
@Slf4j
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
    private final RetirementProfileService retirementProfileService;

    @PostMapping("/calculate")
    public ResponseEntity<SimulationResponseDto> calculate(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SimulationRequestDto request) {
        SimulationResponseDto response = simulationService.calculate(request);

        // D-219: 이 입력을 프로필로 저장해 포트폴리오 대시보드 카드가 쓰게 한다.
        // 계산 자체(SimulationService)는 여전히 userId를 모르는 무상태 경로이고,
        // 저장은 컨트롤러가 별도 서비스로 조립할 뿐이다(D-116 축소 적용).
        //
        // WHY 저장 실패를 삼키는가: 저장은 카드를 위한 부가 기능이지, 사용자가 방금 기다린
        // 계산 결과를 못 받을 이유가 아니다. 대신 조용히 넘어가면 카드가 영영 안 뜨는
        // 버그가 보이지 않으므로 WARN으로 남긴다.
        try {
            retirementProfileService.save(userId, request);
        } catch (Exception e) {
            log.warn("은퇴 프로필 저장 실패 — userId={}", userId, e);
        }

        return ResponseEntity.ok(response);
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

    /**
     * D-219: 포트폴리오 메인의 "은퇴 가능 나이" 카드. 저장된 입력 + 지금 포트폴리오
     * 자산으로 매번 다시 계산한다(결과는 저장하지 않음 — D-050).
     * 시뮬레이터를 한 번도 안 돌렸으면 hasProfile=false로 내려가고 프론트가 안내를 띄운다.
     */
    @GetMapping("/retirement-age")
    public ResponseEntity<RetirementAgeCardDto> retirementAgeCard(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(retirementProfileService.getCard(userId));
    }
}