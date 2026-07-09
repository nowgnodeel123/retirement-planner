package com.nowgnodeel.retirement_planner.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DividendRepository extends JpaRepository<Dividend, Long> {

    List<Dividend> findAllByAssetIdOrderByPayDateDesc(Long assetId);
}
