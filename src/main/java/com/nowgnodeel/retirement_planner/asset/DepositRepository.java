package com.nowgnodeel.retirement_planner.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepositRepository extends JpaRepository<Deposit, Long> {

    List<Deposit> findAllByAssetIdOrderByDepositDateDesc(Long assetId);
}
