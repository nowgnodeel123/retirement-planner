package com.nowgnodeel.retirement_planner.simulation.controller;

import com.nowgnodeel.retirement_planner.simulation.dto.RetirementAgeCardDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationPrefillResponseDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationResponseDto;
import com.nowgnodeel.retirement_planner.simulation.service.RetirementProfileService;
import com.nowgnodeel.retirement_planner.simulation.service.SimulationPrefillService;
import com.nowgnodeel.retirement_planner.simulation.service.SimulationService;
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
// CORS는 SecurityConfig의 전역 설정(app.cors.allowed-origins, D-225)이 "/**"에 걸어둔다.
//
// 예전엔 여기에 @CrossOrigin이 따로 있었고 그 안에 "https://YOUR-APP.vercel.app"이라는
// **플레이스홀더가 배포된 채로 남아 있었다**("배포 후 실제 도메인으로 교체" TODO와 함께).
// 허용 origin이 컨트롤러와 전역 설정 두 곳에 갈려 있으면, 도메인이 바뀔 때 한쪽만 고쳐도
// 눈에 안 띄고 "이 화면만 CORS로 막힌다"는 형태로 나타난다 — 원인을 찾기 가장 어려운 종류다.
// 환경변수 하나(APP_CORS_ALLOWED_ORIGINS)로 단일화한다.
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