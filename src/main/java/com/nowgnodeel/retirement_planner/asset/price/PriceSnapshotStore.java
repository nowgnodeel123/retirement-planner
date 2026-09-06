package com.nowgnodeel.retirement_planner.asset.price;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * 시세 스냅샷 읽기/쓰기를 PriceService에서 분리한 이유는 트랜잭션 때문이다.
 *
 * 시세 조회는 AssetService의 조회 경로에서 일어나는데 그 클래스는 @Transactional(readOnly = true)다.
 * 그 안에서 그대로 저장하면 읽기 전용 트랜잭션에 참여해 쓰기가 실패한다. REQUIRES_NEW로
 * 바깥 트랜잭션을 잠시 멈추고 별도의 쓰기 트랜잭션에서 저장한다.
 *
 * 스냅샷 저장 실패는 시세 조회 자체를 깨뜨리면 안 된다 — 어디까지나 다음 장애를 대비한
 * 보조 기록이라, 실패하면 로그만 남기고 넘어간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceSnapshotStore {

    private final PriceSnapshotRepository repository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<PriceSnapshot> find(String priceKey) {
        try {
            return repository.findById(priceKey);
        } catch (Exception e) {
            log.warn("시세 스냅샷 조회 실패 key={}", priceKey, e);
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String priceKey, BigDecimal price, Instant fetchedAt) {
        try {
            PriceSnapshot snapshot = repository.findById(priceKey)
                    .map(existing -> {
                        existing.update(price, fetchedAt);
                        return existing;
                    })
                    .orElseGet(() -> new PriceSnapshot(priceKey, price, fetchedAt));
            repository.save(snapshot);
        } catch (Exception e) {
            log.warn("시세 스냅샷 저장 실패 key={}", priceKey, e);
        }
    }
}
