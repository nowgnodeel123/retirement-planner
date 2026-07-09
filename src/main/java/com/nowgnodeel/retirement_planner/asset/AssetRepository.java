package com.nowgnodeel.retirement_planner.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByAccountId(Long accountId);

    Optional<Asset> findByIdAndAccountId(Long id, Long accountId);
}
