package com.nowgnodeel.retirement_planner.asset.price;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 마지막으로 성공한 시세 조회값. 외부 시세 API가 죽어 있는 동안 화면이 빈 값이 되지
 * 않도록 여기서 되돌려준다(stale-on-error).
 *
 * 조회에 성공했을 때만 갱신한다 — 실패는 기록하지 않는다. 실패를 적으면 "마지막으로
 * 성공한 값"이라는 의미가 깨진다.
 */
@Entity
@Table(name = "price_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceSnapshot {

    @Id
    @Column(name = "price_key", nullable = false, length = 80)
    private String priceKey;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public PriceSnapshot(String priceKey, BigDecimal price, Instant fetchedAt) {
        this.priceKey = priceKey;
        this.price = price;
        this.fetchedAt = fetchedAt;
    }

    public void update(BigDecimal price, Instant fetchedAt) {
        this.price = price;
        this.fetchedAt = fetchedAt;
    }
}
