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
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal unitPrice;

    @Column(precision = 10, scale = 4)
    private BigDecimal fx;   // 해외주식 전용, 국내/코인은 null

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Transaction(Asset asset, TransactionType type, LocalDate tradeDate,
                        BigDecimal quantity, BigDecimal unitPrice, BigDecimal fx) {
        this.asset = asset;
        this.type = type;
        this.tradeDate = tradeDate;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.fx = fx;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        validateTradeDate();
    }

    @PreUpdate
    void preUpdate() {
        validateTradeDate();
    }

    // 미래 날짜 차단(D-061)은 서비스 레이어에서 검증 — 엔티티는 마지막 방어선으로만 둠
    private void validateTradeDate() {
        if (this.tradeDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("거래일은 오늘보다 미래일 수 없습니다.");
        }
    }

    /**
     * 잘못 입력한 거래의 정정. 매수/매도 구분(type)은 바꾸지 않는다 —
     * 방향이 뒤바뀌면 실현손익·보유수량의 의미가 통째로 달라져서,
     * 그 경우는 삭제 후 다시 등록하는 쪽이 안전하다.
     * 보유수량이 음수가 되지 않는지(D-057)는 서비스 레이어에서 반영 전에 검증한다.
     */
    public void update(LocalDate tradeDate, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fx) {
        this.tradeDate = tradeDate;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.fx = fx;
    }
}
