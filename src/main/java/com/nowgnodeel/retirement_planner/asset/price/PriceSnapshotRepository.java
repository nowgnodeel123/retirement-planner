package com.nowgnodeel.retirement_planner.asset.price;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 시세 스냅샷은 사용자에 속한 데이터가 아니라 종목 단위 공용 값이라 소유자 스코프가 없다
 * (자산·거래·배당 리포지토리와 다른 점 — 소유자 검증 원칙의 대상이 아니다).
 */
public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, String> {
}
