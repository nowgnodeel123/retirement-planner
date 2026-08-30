package com.nowgnodeel.retirement_planner.asset.entity;

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

    // 사용자가 끌어서 정한 순서. null이면 미지정 — 목록에서 지정된 것들 뒤로 간다.
    @Column(name = "sort_order")
    private Integer sortOrder;

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

    /**
     * 현금·외화 자산의 잔액을 덮어쓴다. 거래 기반 자산(주식/코인)과 달리 현금은
     * 수량*단가 파생이 아니라 사용자가 입력한 잔액 자체가 평가금액이다 — D-050(파생값 캐싱 금지)은
     * "거래에서 계산할 수 있는 값을 따로 저장하지 말라"는 원칙이라 여기엔 해당하지 않는다.
     * cashCost(취득원가)는 매입환율을 받지 않는 현재 스코프에선 쓰지 않는다(손익 미표시).
     */
    public void updateCashBalance(BigDecimal cash) {
        this.cash = cash;
    }

    /** 사용자가 붙이는 표시용 이름. 종목코드(symbol)는 그대로 두므로 시세 조회에는 영향이 없다. */
    public void rename(String name) {
        this.name = name;
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
