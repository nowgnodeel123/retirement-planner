package com.nowgnodeel.retirement_planner.asset.stock.dto;

/**
 * D-140/D-148/D-153: 금융위원회_주식배당정보(data.go.kr, GetStocDiviInfoService_V2) 조회 결과.
 * ⚠ 이 데이터는 공공누리 2유형(출처표시+상업적 이용금지) — 네스트가 상업 서비스로 전환되면
 * 데이터 원천 소유자인 한국예탁결제원과 별도 정보이용계약이 필요할 수 있다(개발단계 전용 기능).
 */
public record DividendScheduleResult(
        String recordDate,        // dvdnBasDt: 배당기준일자
        String cashPayDate,       // cashDvdnPayDt: 현금배당지급일자
        String stockDeliveryDate, // stckHndvDt: 주식교부일자
        String stockIssuerName,   // stckIssuCmpyNm
        String generalDividendAmount, // stckGenrDvdnAmt: 주식일반배당금액
        String dividendRate       // stckGenrDvdnRt: 배당률
) {}
