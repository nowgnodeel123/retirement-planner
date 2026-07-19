package com.nowgnodeel.retirement_planner.asset.stock.repository;

import com.nowgnodeel.retirement_planner.asset.stock.entity.DomesticStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DomesticStockRepository extends JpaRepository<DomesticStock, String> {

    // 종목검색 자동완성용 (이름 부분일치, 최대 20건으로 프론트에서 제한)
    List<DomesticStock> findTop20ByNameContainingOrderByNameAsc(String keyword);
}