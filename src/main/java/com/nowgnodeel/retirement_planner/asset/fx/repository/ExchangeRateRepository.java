// src/main/java/com/nowgnodeel/retirement_planner/asset/fx/repository/ExchangeRateRepository.java

package com.nowgnodeel.retirement_planner.asset.fx.repository;

import com.nowgnodeel.retirement_planner.asset.fx.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, String> {
}