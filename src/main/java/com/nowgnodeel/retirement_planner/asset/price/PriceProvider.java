package com.nowgnodeel.retirement_planner.asset.price;

import java.math.BigDecimal;

/**
 * 국내주식/해외주식/코인 3개 공급자를 하나의 인터페이스로 통일.
 * 각 구현체는 자기 통화 기준 현재가만 반환한다(원화환산은 M5 환율 연동 스코프).
 */
public interface PriceProvider {
    BigDecimal getCurrentPrice(String symbol);
}