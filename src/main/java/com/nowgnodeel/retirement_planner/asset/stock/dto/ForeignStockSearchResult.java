package com.nowgnodeel.retirement_planner.asset.stock.dto;

// 자산 추가 화면 종목검색 자동완성 (해외주식) — Finnhub 심볼 검색 응답 매핑
public record ForeignStockSearchResult(String symbol, String name, String type) {
}
