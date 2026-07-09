package com.nowgnodeel.retirement_planner.asset.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividends")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;   // 국내: 세후 원화 / 해외: USD

    @Column(precision = 10, scale = 4)
    private BigDecimal fx;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Dividend(Asset asset, LocalDate payDate, BigDecimal amount, BigDecimal fx) {
        this.asset = asset;
        this.payDate = payDate;
        this.amount = amount;
        this.fx = fx;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
