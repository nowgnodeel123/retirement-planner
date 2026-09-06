package com.nowgnodeel.retirement_planner.asset.tax.service;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.entity.AccountDetailType;
import com.nowgnodeel.retirement_planner.asset.entity.Asset;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.entity.InstitutionType;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.asset.repository.TransactionRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.tax.dto.TaxDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * M15(D-232) 이후: 인별 스코프 세금 집계.
 * 이 클래스의 핵심 관심사는 "인별 한도가 인별로 딱 한 번만 적용되는가"다 —
 * 계좌별로 적용하던 것이 실제 결함이었다.
 */
@ExtendWith(MockitoExtension.class)
class TaxServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock DividendRepository dividendRepository;
    @Mock AssetService assetService;
    @InjectMocks TaxService taxService;

    private static final Long USER_ID = 1L;

    private Dividend dividendOf(AssetCategory category, BigDecimal amount, BigDecimal fx) {
        Asset asset = mock(Asset.class);
        when(asset.getCategory()).thenReturn(category);
        Dividend dividend = mock(Dividend.class);
        when(dividend.getAsset()).thenReturn(asset);
        when(dividend.getAmount()).thenReturn(amount);
        when(dividend.getFx()).thenReturn(fx);
        return dividend;
    }

    // 집계 범위는 detailType(세제혜택 여부)과 institutionType(거래소 여부)으로 갈린다.
    private Account accountOf(String name, InstitutionType institution, AccountDetailType detail) {
        Account account = mock(Account.class);
        when(account.getDetailType()).thenReturn(detail);
        lenient().when(account.getInstitutionType()).thenReturn(institution);
        boolean excluded = detail != AccountDetailType.NORMAL || institution == InstitutionType.EXCHANGE;
        if (excluded) {
            when(account.getName()).thenReturn(name);
        }
        return account;
    }

    private void noAccounts() {
        given(accountRepository.findAllByUserId(USER_ID)).willReturn(Collections.emptyList());
    }

    private void noSells() {
        given(transactionRepository.findTaxableByUserAndCategoryAndTypeInPeriod(any(), any(), any(), any(), any()))
                .willReturn(Collections.emptyList());
    }

    private void noDividends() {
        given(dividendRepository.findTaxableByUserInPeriod(any(), any(), any()))
                .willReturn(Collections.emptyList());
    }

    // ── 인별 한도 1회 적용 (D-232가 고친 결함) ─────────────────────────────

    @Test
    @DisplayName("D-232: 기본공제 250만원은 계좌가 몇 개든 전체 합계에서 딱 한 번만 뺀다")
    void basicDeduction_appliedOncePerPerson() {
        noAccounts();
        noDividends();

        // 서로 다른 계좌에서 발생한 매도 3건, 각 300만원 이익 → 합 900만원.
        // 계좌별로 공제하던 예전 방식이면 900만 - 750만 = 150만이 과세표준이 됐다.
        Transaction t1 = sellTxOnAsset(1L);
        Transaction t2 = sellTxOnAsset(2L);
        Transaction t3 = sellTxOnAsset(3L);
        given(transactionRepository.findTaxableByUserAndCategoryAndTypeInPeriod(any(), any(), any(), any(), any()))
                .willReturn(List.of(t1, t2, t3));
        given(assetService.loadTransactionsForAssetsOf(any())).willReturn(java.util.Map.of());
        given(assetService.calculateRealizedProfitKrw(any(), any())).willReturn(new BigDecimal("3000000"));

        TaxSummaryResponse result = taxService.getTaxForUser(USER_ID, 2026);
        CapitalGainsEstimate cg = result.capitalGains();

        assertThat(cg.realizedProfitKrw()).isEqualByComparingTo("9000000");
        assertThat(cg.basicDeductionKrw()).isEqualByComparingTo("2500000");
        // 900만 - 250만 = 650만 (750만을 빼는 계좌별 방식이 아님)
        assertThat(cg.taxableBaseKrw()).isEqualByComparingTo("6500000");
        assertThat(cg.estimatedTaxKrw()).isEqualByComparingTo("1430000"); // 650만 × 22%
    }

    @Test
    @DisplayName("실현손익이 기본공제보다 적으면 과세표준·세액 모두 0 (음수로 내려가지 않는다)")
    void basicDeduction_clampedAtZero() {
        noAccounts();
        noDividends();

        Transaction t1 = sellTxOnAsset(1L);
        given(transactionRepository.findTaxableByUserAndCategoryAndTypeInPeriod(any(), any(), any(), any(), any()))
                .willReturn(List.of(t1));
        given(assetService.loadTransactionsForAssetsOf(any())).willReturn(java.util.Map.of());
        given(assetService.calculateRealizedProfitKrw(any(), any())).willReturn(new BigDecimal("1000000"));

        CapitalGainsEstimate cg = taxService.getTaxForUser(USER_ID, 2026).capitalGains();

        assertThat(cg.taxableBaseKrw()).isEqualByComparingTo("0");
        assertThat(cg.estimatedTaxKrw()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("D-232: 2천만원 기준은 계좌를 합친 뒤 한 번만 판정한다")
    void dividendThreshold_judgedOncePerPerson() {
        noAccounts();
        noSells();

        // 서로 다른 계좌의 해외 배당 3건 × 700만원 = 2,100만원 → 합치면 기준 초과.
        // 계좌별로 판정하던 예전 방식이면 세 계좌 모두 "미달"로 답했다.
        List<Dividend> threeAccounts = List.of(
                dividendOf(AssetCategory.FOREIGN_STOCK, new BigDecimal("7000000"), null),
                dividendOf(AssetCategory.FOREIGN_STOCK, new BigDecimal("7000000"), null),
                dividendOf(AssetCategory.FOREIGN_STOCK, new BigDecimal("7000000"), null));
        given(dividendRepository.findTaxableByUserInPeriod(any(), any(), any())).willReturn(threeAccounts);

        DividendIncomeJudgement judgement = taxService.getTaxForUser(USER_ID, 2026).dividendIncome();

        assertThat(judgement.totalDividendKrw()).isEqualByComparingTo("21000000");
        assertThat(judgement.exceedsThreshold()).isTrue();
        assertThat(judgement.judgement()).isEqualTo(DividendTaxJudgement.COMPREHENSIVE_FILING_POSSIBLE);
    }

    // ── 집계 대상 계좌 범위 ────────────────────────────────────────────────

    @Test
    @DisplayName("세제혜택 계좌와 거래소 계좌를 빼고, 뺀 이유까지 응답에 담는다")
    void scope_excludesTaxAdvantagedAndExchangeAccounts() {
        List<Account> accounts = List.of(
                accountOf("키움 위탁", InstitutionType.SECURITIES, AccountDetailType.NORMAL),
                accountOf("업비트", InstitutionType.EXCHANGE, AccountDetailType.NORMAL),
                accountOf("미래에셋 연금저축", InstitutionType.SECURITIES, AccountDetailType.PENSION_SAVINGS),
                accountOf("삼성 IRP", InstitutionType.SECURITIES, AccountDetailType.IRP),
                accountOf("국민 ISA", InstitutionType.SECURITIES, AccountDetailType.ISA));
        given(accountRepository.findAllByUserId(USER_ID)).willReturn(accounts);
        noSells();
        noDividends();

        TaxScope scope = taxService.getTaxForUser(USER_ID, 2026).scope();

        // 업비트는 암호화폐만 담기고 이 앱은 가상자산 세금을 추정하지 않는다 —
        // 과세 대상으로 세면 화면이 "계산했다"고 말하면서 실제로는 한 푼도 안 넣게 된다.
        assertThat(scope.taxableAccountCount()).isEqualTo(1);
        assertThat(scope.excludedAccountCount()).isEqualTo(4);
        assertThat(scope.excludedAccounts())
                .extracting(ExcludedAccount::name, ExcludedAccount::reason)
                .containsExactly(
                        tuple("업비트", ExclusionReason.CRYPTO_ONLY),
                        tuple("미래에셋 연금저축", ExclusionReason.TAX_ADVANTAGED),
                        tuple("삼성 IRP", ExclusionReason.TAX_ADVANTAGED),
                        tuple("국민 ISA", ExclusionReason.TAX_ADVANTAGED));
    }

    // ── 배당 세전 역환산 (R-016) — 인별로 올려도 규칙은 그대로 ─────────────

    @Test
    @DisplayName("R-016: 국내주식 배당(세후 순액)은 15.4% 원천징수율로 세전 역환산해 합산한다")
    void dividend_grossesUpDomestic() {
        noAccounts();
        noSells();

        // 세후 84,600원 저장 → 세전 100,000원 (84,600 / (1 - 0.154))
        Dividend domestic = dividendOf(AssetCategory.DOMESTIC_STOCK, new BigDecimal("84600"), null);
        given(dividendRepository.findTaxableByUserInPeriod(any(), any(), any())).willReturn(List.of(domestic));

        DividendIncomeJudgement judgement = taxService.getTaxForUser(USER_ID, 2026).dividendIncome();

        assertThat(judgement.totalDividendKrw()).isEqualByComparingTo("100000");
        assertThat(judgement.dividendGrossedUp()).isTrue();
    }

    @Test
    @DisplayName("해외주식 배당은 세전 역환산하지 않고, 그 사실을 건수로 알린다")
    void dividend_doesNotGrossUpForeign() {
        noAccounts();
        noSells();

        // $100 × 1,300 = 130,000원. 미국 원천징수 15%를 되돌리면 152,941원이지만
        // 원천징수율이 국가마다 달라 추정하지 않는다(R-009). 대신 화면이 밝히도록
        // 해외 배당 건수를 함께 내려준다.
        Dividend foreign = dividendOf(
                AssetCategory.FOREIGN_STOCK, new BigDecimal("100"), new BigDecimal("1300"));
        given(dividendRepository.findTaxableByUserInPeriod(any(), any(), any()))
                .willReturn(List.of(foreign));

        DividendIncomeJudgement judgement = taxService.getTaxForUser(USER_ID, 2026).dividendIncome();

        assertThat(judgement.totalDividendKrw()).isEqualByComparingTo("130000");
        assertThat(judgement.foreignDividendCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("국내·해외가 섞이면 국내분만 역환산해 합산한다")
    void dividend_mixedDomesticAndForeign() {
        noAccounts();
        noSells();

        Dividend domestic = dividendOf(AssetCategory.DOMESTIC_STOCK, new BigDecimal("84600"), null);
        Dividend foreign = dividendOf(
                AssetCategory.FOREIGN_STOCK, new BigDecimal("100"), new BigDecimal("1300"));
        given(dividendRepository.findTaxableByUserInPeriod(any(), any(), any()))
                .willReturn(List.of(domestic, foreign));

        DividendIncomeJudgement judgement = taxService.getTaxForUser(USER_ID, 2026).dividendIncome();

        // 100,000(역환산) + 130,000(원값) = 230,000
        assertThat(judgement.totalDividendKrw()).isEqualByComparingTo("230000");
        assertThat(judgement.foreignDividendCount()).isEqualTo(1);
        assertThat(judgement.dividendCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("해외주식 배당은 세전 환산 없이 fx만 곱해 원화로 합산한다")
    void dividend_foreignNotGrossedUp() {
        noAccounts();
        noSells();

        Dividend foreign = dividendOf(AssetCategory.FOREIGN_STOCK, new BigDecimal("100"), new BigDecimal("1300"));
        given(dividendRepository.findTaxableByUserInPeriod(any(), any(), any())).willReturn(List.of(foreign));

        DividendIncomeJudgement judgement = taxService.getTaxForUser(USER_ID, 2026).dividendIncome();

        assertThat(judgement.totalDividendKrw()).isEqualByComparingTo("130000");
    }

    @Test
    @DisplayName("이자소득 미추적 캐비트는 항상 켜져 있다(프론트 상시 노출용)")
    void dividend_interestNotTrackedCaveatAlwaysOn() {
        noAccounts();
        noSells();
        noDividends();

        assertThat(taxService.getTaxForUser(USER_ID, 2026).dividendIncome().interestIncomeNotTracked()).isTrue();
    }

    /** 거래는 반드시 자산에 속한다(FK not-null). 집계 경로가 자산 id로 거래를 묶으므로 목도 그 상태를 지킨다. */
    private Transaction sellTxOnAsset(Long assetId) {
        Transaction tx = mock(Transaction.class);
        com.nowgnodeel.retirement_planner.asset.entity.Asset asset =
                mock(com.nowgnodeel.retirement_planner.asset.entity.Asset.class);
        lenient().when(asset.getId()).thenReturn(assetId);
        lenient().when(tx.getAsset()).thenReturn(asset);
        return tx;
    }
}
