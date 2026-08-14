package com.nowgnodeel.retirement_planner.asset.stock.repository;

import com.nowgnodeel.retirement_planner.asset.stock.entity.CorpCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorpCodeRepository extends JpaRepository<CorpCode, String> {
}
