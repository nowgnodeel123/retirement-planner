// asset/dividend/entity/Dividend.java
package com.nowgnodeel.retirement_planner.asset.dividend.entity;

import com.nowgnodeel.retirement_planner.asset.entity.Asset;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * D-067: 배당 기록. amount는 국내주식=세후 원화, 해외주식=USD.
 * fx는 해외주식만 값을 가진다(지급 시점 환율, 참고용 — D-087과 동일하게 손익 재계산에는 쓰지 않음).
 * M2에서 테이블/엔티티가 먼저 만들어졌고, M8에서 asset/dividend 서브패키지로 이동(fx·stock과 동일 컨벤션).
 */
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
    private BigDecimal amount;

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
