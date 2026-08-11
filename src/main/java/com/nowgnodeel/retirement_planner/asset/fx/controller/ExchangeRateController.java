// src/main/java/com/nowgnodeel/retirement_planner/asset/fx/controller/ExchangeRateController.java

package com.nowgnodeel.retirement_planner.asset.fx.controller;

import com.nowgnodeel.retirement_planner.asset.fx.entity.ExchangeRate;
import com.nowgnodeel.retirement_planner.asset.fx.service.ExchangeRateService;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 해외주식 매수 폼에서 환율을 직접 입력하는 대신 최근 매매기준율을 기본값으로
 * 보여주기 위한 조회용 엔드포인트. ExchangeRateAdminController(갱신 트리거)와
 * 달리 일반 인증 사용자가 읽기 전용으로 호출한다.
 * D-087 원칙(매매기준율만 저장, 과거 이력 미보관)상 "가장 최근 값"만 내려주므로
 * 프론트는 이 값을 편집 가능한 기본값으로만 취급해야 한다(과거 날짜 거래엔 안 맞을 수 있음).
 */
@RestController
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/api/exchange-rates/{currencyCode}")
    public ResponseEntity<Response> get(@PathVariable String currencyCode) {
        ExchangeRate rate = exchangeRateService.getRate(currencyCode)
                .orElseThrow(() -> new NotFoundException("환율 정보를 찾을 수 없습니다."));
        return ResponseEntity.ok(new Response(rate.getDealBasR(), rate.getBaseDate()));
    }

    public record Response(BigDecimal dealBasR, LocalDate baseDate) {}
}
