// asset/dividend/repository/DividendRepository.java
package com.nowgnodeel.retirement_planner.asset.dividend.repository;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DividendRepository extends JpaRepository<Dividend, Long> {

    // D-092에서 발견된 "같은 날짜 여러 건 정렬 불안정" 문제를 배당에도 선제 적용 —
    // id desc를 2차 정렬키로 둬서 나중에 입력한 게 위로 오게 고정한다.
    List<Dividend> findAllByAssetIdOrderByPayDateDescIdDesc(Long assetId);

    // 삭제 API용: dividendId + assetId(경로 리소스 일치) + 소유자(userId)를 쿼리 한 번으로 강제.
    // AssetRepository.findByIdAndAccount_User_Id와 동일한 보안 원칙(8.1 소유자 검증).
    Optional<Dividend> findByIdAndAssetIdAndAsset_Account_User_Id(Long id, Long assetId, Long userId);

    // M9: "이번 달 배당 요약" 인사이트 배너용 — 계좌 전체 범위, 날짜 구간 필터
    List<Dividend> findAllByAsset_Account_User_IdAndPayDateBetween(
            Long userId, LocalDate start, LocalDate end);

    // M10: 수익 탭(D-065) — 계좌 스코프 배당 조회. asset은 리스트 렌더링에 항상 필요해
    // EntityGraph로 fetch join(N+1 방지, 8장 계층 책임 원칙).
    @EntityGraph(attributePaths = "asset")
    List<Dividend> findAllByAsset_AccountIdAndPayDateBetween(
            Long accountId, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = "asset")
    List<Dividend> findAllByAsset_AccountIdAndAsset_CategoryAndPayDateBetween(
            Long accountId, AssetCategory category, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = "asset")
    List<Dividend> findAllByAsset_AccountId(Long accountId);

    @EntityGraph(attributePaths = "asset")
    List<Dividend> findAllByAsset_AccountIdAndAsset_Category(Long accountId, AssetCategory category);
}
