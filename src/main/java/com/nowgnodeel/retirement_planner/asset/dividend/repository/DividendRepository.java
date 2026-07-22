// asset/dividend/repository/DividendRepository.java
package com.nowgnodeel.retirement_planner.asset.dividend.repository;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DividendRepository extends JpaRepository<Dividend, Long> {

    // D-092에서 발견된 "같은 날짜 여러 건 정렬 불안정" 문제를 배당에도 선제 적용 —
    // id desc를 2차 정렬키로 둬서 나중에 입력한 게 위로 오게 고정한다.
    List<Dividend> findAllByAssetIdOrderByPayDateDescIdDesc(Long assetId);

    // 삭제 API용: dividendId + assetId(경로 리소스 일치) + 소유자(userId)를 쿼리 한 번으로 강제.
    // AssetRepository.findByIdAndAccount_User_Id와 동일한 보안 원칙(8.1 소유자 검증).
    Optional<Dividend> findByIdAndAssetIdAndAsset_Account_User_Id(Long id, Long assetId, Long userId);
}
