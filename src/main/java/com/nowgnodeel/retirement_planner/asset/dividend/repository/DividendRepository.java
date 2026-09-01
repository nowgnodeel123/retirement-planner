// asset/dividend/repository/DividendRepository.java
package com.nowgnodeel.retirement_planner.asset.dividend.repository;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 선택한 기간 밖에도 내역이 있는지 알려주기 위한 전체 건수(D-237).
    long countByAsset_Account_User_Id(Long userId);

    long countByAsset_Account_User_IdAndAsset_Category(Long userId, AssetCategory category);

    // ── M15: 인별(사용자 전체) 스코프 ─────────────────────────────────────────
    @EntityGraph(attributePaths = "asset")
    @Query("SELECT d FROM Dividend d WHERE d.asset.account.user.id = :userId " +
            "AND d.payDate BETWEEN :start AND :end")
    List<Dividend> findAllByUserInPeriod(
            @Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @EntityGraph(attributePaths = "asset")
    @Query("SELECT d FROM Dividend d WHERE d.asset.account.user.id = :userId " +
            "AND d.asset.category = :category AND d.payDate BETWEEN :start AND :end")
    List<Dividend> findAllByUserAndCategoryInPeriod(
            @Param("userId") Long userId, @Param("category") AssetCategory category,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 금융소득종합과세 2천만원 판정 대상만 — 세제혜택 계좌(ISA/IRP/연금저축)의 배당은
    // 금융소득에 합산되지 않는다(인출 시 별도 과세). 이 필터를 빼면 연금계좌 배당까지
    // 2천만원 기준에 더해져 "종합과세 대상"이라고 잘못 판정한다.
    @EntityGraph(attributePaths = "asset")
    @Query("SELECT d FROM Dividend d WHERE d.asset.account.user.id = :userId " +
            "AND d.asset.account.detailType = com.nowgnodeel.retirement_planner.asset.entity.AccountDetailType.NORMAL " +
            "AND d.asset.account.institutionType <> com.nowgnodeel.retirement_planner.asset.entity.InstitutionType.BANK " +
            "AND d.payDate BETWEEN :start AND :end")
    List<Dividend> findTaxableByUserInPeriod(
            @Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
