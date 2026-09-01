// asset/tax/service/TaxService.java
package com.nowgnodeel.retirement_planner.asset.tax.service;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.entity.AccountDetailType;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.entity.InstitutionType;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;
import com.nowgnodeel.retirement_planner.asset.entity.TransactionType;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.asset.repository.TransactionRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.tax.dto.TaxDtos.*;

/**
 * M11(D-064/D-068) → M15(D-232)에서 인별 스코프로 승격. 세금 탭. 세금계산기 완전판 재도입 금지(0장 절대원칙) —
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
    // R-016 대응: 국내주식 배당소득세 원천징수율(소득세 14%+지방소득세 1.4%). Dividend.amount는
    // 국내주식=세후 순액(D-067)으로 저장되므로, 2천만원 기준 판정 전 세전 금액으로 역환산해야 한다.
    private static final BigDecimal DOMESTIC_DIVIDEND_WITHHOLDING_RATE = new BigDecimal("0.154");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;
    private final AssetService assetService;

    /**
     * M15(D-232): 인별 집계. 계좌별로 돌리던 것을 사람 단위로 올린다.
     *
     * WHY: 양도소득세 기본공제 250만원과 금융소득종합과세 2천만원 기준은 **인별 연간 한도**다.
     * 계좌별로 계산하면 증권 계좌가 3개일 때 공제를 750만원까지 잡아 세금을 실제보다 적게
     * 추정하고, 배당도 계좌마다 따로 판정해 합계 2,100만원인 사람에게 세 계좌 모두
     * "기준 미달"이라고 답한다. 둘 다 세법을 잘못 적용한 것이라 계좌 스코프 API는 폐지했다.
     *
     * 대상 계좌는 일반(NORMAL) 증권·거래소 계좌뿐이다 — ISA·IRP·연금저축은 과세이연/저율
     * 분리과세라 양도소득세·금융소득 합산 대상이 아니고, 은행 계좌는 매도·배당 개념이 없다.
     */
    public TaxSummaryResponse getTaxForUser(Long userId, int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        List<Account> accounts = accountRepository.findAllByUserId(userId);
        List<String> excludedNames = accounts.stream()
                .filter(a -> !isTaxScoped(a))
                .map(Account::getName)
                .toList();
        int taxableCount = accounts.size() - excludedNames.size();

        return new TaxSummaryResponse(
                year,
                calculateCapitalGainsForUser(userId, start, end),
                calculateDividendIncomeForUser(userId, start, end),
                new TaxScope(taxableCount, excludedNames.size(), excludedNames)
        );
    }

    /** 위 쿼리들의 detailType/institutionType 조건과 반드시 같은 규칙이어야 한다. */
    private boolean isTaxScoped(Account account) {
        return account.getDetailType() == AccountDetailType.NORMAL
                && account.getInstitutionType() != InstitutionType.BANK;
    }

    // D-064: 해외주식만 대상, 국내주식은 조회 자체를 하지 않는다.
    // 기본공제는 전체 합계에서 딱 한 번 뺀다(인별 한도).
    private CapitalGainsEstimate calculateCapitalGainsForUser(Long userId, LocalDate start, LocalDate end) {
        List<Transaction> sells = transactionRepository.findTaxableByUserAndCategoryAndTypeInPeriod(
                userId, AssetCategory.FOREIGN_STOCK, TransactionType.SELL, start, end);

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

    // D-068 + R-016 규칙은 계좌 스코프 때와 동일하고, 합산 범위만 사람 단위로 넓어진다.
    private DividendIncomeJudgement calculateDividendIncomeForUser(Long userId, LocalDate start, LocalDate end) {
        List<Dividend> dividends = dividendRepository.findTaxableByUserInPeriod(userId, start, end);
        return judgeDividends(dividends);
    }

    // D-068: 실제 세액 미계산, 분리과세 종결 vs 종합소득 신고 가능성 판정만.
    // R-016: 국내주식 배당(세후 순액)을 세전으로 역환산한 뒤 합산해야 2천만원 기준과 같은 기준(세전)으로 비교된다.
    private DividendIncomeJudgement judgeDividends(List<Dividend> dividends) {
        BigDecimal totalDividend = BigDecimal.ZERO;
        for (Dividend d : dividends) {
            BigDecimal amount = d.getFx() != null ? d.getAmount().multiply(d.getFx()) : d.getAmount();
            if (d.getAsset().getCategory() == AssetCategory.DOMESTIC_STOCK) {
                amount = amount.divide(BigDecimal.ONE.subtract(DOMESTIC_DIVIDEND_WITHHOLDING_RATE), 0, RoundingMode.HALF_UP);
            }
            totalDividend = totalDividend.add(amount);
        }

        boolean exceeds = totalDividend.compareTo(DIVIDEND_INCOME_THRESHOLD) > 0;
        DividendTaxJudgement judgement = exceeds
                ? DividendTaxJudgement.COMPREHENSIVE_FILING_POSSIBLE
                : DividendTaxJudgement.SEPARATE_TAXATION_FINAL;

        return new DividendIncomeJudgement(
                totalDividend, DIVIDEND_INCOME_THRESHOLD, exceeds, judgement, true, true, dividends.size());
    }
}
