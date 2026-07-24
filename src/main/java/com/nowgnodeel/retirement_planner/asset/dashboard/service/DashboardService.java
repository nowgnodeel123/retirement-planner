// asset/dashboard/service/DashboardService.java
package com.nowgnodeel.retirement_planner.asset.dashboard.service;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;
import com.nowgnodeel.retirement_planner.asset.entity.TransactionType;
import com.nowgnodeel.retirement_planner.asset.repository.TransactionRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nowgnodeel.retirement_planner.asset.dashboard.dto.DashboardDtos.*;
import static com.nowgnodeel.retirement_planner.asset.dto.AssetDtos.HoldingResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AssetService assetService;
    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;

    /**
     * M9: 총자산(D-066 손익 통합)/카테고리별 도넛차트 집계.
     * 계좌 상세 화면(app/portfolio/accounts/[accountId]/page.tsx)의 summary 계산과 동일한
     * "시세 미조회 자산 제외" 정책을 서버 사이드로 이식 — 해외주식은 krwEvaluationAmount/exchangeRate
     * 기준, 그 외는 evaluationAmount 기준으로 합산한다(D-087: 손익금액도 원화환산해 합산에 포함).
     */
    public PortfolioSummaryResponse getSummary(Long userId) {
        List<HoldingResponse> holdings = assetService.findAllHoldingsByUser(userId);

        BigDecimal totalKrw = BigDecimal.ZERO;
        BigDecimal profitKrw = BigDecimal.ZERO;
        int excludedCount = 0;
        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();

        for (HoldingResponse h : holdings) {
            BigDecimal evalKrw;
            BigDecimal profit;

            if ("FOREIGN_STOCK".equals(h.category())) {
                if (h.krwEvaluationAmount() != null && h.exchangeRate() != null) {
                    evalKrw = h.krwEvaluationAmount();
                    profit = h.profitAmount() != null
                            ? h.profitAmount().multiply(h.exchangeRate())
                            : BigDecimal.ZERO;
                } else {
                    excludedCount++;
                    continue;
                }
            } else if (h.evaluationAmount() != null) {
                evalKrw = h.evaluationAmount();
                profit = h.profitAmount() != null ? h.profitAmount() : BigDecimal.ZERO;
            } else {
                excludedCount++;
                continue;
            }

            totalKrw = totalKrw.add(evalKrw);
            profitKrw = profitKrw.add(profit);
            categoryTotals.merge(h.category(), evalKrw, BigDecimal::add);
            categoryCounts.merge(h.category(), 1, Integer::sum);
        }

        // 계좌 상세 화면과 동일한 규칙: 포함된 자산이 하나도 없으면(전부 제외) totalKrw를 null로 반환
        if (totalKrw.compareTo(BigDecimal.ZERO) == 0 && excludedCount > 0) {
            return new PortfolioSummaryResponse(null, BigDecimal.ZERO, BigDecimal.ZERO, excludedCount, List.of());
        }

        BigDecimal cost = totalKrw.subtract(profitKrw);
        BigDecimal profitRate = cost.compareTo(BigDecimal.ZERO) > 0
                ? profitKrw.divide(cost, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        List<CategorySummary> categories = categoryTotals.entrySet().stream()
                .map(e -> new CategorySummary(e.getKey(), e.getValue(), categoryCounts.get(e.getKey())))
                .sorted((a, b) -> b.totalKrw().compareTo(a.totalKrw()))
                .toList();

        return new PortfolioSummaryResponse(totalKrw, profitKrw, profitRate, excludedCount, categories);
    }

    /**
     * D-069: "이번 달 매매+배당 요약" 인사이트 배너.
     * Transaction.fx/Dividend.fx는 거래·지급 시점에 이미 저장돼 있어(D-063) 실시간
     * 환율 조회 없이 그대로 곱해 환산한다 — 국내주식/암호화폐는 fx가 null이라 원화 그대로 사용.
     */
    public MonthlyInsightResponse getMonthlyInsight(Long userId) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now();

        List<Transaction> txs = transactionRepository
                .findAllByAsset_Account_User_IdAndTradeDateBetween(userId, start, end);
        List<Dividend> dividends = dividendRepository
                .findAllByAsset_Account_User_IdAndPayDateBetween(userId, start, end);

        int buyCount = 0;
        int sellCount = 0;
        BigDecimal buyAmountKrw = BigDecimal.ZERO;
        BigDecimal sellAmountKrw = BigDecimal.ZERO;

        for (Transaction tx : txs) {
            BigDecimal amount = tx.getQuantity().multiply(tx.getUnitPrice());
            if (tx.getFx() != null) {
                amount = amount.multiply(tx.getFx());
            }
            if (tx.getType() == TransactionType.BUY) {
                buyCount++;
                buyAmountKrw = buyAmountKrw.add(amount);
            } else {
                sellCount++;
                sellAmountKrw = sellAmountKrw.add(amount);
            }
        }

        BigDecimal dividendAmountKrw = BigDecimal.ZERO;
        for (Dividend d : dividends) {
            BigDecimal amount = d.getAmount();
            if (d.getFx() != null) {
                amount = amount.multiply(d.getFx());
            }
            dividendAmountKrw = dividendAmountKrw.add(amount);
        }

        return new MonthlyInsightResponse(
                buyCount, buyAmountKrw, sellCount, sellAmountKrw, dividends.size(), dividendAmountKrw);
    }
}
