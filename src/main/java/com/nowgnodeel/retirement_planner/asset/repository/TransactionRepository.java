package com.nowgnodeel.retirement_planner.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 매도 검증(D-057)·평단 계산(D-050) 모두 이 순서 그대로 사용
    List<Transaction> findAllByAssetIdOrderByTradeDateAsc(Long assetId);
}
