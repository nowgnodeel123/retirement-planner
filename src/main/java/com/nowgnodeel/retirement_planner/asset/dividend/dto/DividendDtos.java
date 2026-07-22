// asset/dividend/dto/DividendDtos.java
package com.nowgnodeel.retirement_planner.asset.dividend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DividendDtos {

    /**
     * fx는 해외주식만 필수 — 카테고리에 따른 조건부 검증은 서비스 레이어에서 처리(BuyRequest/SellRequest와 동일 규칙).
     * payDate 미래 날짜 차단은 D-061 원칙(거래일/입금일)을 배당 지급일에도 동일 적용.
     */
    public record DividendCreateRequest(
            @NotNull @PastOrPresent LocalDate payDate,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            BigDecimal fx
    ) {}

    public record DividendResponse(
            Long dividendId,
            Long assetId,
            LocalDate payDate,
            BigDecimal amount,
            BigDecimal fx
    ) {}
}
