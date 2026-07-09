package com.nowgnodeel.retirement_planner.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Deposit;

import java.util.List;

public interface DepositRepository extends JpaRepository<Deposit, Long> {

    List<Deposit> findAllByAssetIdOrderByDepositDateDesc(Long assetId);
}
