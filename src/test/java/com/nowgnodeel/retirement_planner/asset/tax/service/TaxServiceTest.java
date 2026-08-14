package com.nowgnodeel.retirement_planner.asset.tax.service;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.entity.Asset;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.asset.repository.TransactionRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.nowgnodeel.retirement_planner.asset.tax.dto.TaxDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock DividendRepository dividendRepository;
    @Mock AssetService assetService;
    @InjectMocks TaxService taxService;

    private static final Long USER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;

    private Dividend dividendOf(AssetCategory category, BigDecimal amount, BigDecimal fx) {
        Asset asset = mock(Asset.class);
        when(asset.getCategory()).thenReturn(category);
        Dividend dividend = mock(Dividend.class);
        when(dividend.getAsset()).thenReturn(asset);
        when(dividend.getAmount()).thenReturn(amount);
        when(dividend.getFx()).thenReturn(fx);
        return dividend;
    }

    @Test
    @DisplayName("R-016: 국내주식 배당(세후 순액)은 15.4% 원천징수율로 세전 역환산해 합산한다")
    void calculateDividendIncome_grossesUpDomesticDividend() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(mock(Account.class)));
        given(transactionRepository.findAllByAsset_AccountIdAndAsset_CategoryAndTypeAndTradeDateBetween(
                any(), any(), any(), any(), any())).willReturn(Collections.emptyList());

        // 세후 84,600원 저장 → 세전 100,000원으로 역환산되어야 함 (84,600 / (1 - 0.154))
        Dividend domestic = dividendOf(AssetCategory.DOMESTIC_STOCK, new BigDecimal("84600"), null);
        given(dividendRepository.findAllByAsset_AccountIdAndPayDateBetween(any(), any(), any()))
                .willReturn(List.of(domestic));

        TaxSummaryResponse result = taxService.getTax(USER_ID, ACCOUNT_ID, 2026);

        assertThat(result.dividendIncome().totalDividendKrw()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(result.dividendIncome().dividendGrossedUp()).isTrue();
    }

    @Test
    @DisplayName("해외주식 배당은 세전 환산 없이 fx만 곱해 원화로 합산한다")
    void calculateDividendIncome_foreignDividendNotGrossedUp() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(mock(Account.class)));
        given(transactionRepository.findAllByAsset_AccountIdAndAsset_CategoryAndTypeAndTradeDateBetween(
                any(), any(), any(), any(), any())).willReturn(Collections.emptyList());

        Dividend foreign = dividendOf(AssetCategory.FOREIGN_STOCK, new BigDecimal("100"), new BigDecimal("1300"));
        given(dividendRepository.findAllByAsset_AccountIdAndPayDateBetween(any(), any(), any()))
                .willReturn(List.of(foreign));

        TaxSummaryResponse result = taxService.getTax(USER_ID, ACCOUNT_ID, 2026);

        assertThat(result.dividendIncome().totalDividendKrw()).isEqualByComparingTo(new BigDecimal("130000"));
    }

    @Test
    @DisplayName("배당 합계(세전 환산 후)가 2천만원을 초과하면 종합신고가능으로 판정한다")
    void calculateDividendIncome_exceedsThreshold() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(mock(Account.class)));
        given(transactionRepository.findAllByAsset_AccountIdAndAsset_CategoryAndTypeAndTradeDateBetween(
                any(), any(), any(), any(), any())).willReturn(Collections.emptyList());

        // 세후 20,000,000원 저장 → 세전 약 23,640,900원으로 역환산되어 기준(2천만원) 초과
        Dividend domestic = dividendOf(AssetCategory.DOMESTIC_STOCK, new BigDecimal("20000000"), null);
        given(dividendRepository.findAllByAsset_AccountIdAndPayDateBetween(any(), any(), any()))
                .willReturn(List.of(domestic));

        TaxSummaryResponse result = taxService.getTax(USER_ID, ACCOUNT_ID, 2026);

        assertThat(result.dividendIncome().exceedsThreshold()).isTrue();
        assertThat(result.dividendIncome().judgement()).isEqualTo(DividendTaxJudgement.COMPREHENSIVE_FILING_POSSIBLE);
    }

    @Test
    @DisplayName("소유하지 않은 계좌 조회 시 NotFoundException")
    void getTax_accountNotOwned() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> taxService.getTax(USER_ID, ACCOUNT_ID, 2026))
                .isInstanceOf(NotFoundException.class);
    }
}
