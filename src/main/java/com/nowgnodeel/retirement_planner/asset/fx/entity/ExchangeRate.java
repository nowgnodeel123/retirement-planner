// src/main/java/com/nowgnodeel/retirement_planner/asset/fx/entity/ExchangeRate.java

package com.nowgnodeel.retirement_planner.asset.fx.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * M5: 한국수출입은행 환율 API(D-063) 캐시. MVP는 USD만 사용(해외주식 원화환산 전용).
 * 매매기준율(deal_bas_r)만 저장 — 송금 시 우대환율(ttb/tts)은 이 앱 스코프 밖.
 * 매 영업일 11:30(KST) 스케줄러 + 수동 트리거 병행(asset/stock 패턴과 동일).
 */
@Entity
@Table(name = "exchange_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate {

    @Id
    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "deal_bas_r", nullable = false, precision = 12, scale = 4)
    private BigDecimal dealBasR;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ExchangeRate(String currencyCode, BigDecimal dealBasR, LocalDate baseDate) {
        this.currencyCode = currencyCode;
        this.dealBasR = dealBasR;
        this.baseDate = baseDate;
        this.updatedAt = LocalDateTime.now();
    }

    public void refresh(BigDecimal dealBasR, LocalDate baseDate) {
        this.dealBasR = dealBasR;
        this.baseDate = baseDate;
        this.updatedAt = LocalDateTime.now();
    }
}