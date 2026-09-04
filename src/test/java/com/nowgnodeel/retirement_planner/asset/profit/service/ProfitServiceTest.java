package com.nowgnodeel.retirement_planner.asset.profit.service;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.entity.Asset;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;
import com.nowgnodeel.retirement_planner.asset.entity.TransactionType;
import com.nowgnodeel.retirement_planner.asset.repository.TransactionRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.profit.dto.ProfitDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D-237: 기간 경계와 전체 건수. 이 두 값이 없으면 "이번 달"이 하루뿐인 매월 1일 같은 날
 * 며칠 전에 판 종목이 목록에서 조용히 빠져, 사용자에게는 계산 오류로 보인다(실제 제보).
 */
@ExtendWith(MockitoExtension.class)
class ProfitServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock DividendRepository dividendRepository;
    @Mock AssetService assetService;
    @InjectMocks ProfitService profitService;

    private static final Long USER_ID = 1L;

    private Transaction sellOf(BigDecimal profit, LocalDate date) {
        Asset asset = mock(Asset.class);
        when(asset.getId()).thenReturn(9L);
        when(asset.getName()).thenReturn("Apple");
        when(asset.getCategory()).thenReturn(AssetCategory.FOREIGN_STOCK);
        // 인별 집계라 항목마다 어느 계좌인지 함께 내려준다(같은 종목이 여러 계좌에 있을 수 있다).
        Account account = mock(Account.class);
        when(account.getName()).thenReturn("키움 위탁");
        when(asset.getAccount()).thenReturn(account);
        Transaction tx = mock(Transaction.class);
        when(tx.getId()).thenReturn(1L);
        when(tx.getAsset()).thenReturn(asset);
        when(tx.getTradeDate()).thenReturn(date);
        given(assetService.calculateRealizedProfitKrw(tx)).willReturn(profit);
        return tx;
    }

    private void noDividends() {
        given(dividendRepository.findAllByUserInPeriod(any(), any(), any()))
                .willReturn(Collections.<Dividend>emptyList());
        given(dividendRepository.countByAsset_Account_User_Id(USER_ID)).willReturn(0L);
    }

    @Test
    @DisplayName("월 기간의 경계는 이번 달 1일 ~ 오늘로 내려간다")
    void monthRangeIsReported() {
        given(transactionRepository.findAllByUserAndTypeInPeriod(any(), any(), any(), any()))
                .willReturn(Collections.<Transaction>emptyList());
        given(transactionRepository.countByAsset_Account_User_IdAndType(USER_ID, TransactionType.SELL))
                .willReturn(0L);
        noDividends();

        ProfitSummaryResponse r = profitService.getProfitForUser(USER_ID, ProfitPeriod.MONTH, null);

        LocalDate today = LocalDate.now();
        assertThat(r.rangeStart()).isEqualTo(today.withDayOfMonth(1));
        assertThat(r.rangeEnd()).isEqualTo(today);
    }

    @Test
    @DisplayName("주 기간의 경계는 이번 주 월요일 ~ 오늘")
    void weekRangeIsReported() {
        given(transactionRepository.findAllByUserAndTypeInPeriod(any(), any(), any(), any()))
                .willReturn(Collections.<Transaction>emptyList());
        given(transactionRepository.countByAsset_Account_User_IdAndType(USER_ID, TransactionType.SELL))
                .willReturn(0L);
        noDividends();

        ProfitSummaryResponse r = profitService.getProfitForUser(USER_ID, ProfitPeriod.WEEK, null);

        LocalDate today = LocalDate.now();
        assertThat(r.rangeStart()).isEqualTo(today.with(DayOfWeek.MONDAY));
        assertThat(r.rangeEnd()).isEqualTo(today);
    }

    @Test
    @DisplayName("전체 기간이면 경계는 null — 화면이 기간 안내를 띄우지 않는 신호")
    void allPeriodHasNoRange() {
        given(transactionRepository.findAllByUserAndTypeInPeriod(any(), any(), any(), any()))
                .willReturn(Collections.<Transaction>emptyList());
        given(transactionRepository.countByAsset_Account_User_IdAndType(USER_ID, TransactionType.SELL))
                .willReturn(0L);
        noDividends();

        ProfitSummaryResponse r = profitService.getProfitForUser(USER_ID, ProfitPeriod.ALL, null);

        assertThat(r.rangeStart()).isNull();
        assertThat(r.rangeEnd()).isNull();
    }

    @Test
    @DisplayName("D-237: 기간 밖 내역이 있으면 allTimeItemCount가 기간 내 건수보다 크다")
    void allTimeCountRevealsHiddenItems() {
        // 이번 달에는 1건만 잡히지만 전체로는 매도 3건 + 배당 1건 = 4건.
        // 화면은 이 차이(3건)를 "기간 밖에 더 있어요"로 안내한다.
        Transaction inRange = sellOf(new BigDecimal("100000"), LocalDate.now());
        given(transactionRepository.findAllByUserAndTypeInPeriod(any(), any(), any(), any()))
                .willReturn(List.of(inRange));
        given(transactionRepository.countByAsset_Account_User_IdAndType(USER_ID, TransactionType.SELL))
                .willReturn(3L);
        given(dividendRepository.findAllByUserInPeriod(any(), any(), any()))
                .willReturn(Collections.<Dividend>emptyList());
        given(dividendRepository.countByAsset_Account_User_Id(USER_ID)).willReturn(1L);

        ProfitSummaryResponse r = profitService.getProfitForUser(USER_ID, ProfitPeriod.MONTH, null);

        assertThat(r.items()).hasSize(1);
        assertThat(r.allTimeItemCount()).isEqualTo(4);
        assertThat(r.allTimeItemCount() - r.items().size()).isEqualTo(3);
    }

    @Test
    @DisplayName("카테고리를 지정하면 전체 건수도 같은 카테고리로 센다(다른 카테고리가 섞여 안내되면 안 됨)")
    void allTimeCountRespectsCategoryFilter() {
        given(transactionRepository.findAllByUserAndCategoryAndTypeInPeriod(any(), any(), any(), any(), any()))
                .willReturn(Collections.<Transaction>emptyList());
        given(transactionRepository.countByAsset_Account_User_IdAndAsset_CategoryAndType(
                USER_ID, AssetCategory.FOREIGN_STOCK, TransactionType.SELL)).willReturn(2L);
        given(dividendRepository.findAllByUserAndCategoryInPeriod(any(), any(), any(), any()))
                .willReturn(Collections.<Dividend>emptyList());
        given(dividendRepository.countByAsset_Account_User_IdAndAsset_Category(
                USER_ID, AssetCategory.FOREIGN_STOCK)).willReturn(0L);

        ProfitSummaryResponse r = profitService.getProfitForUser(
                USER_ID, ProfitPeriod.MONTH, AssetCategory.FOREIGN_STOCK);

        assertThat(r.allTimeItemCount()).isEqualTo(2);
    }
}
