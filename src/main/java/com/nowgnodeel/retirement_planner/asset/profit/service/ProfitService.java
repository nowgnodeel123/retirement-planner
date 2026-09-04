// asset/profit/service/ProfitService.java
package com.nowgnodeel.retirement_planner.asset.profit.service;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Asset;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;
import com.nowgnodeel.retirement_planner.asset.entity.TransactionType;
import com.nowgnodeel.retirement_planner.asset.repository.TransactionRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.profit.dto.ProfitDtos.*;

/**
 * M10(D-065) → M15(D-232)에서 인별 스코프로 승격. 기간×카테고리 필터로 실현손익+배당 조회.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfitService {

    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;
    private final AssetService assetService;

    /**
     * M15(D-232): 인별 집계. "내 실현손익이 얼마인가"는 계좌가 아니라 사람 단위의 질문이라
     * 계좌 스코프 API는 폐지했다. 세금과 달리 계좌 유형으로 걸러내지 않는다 — 연금저축·IRP의
     * 실현손익·배당도 사용자에게는 엄연한 수익이다(과세만 이연될 뿐이다).
     * 여기서 계좌를 빼면 "번 돈"을 축소해서 보여주게 된다.
     */
    public ProfitSummaryResponse getProfitForUser(Long userId, ProfitPeriod period, AssetCategory category) {
        LocalDate[] range = resolveRange(period);
        List<Transaction> sells = fetchSells(userId, category, range[0], range[1]);
        List<Dividend> dividends = fetchDividends(userId, category, range[0], range[1]);

        List<ProfitItem> items = new ArrayList<>();
        BigDecimal realizedTotal = BigDecimal.ZERO;

        for (Transaction tx : sells) {
            Asset asset = tx.getAsset();
            // D-107: AssetService.calculateRealizedProfitKrw가 공식의 단일 출처(tax 패키지와 공유).
            BigDecimal profit = assetService.calculateRealizedProfitKrw(tx);
            realizedTotal = realizedTotal.add(profit);

            items.add(new ProfitItem(
                    "REALIZED_SELL", tx.getId(), asset.getId(), asset.getName(),
                    asset.getAccount().getName(),
                    asset.getCategory().name(), tx.getTradeDate(), profit));
        }

        BigDecimal dividendTotal = BigDecimal.ZERO;
        for (Dividend d : dividends) {
            Asset asset = d.getAsset();
            BigDecimal amount = d.getFx() != null ? d.getAmount().multiply(d.getFx()) : d.getAmount();
            dividendTotal = dividendTotal.add(amount);

            items.add(new ProfitItem(
                    "DIVIDEND", d.getId(), asset.getId(), asset.getName(),
                    asset.getAccount().getName(),
                    asset.getCategory().name(), d.getPayDate(), amount));
        }

        items.sort(Comparator.comparing(ProfitItem::date).reversed());

        long allTimeSells = category != null
                ? transactionRepository.countByAsset_Account_User_IdAndAsset_CategoryAndType(userId, category, TransactionType.SELL)
                : transactionRepository.countByAsset_Account_User_IdAndType(userId, TransactionType.SELL);
        long allTimeDividends = category != null
                ? dividendRepository.countByAsset_Account_User_IdAndAsset_Category(userId, category)
                : dividendRepository.countByAsset_Account_User_Id(userId);

        return new ProfitSummaryResponse(
                realizedTotal, dividendTotal, realizedTotal.add(dividendTotal),
                sells.size(), dividends.size(), items,
                range[0], range[1], (int) (allTimeSells + allTimeDividends));
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

    // 기간 ALL(start=null)은 날짜 경계를 아주 넓게 잡아 같은 쿼리로 처리한다 —
    // 계좌 스코프 때는 오버로드로 분기했지만, 인별 쿼리는 @Query라 분기가 곱절로 늘어난다.
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);

    private List<Transaction> fetchSells(Long userId, AssetCategory category, LocalDate start, LocalDate end) {
        LocalDate from = start != null ? start : MIN_DATE;
        LocalDate to = end != null ? end : MAX_DATE;
        return category != null
                ? transactionRepository.findAllByUserAndCategoryAndTypeInPeriod(userId, category, TransactionType.SELL, from, to)
                : transactionRepository.findAllByUserAndTypeInPeriod(userId, TransactionType.SELL, from, to);
    }

    private List<Dividend> fetchDividends(Long userId, AssetCategory category, LocalDate start, LocalDate end) {
        LocalDate from = start != null ? start : MIN_DATE;
        LocalDate to = end != null ? end : MAX_DATE;
        return category != null
                ? dividendRepository.findAllByUserAndCategoryInPeriod(userId, category, from, to)
                : dividendRepository.findAllByUserInPeriod(userId, from, to);
    }
}
