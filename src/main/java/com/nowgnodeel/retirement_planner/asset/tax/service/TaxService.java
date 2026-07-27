// asset/tax/service/TaxService.java
package com.nowgnodeel.retirement_planner.asset.tax.service;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.tax.dto.TaxDtos.*;

/**
 * M11(D-064/D-068): 계좌 상세 세금 탭. 세금계산기 완전판 재도입 금지(0장 절대원칙) —
 * 두 값 모두 "추정/판정"이며, 계산이 불가한 영역(종합소득세 실액, 이자소득 반영)은
 * 계산 자체를 시도하지 않고 안내로 대체한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxService {

    private static final BigDecimal BASIC_DEDUCTION = new BigDecimal("2500000");
    private static final BigDecimal CAPITAL_GAINS_TAX_RATE = new BigDecimal("0.22"); // 지방소득세 포함
    private static final BigDecimal DIVIDEND_INCOME_THRESHOLD = new BigDecimal("20000000");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;
    private final AssetService assetService;

    public TaxSummaryResponse getTax(Long userId, Long accountId, int year) {
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));

        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        return new TaxSummaryResponse(
                year,
                calculateCapitalGains(accountId, start, end),
                calculateDividendIncome(accountId, start, end)
        );
    }

    // D-064: 해외주식만 대상, 국내주식은 조회 자체를 하지 않는다.
    private CapitalGainsEstimate calculateCapitalGains(Long accountId, LocalDate start, LocalDate end) {
        List<Transaction> sells = transactionRepository
                .findAllByAsset_AccountIdAndAsset_CategoryAndTypeAndTradeDateBetween(
                        accountId, AssetCategory.FOREIGN_STOCK, TransactionType.SELL, start, end);

        BigDecimal realizedTotal = BigDecimal.ZERO;
        for (Transaction tx : sells) {
            // D-107 공식의 단일 출처 재사용 — 수익 탭과 동일 계산(R-015 대응)
            realizedTotal = realizedTotal.add(assetService.calculateRealizedProfitKrw(tx));
        }

        BigDecimal taxableBase = realizedTotal.subtract(BASIC_DEDUCTION);
        if (taxableBase.compareTo(BigDecimal.ZERO) < 0) {
            taxableBase = BigDecimal.ZERO;
        }
        BigDecimal estimatedTax = taxableBase.multiply(CAPITAL_GAINS_TAX_RATE)
                .setScale(0, RoundingMode.HALF_UP);

        return new CapitalGainsEstimate(
                realizedTotal, BASIC_DEDUCTION, taxableBase, CAPITAL_GAINS_TAX_RATE, estimatedTax, sells.size());
    }

    // D-068: 실제 세액 미계산, 분리과세 종결 vs 종합소득 신고 가능성 판정만.
    private DividendIncomeJudgement calculateDividendIncome(Long accountId, LocalDate start, LocalDate end) {
        List<Dividend> dividends = dividendRepository.findAllByAsset_AccountIdAndPayDateBetween(accountId, start, end);

        BigDecimal totalDividend = BigDecimal.ZERO;
        for (Dividend d : dividends) {
            BigDecimal amount = d.getFx() != null ? d.getAmount().multiply(d.getFx()) : d.getAmount();
            totalDividend = totalDividend.add(amount);
        }

        boolean exceeds = totalDividend.compareTo(DIVIDEND_INCOME_THRESHOLD) > 0;
        DividendTaxJudgement judgement = exceeds
                ? DividendTaxJudgement.COMPREHENSIVE_FILING_POSSIBLE
                : DividendTaxJudgement.SEPARATE_TAXATION_FINAL;

        return new DividendIncomeJudgement(
                totalDividend, DIVIDEND_INCOME_THRESHOLD, exceeds, judgement, true, dividends.size());
    }
}
