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
@CrossOrigin(origins = "*")
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/calculate")
    public ResponseEntity<SimulationResponseDto> calculate(
            @Valid @RequestBody SimulationRequestDto request) {
        return ResponseEntity.ok(simulationService.calculate(request));
    }
}