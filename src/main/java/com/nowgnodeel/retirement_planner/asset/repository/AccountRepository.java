package com.nowgnodeel.retirement_planner.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByUserId(Long userId);

    // 보안 원칙: 컨트롤러가 accountId만 주더라도 소유자 검증을 반드시 이 메서드로 강제
    Optional<Account> findByIdAndUserId(Long id, Long userId);
}
