package com.nowgnodeel.retirement_planner.asset.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;
import com.nowgnodeel.retirement_planner.asset.entity.TransactionType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 매도 검증(D-057)·평단 계산(D-050) 모두 이 순서 그대로 사용
    List<Transaction> findAllByAssetIdOrderByTradeDateAsc(Long assetId);

    // M6: 거래내역 화면은 최신순 표시가 자연스러움.
    // tradeDate는 날짜 단위(시각 없음)라 같은 날 여러 건이면 동점이 발생 —
    // id desc를 2차 정렬 기준으로 둬서 "같은 날짜 안에서는 나중에 입력한 게 위"가 되도록 보장한다.
    // (실동작 검증 중 발견: 오늘 매수→매도 했더니 매도가 아래로 가는 문제가 실제로 나타남)
    List<Transaction> findAllByAssetIdOrderByTradeDateDescIdDesc(Long assetId);

    // 거래 수정/삭제 — 소유자 검증까지 한 번의 조회로 끝낸다(서비스 레이어 규칙).
    Optional<Transaction> findByIdAndAssetIdAndAsset_Account_User_Id(
            Long id, Long assetId, Long userId);

    // M9: "이번 달 매매 요약" 인사이트 배너용 — 계좌 전체 범위, 날짜 구간 필터
    List<Transaction> findAllByAsset_Account_User_IdAndTradeDateBetween(
            Long userId, LocalDate start, LocalDate end);

    // M10: 수익 탭(D-065) — 계좌 스코프 실현손익(SELL) 조회.
    // asset은 리스트 렌더링에 항상 필요해 EntityGraph로 fetch join(N+1 방지, 8장 계층 책임 원칙).
    @EntityGraph(attributePaths = "asset")
    List<Transaction> findAllByAsset_AccountIdAndTypeAndTradeDateBetween(
            Long accountId, TransactionType type, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = "asset")
    List<Transaction> findAllByAsset_AccountIdAndAsset_CategoryAndTypeAndTradeDateBetween(
            Long accountId, AssetCategory category, TransactionType type, LocalDate start, LocalDate end);

    // 전체(ALL) 기간 — 날짜 필터 없는 별도 오버로드(옵셔널 필터는 Java에서 분기, @Query 미사용 컨벤션 유지)
    @EntityGraph(attributePaths = "asset")
    List<Transaction> findAllByAsset_AccountIdAndType(Long accountId, TransactionType type);

    @EntityGraph(attributePaths = "asset")
    List<Transaction> findAllByAsset_AccountIdAndAsset_CategoryAndType(
            Long accountId, AssetCategory category, TransactionType type);

    // ── M15: 인별(사용자 전체) 스코프 ─────────────────────────────────────────
    // 수익·세금을 계좌별이 아니라 사람 단위로 집계한다. 세법상 기본공제(250만원)와
    // 금융소득 2천만원 기준이 인별 한도이기 때문이다(계좌별로 적용하면 공제를 계좌 수만큼
    // 중복해서 잡는다). 파생 메서드명으로 쓰면 detailType 필터까지 붙어 이름이 감당이
    // 안 되므로 이 두 건만 @Query를 쓴다.

    @EntityGraph(attributePaths = "asset")
    @Query("SELECT t FROM Transaction t WHERE t.asset.account.user.id = :userId " +
            "AND t.type = :type AND t.tradeDate BETWEEN :start AND :end")
    List<Transaction> findAllByUserAndTypeInPeriod(
            @Param("userId") Long userId, @Param("type") TransactionType type,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    @EntityGraph(attributePaths = "asset")
    @Query("SELECT t FROM Transaction t WHERE t.asset.account.user.id = :userId " +
            "AND t.type = :type AND t.asset.category = :category " +
            "AND t.tradeDate BETWEEN :start AND :end")
    List<Transaction> findAllByUserAndCategoryAndTypeInPeriod(
            @Param("userId") Long userId, @Param("category") AssetCategory category,
            @Param("type") TransactionType type,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 과세 대상 계좌만 — 세제혜택 계좌(ISA/IRP/연금저축)는 양도소득세 대상이 아니고(과세이연·
    // 저율분리과세), 은행 계좌는 매도 개념 자체가 없다. 이 필터를 빼면 연금저축 안의 해외 ETF
    // 매도차익이 양도세로 잘못 잡힌다.
    @EntityGraph(attributePaths = "asset")
    @Query("SELECT t FROM Transaction t WHERE t.asset.account.user.id = :userId " +
            "AND t.type = :type AND t.asset.category = :category " +
            "AND t.asset.account.detailType = com.nowgnodeel.retirement_planner.asset.entity.AccountDetailType.NORMAL " +
            "AND t.asset.account.institutionType <> com.nowgnodeel.retirement_planner.asset.entity.InstitutionType.BANK " +
            "AND t.tradeDate BETWEEN :start AND :end")
    List<Transaction> findTaxableByUserAndCategoryAndTypeInPeriod(
            @Param("userId") Long userId, @Param("category") AssetCategory category,
            @Param("type") TransactionType type,
            @Param("start") LocalDate start, @Param("end") LocalDate end);
}