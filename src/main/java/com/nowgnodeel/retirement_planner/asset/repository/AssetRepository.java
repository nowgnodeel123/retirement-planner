package com.nowgnodeel.retirement_planner.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Asset;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByAccountId(Long accountId);

    Optional<Asset> findByIdAndAccountId(Long id, Long accountId);

    // D-053: 종목 검색 → 최초 매수 시 기존 자산 재사용 여부 판단용 (M3 신규)
    Optional<Asset> findByAccountIdAndSymbol(Long accountId, String symbol);
}