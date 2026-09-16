package com.nowgnodeel.retirement_planner.simulation.repository;

import com.nowgnodeel.retirement_planner.simulation.entity.RetirementProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetirementProfileRepository extends JpaRepository<RetirementProfile, Long> {

    // 소유자 검증: 프로필은 사용자당 1행이고, 조회는 항상 userId로만 한다.
    // (profileId를 외부에 노출하지 않으므로 남의 행에 접근할 경로 자체가 없다)
    Optional<RetirementProfile> findByUserId(Long userId);
}
