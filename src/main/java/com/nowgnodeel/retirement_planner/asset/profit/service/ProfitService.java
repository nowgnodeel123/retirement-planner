// asset/profit/service/ProfitService.java
package com.nowgnodeel.retirement_planner.asset.profit.service;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Asset;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;
import com.nowgnodeel.retirement_planner.asset.entity.TransactionType;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.asset.repository.TransactionRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nowgnodeel.retirement_planner.asset.profit.dto.ProfitDtos.*;

/**
 * M10(D-065): 계좌 상세 수익 탭 — 기간×카테고리 필터로 실현손익+배당 조회.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfitService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;
    private final AssetService assetService;

    public ProfitSummaryResponse getProfit(Long userId, Long accountId, ProfitPeriod period, AssetCategory category) {
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));

        LocalDate[] range = resolveRange(period);
        List<Transaction> sells = fetchSells(accountId, category, range[0], range[1]);
        List<Dividend> dividends = fetchDividends(accountId, category, range[0], range[1]);

        Map<Long, BigDecimal> avgPriceCache = new HashMap<>();
        List<ProfitItem> items = new ArrayList<>();
        BigDecimal realizedTotal = BigDecimal.ZERO;

        for (Transaction tx : sells) {
            Asset asset = tx.getAsset();
            // D-050 원칙의 연장(신규 아님): 매도 시점 재계산 없이 전체 매수 내역 기준 평단을
            // 그대로 실현손익 원가로 사용한다(AssetService.toHoldingResponse와 동일 근거, M11 재검토 전제).
            BigDecimal avgPrice = avgPriceCache.computeIfAbsent(
                    asset.getId(), id -> assetService.calculateAveragePrice(asset));

            BigDecimal profit = tx.getUnitPrice().subtract(avgPrice).multiply(tx.getQuantity());
            if (tx.getFx() != null) {
                profit = profit.multiply(tx.getFx()); // D-104: 매도 시점 저장된 fx만 사용, 재조회 없음
            }
            realizedTotal = realizedTotal.add(profit);

            items.add(new ProfitItem(
                    "REALIZED_SELL", tx.getId(), asset.getId(), asset.getName(),
                    asset.getCategory().name(), tx.getTradeDate(), profit));
        }

        BigDecimal dividendTotal = BigDecimal.ZERO;
        for (Dividend d : dividends) {
            Asset asset = d.getAsset();
            BigDecimal amount = d.getFx() != null ? d.getAmount().multiply(d.getFx()) : d.getAmount();
            dividendTotal = dividendTotal.add(amount);

            items.add(new ProfitItem(
                    "DIVIDEND", d.getId(), asset.getId(), asset.getName(),
                    asset.getCategory().name(), d.getPayDate(), amount));
        }

        items.sort(Comparator.comparing(ProfitItem::date).reversed());

        return new ProfitSummaryResponse(
                realizedTotal, dividendTotal, realizedTotal.add(dividendTotal),
                sells.size(), dividends.size(), items);
    }

    // 일/주/월/년/전체 → LocalDate(start,end). 달력 기준(M9 "이번 달" 관례의 일반화).
    // 전체는 {null, null} — 날짜 필터 없이 전체 조회.
    private LocalDate[] resolveRange(ProfitPeriod period) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case DAY -> new LocalDate[]{today, today};
            case WEEK -> new LocalDate[]{today.with(DayOfWeek.MONDAY), today};
            case MONTH -> new LocalDate[]{today.withDayOfMonth(1), today};
            case YEAR -> new LocalDate[]{today.withDayOfYear(1), today};
            case ALL -> new LocalDate[]{null, null};
        };
    }

    private List<Transaction> fetchSells(Long accountId, AssetCategory category, LocalDate start, LocalDate end) {
        if (start == null) {
            return category != null
                    ? transactionRepository.findAllByAsset_AccountIdAndAsset_CategoryAndType(accountId, category, TransactionType.SELL)
                    : transactionRepository.findAllByAsset_AccountIdAndType(accountId, TransactionType.SELL);
        }
        return category != null
                ? transactionRepository.findAllByAsset_AccountIdAndAsset_CategoryAndTypeAndTradeDateBetween(accountId, category, TransactionType.SELL, start, end)
                : transactionRepository.findAllByAsset_AccountIdAndTypeAndTradeDateBetween(accountId, TransactionType.SELL, start, end);
    }

    private List<Dividend> fetchDividends(Long accountId, AssetCategory category, LocalDate start, LocalDate end) {
        if (start == null) {
            return category != null
                    ? dividendRepository.findAllByAsset_AccountIdAndAsset_Category(accountId, category)
                    : dividendRepository.findAllByAsset_AccountId(accountId);
        }
        return category != null
                ? dividendRepository.findAllByAsset_AccountIdAndAsset_CategoryAndPayDateBetween(accountId, category, start, end)
                : dividendRepository.findAllByAsset_AccountIdAndPayDateBetween(accountId, start, end);
    }
}
