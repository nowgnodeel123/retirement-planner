package com.nowgnodeel.retirement_planner.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 매도 검증(D-057)·평단 계산(D-050) 모두 이 순서 그대로 사용
    List<Transaction> findAllByAssetIdOrderByTradeDateAsc(Long assetId);

    // M6: 거래내역 화면은 최신순 표시가 자연스러움.
    // tradeDate는 날짜 단위(시각 없음)라 같은 날 여러 건이면 동점이 발생 —
    // id desc를 2차 정렬 기준으로 둬서 "같은 날짜 안에서는 나중에 입력한 게 위"가 되도록 보장한다.
    // (실동작 검증 중 발견: 오늘 매수→매도 했더니 매도가 아래로 가는 문제가 실제로 나타남)
    List<Transaction> findAllByAssetIdOrderByTradeDateDescIdDesc(Long assetId);
}