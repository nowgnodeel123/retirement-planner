package com.nowgnodeel.retirement_planner.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Dividend;

import java.util.List;

public interface DividendRepository extends JpaRepository<Dividend, Long> {

    List<Dividend> findAllByAssetIdOrderByPayDateDesc(Long assetId);
}
