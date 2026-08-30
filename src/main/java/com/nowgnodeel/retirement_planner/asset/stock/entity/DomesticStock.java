package com.nowgnodeel.retirement_planner.asset.stock.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M4: data.go.kr에 종목명 자동완성 엔드포인트가 없어서(확인 완료),
 * KRX상장종목정보로 받은 전체 종목을 캐싱해두고 로컬 검색으로 대체한다.
 * 주 1회 스케줄러(DomesticStockMasterService)로 갱신.
 */
@Entity
@Table(name = "domestic_stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomesticStock {

    @Id
    @Column(name = "symbol_code", length = 10)
    private String symbolCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String market;

    // 연금저축·IRP에서 매수 가능한지 판정하는 근거(D-198). ETF 마스터는 주식과 다른
    // API에서 받아오므로 종목 자체에 표시해 둔다.
    @Column(name = "is_etf", nullable = false)
    private boolean etf;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DomesticStock(String symbolCode, String name, String market, boolean etf) {
        this.symbolCode = symbolCode;
        this.name = name;
        this.market = market;
        this.etf = etf;
        this.updatedAt = LocalDateTime.now();
    }

    public void refresh(String name, String market) {
        this.name = name;
        this.market = market;
        this.updatedAt = LocalDateTime.now();
    }

    public void refreshAsEtf(String name, String market) {
        this.name = name;
        this.market = market;
        this.etf = true;
        this.updatedAt = LocalDateTime.now();
    }
}