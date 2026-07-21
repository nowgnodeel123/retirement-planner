package com.nowgnodeel.retirement_planner.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Asset;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByAccountId(Long accountId);

    Optional<Asset> findByIdAndAccountId(Long id, Long accountId);

    // D-053: 종목 검색 → 최초 매수 시 기존 자산 재사용 여부 판단용
    Optional<Asset> findByAccountIdAndSymbol(Long accountId, String symbol);

    // M6: assetId만으로 접근하는 매도/거래내역 API용 — 계좌 소유자 검증까지 쿼리 한 번에 강제
    // (AccountRepository.findByIdAndUserId와 동일한 보안 원칙)
    Optional<Asset> findByIdAndAccount_User_Id(Long id, Long userId);
}
