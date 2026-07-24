package com.nowgnodeel.retirement_planner.asset.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;
import com.nowgnodeel.retirement_planner.asset.entity.TransactionType;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 매도 검증(D-057)·평단 계산(D-050) 모두 이 순서 그대로 사용
    List<Transaction> findAllByAssetIdOrderByTradeDateAsc(Long assetId);

    // M6: 거래내역 화면은 최신순 표시가 자연스러움.
    // tradeDate는 날짜 단위(시각 없음)라 같은 날 여러 건이면 동점이 발생 —
    // id desc를 2차 정렬 기준으로 둬서 "같은 날짜 안에서는 나중에 입력한 게 위"가 되도록 보장한다.
    // (실동작 검증 중 발견: 오늘 매수→매도 했더니 매도가 아래로 가는 문제가 실제로 나타남)
    List<Transaction> findAllByAssetIdOrderByTradeDateDescIdDesc(Long assetId);

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
}