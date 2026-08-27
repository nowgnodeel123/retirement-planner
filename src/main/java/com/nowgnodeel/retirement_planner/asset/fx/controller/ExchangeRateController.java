// src/main/java/com/nowgnodeel/retirement_planner/asset/fx/controller/ExchangeRateController.java

package com.nowgnodeel.retirement_planner.asset.fx.controller;

import com.nowgnodeel.retirement_planner.asset.fx.entity.ExchangeRate;
import com.nowgnodeel.retirement_planner.asset.fx.service.ExchangeRateService;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 해외주식 매수/매도·배당 폼에서 환율을 직접 입력하는 대신 매매기준율을 기본값으로
 * 보여주기 위한 조회용 엔드포인트. ExchangeRateAdminController(갱신 트리거)와
 * 달리 일반 인증 사용자가 읽기 전용으로 호출한다.
 * - {@code date} 없음: 저장된 "가장 최근 값"(D-087 원칙상 이력은 보관하지 않음).
 * - {@code date} 있음: 그 거래일 기준 고시 매매기준율을 요청 시점에 프록시 조회(DB 저장 안 함).
 *   비영업일이면 직전 영업일로 역탐색하므로 응답 baseDate가 요청일과 다를 수 있다.
 * 프론트는 어느 경우든 이 값을 편집 가능한 기본값으로만 취급한다.
 */
@RestController
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/api/exchange-rates/{currencyCode}")
    public ResponseEntity<Response> get(
            @PathVariable String currencyCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date) {
        if (date != null) {
            ExchangeRateService.DatedRate rate = exchangeRateService.getRateOn(currencyCode, date)
                    .orElseThrow(() -> new NotFoundException("해당 날짜의 환율 정보를 찾을 수 없습니다."));
            return ResponseEntity.ok(new Response(rate.dealBasR(), rate.baseDate()));
        }
        ExchangeRate rate = exchangeRateService.getRate(currencyCode)
                .orElseThrow(() -> new NotFoundException("환율 정보를 찾을 수 없습니다."));
        return ResponseEntity.ok(new Response(rate.getDealBasR(), rate.getBaseDate()));
    }

    public record Response(BigDecimal dealBasR, LocalDate baseDate) {}
}
