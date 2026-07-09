package com.nowgnodeel.retirement_planner.asset;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * D-050 핵심: 거래 기반 자산(국내/해외주식, 암호화폐)의 수량·평균단가·손익률은
 * 여기에 저장하지 않는다. transactions의 파생값으로 조회 시점에 계산한다.
 */
@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetCategory category;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String symbol;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(precision = 18, scale = 2)
    private BigDecimal cash;

    @Column(name = "cash_cost", precision = 18, scale = 2)
    private BigDecimal cashCost;

    @Column(nullable = false, length = 10)
    private String source;

    @Column(name = "external_account_id", length = 100)
    private String externalAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Asset(Account account, AssetCategory category, String name, String symbol,
                  String currency, BigDecimal cash, BigDecimal cashCost) {
        this.account = account;
        this.category = category;
        this.name = name;
        this.symbol = symbol;
        this.currency = currency != null ? currency : "KRW";
        this.cash = cash;
        this.cashCost = cashCost;
        this.source = "MANUAL";
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void updateFundValue(BigDecimal cash, BigDecimal cashCost) {
        this.cash = cash;
        this.cashCost = cashCost;
    }
}
