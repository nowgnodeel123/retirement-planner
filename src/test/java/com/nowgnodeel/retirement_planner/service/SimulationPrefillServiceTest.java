package com.nowgnodeel.retirement_planner.service;

import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.entity.AccountDetailType;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import com.nowgnodeel.retirement_planner.dto.SimulationPrefillResponseDto;
import com.nowgnodeel.retirement_planner.user.entity.User;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.nowgnodeel.retirement_planner.asset.dto.AssetDtos.HoldingResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulationPrefillServiceTest {

    @Mock AssetService assetService;
    @Mock AccountRepository accountRepository;
    @Mock UserRepository userRepository;
    @InjectMocks SimulationPrefillService simulationPrefillService;

    private static final Long USER_ID = 1L;
    private static final Long NORMAL_ACC = 10L;
    private static final Long IRP_ACC = 20L;
    private static final Long PENSION_ACC = 30L;

    private Account accountOf(Long id, AccountDetailType detailType) {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(id);
        when(account.getDetailType()).thenReturn(detailType);
        return account;
    }

    /** 원화 자산: evaluationAmount만 채워지면 합산 대상이 된다. */
    private HoldingResponse krwHolding(Long accountId, AssetCategory category, String evalKrw) {
        return new HoldingResponse(
                1L, accountId, "SYM", "종목", category.name(), "KRW",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal(evalKrw), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, 0);
    }

    /** 시세(또는 환율) 미조회 자산: 평가금액이 없어 합계에서 빠져야 한다. */
    private HoldingResponse unpricedHolding(Long accountId) {
        return new HoldingResponse(
                2L, accountId, "SYM", "종목", AssetCategory.DOMESTIC_STOCK.name(), "KRW",
                BigDecimal.ONE, BigDecimal.ONE, null,
                null, null, null, null, null, null, 0);
    }

    private void givenAccounts(Account... accounts) {
        given(accountRepository.findAllByUserId(USER_ID)).willReturn(List.of(accounts));
    }

    private void givenHoldings(HoldingResponse... holdings) {
        given(assetService.findAllHoldingsByUser(USER_ID)).willReturn(List.of(holdings));
    }

    @Test
    @DisplayName("D-218: 계좌 유형(IRP/연금저축/일반)별로 각 위저드 필드에 나눠 담고 만원 단위로 환산한다")
    void getPrefill_mapsByAccountDetailType() {
        givenAccounts(
                accountOf(NORMAL_ACC, AccountDetailType.NORMAL),
                accountOf(IRP_ACC, AccountDetailType.IRP),
                accountOf(PENSION_ACC, AccountDetailType.PENSION_SAVINGS));
        givenHoldings(
                krwHolding(NORMAL_ACC, AssetCategory.DOMESTIC_STOCK, "76500000"),
                krwHolding(IRP_ACC, AssetCategory.DOMESTIC_STOCK, "32400000"),
                krwHolding(PENSION_ACC, AssetCategory.DOMESTIC_STOCK, "11800000"));

        SimulationPrefillResponseDto result = simulationPrefillService.getPrefill(USER_ID);

        assertThat(result.stockAssetBalance()).isEqualTo(7650L);
        assertThat(result.currentIrpBalance()).isEqualTo(3240L);
        assertThat(result.currentPensionSavingsBalance()).isEqualTo(1180L);
        assertThat(result.excludedCount()).isZero();
    }

    @Test
    @DisplayName("D-218: 일반계좌의 코인은 주식 잔액에 포함하고, 현금은 제외한 뒤 금액을 따로 알려준다")
    void getPrefill_includesCryptoButExcludesCash() {
        givenAccounts(accountOf(NORMAL_ACC, AccountDetailType.NORMAL));
        givenHoldings(
                krwHolding(NORMAL_ACC, AssetCategory.DOMESTIC_STOCK, "50000000"),
                krwHolding(NORMAL_ACC, AssetCategory.CRYPTO, "8000000"),
                krwHolding(NORMAL_ACC, AssetCategory.CASH, "13500000"));

        SimulationPrefillResponseDto result = simulationPrefillService.getPrefill(USER_ID);

        // 주식 5,000 + 코인 800 = 5,800만원. 현금 1,350만원은 빠지고 별도 필드로 노출.
        assertThat(result.stockAssetBalance()).isEqualTo(5800L);
        assertThat(result.excludedCashAmount()).isEqualTo(1350L);
    }

    @Test
    @DisplayName("D-218: IRP/연금저축 계좌의 현금은 '계좌 잔액'이므로 제외하지 않고 합산한다")
    void getPrefill_keepsCashInsidePensionAccounts() {
        givenAccounts(accountOf(IRP_ACC, AccountDetailType.IRP));
        givenHoldings(
                krwHolding(IRP_ACC, AssetCategory.DOMESTIC_STOCK, "20000000"),
                krwHolding(IRP_ACC, AssetCategory.CASH, "5000000"));

        SimulationPrefillResponseDto result = simulationPrefillService.getPrefill(USER_ID);

        assertThat(result.currentIrpBalance()).isEqualTo(2500L);
        assertThat(result.excludedCashAmount()).isZero();
    }

    @Test
    @DisplayName("D-218: 시세 미조회 자산은 합계에서 빼되 excludedCount로 반드시 드러낸다(조용한 과소계산 방지)")
    void getPrefill_reportsExcludedHoldings() {
        givenAccounts(accountOf(NORMAL_ACC, AccountDetailType.NORMAL));
        givenHoldings(
                krwHolding(NORMAL_ACC, AssetCategory.DOMESTIC_STOCK, "50000000"),
                unpricedHolding(NORMAL_ACC),
                unpricedHolding(NORMAL_ACC));

        SimulationPrefillResponseDto result = simulationPrefillService.getPrefill(USER_ID);

        assertThat(result.stockAssetBalance()).isEqualTo(5000L);
        assertThat(result.excludedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("D-218: 해외자산은 환율이 있어야 원화환산해 합산하고, 없으면 제외한다(대시보드와 동일 규칙)")
    void getPrefill_appliesSameFxRuleAsDashboard() {
        givenAccounts(accountOf(NORMAL_ACC, AccountDetailType.NORMAL));
        HoldingResponse withFx = new HoldingResponse(
                3L, NORMAL_ACC, "AAPL", "애플", AssetCategory.FOREIGN_STOCK.name(), "USD",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1300"), new BigDecimal("13000000"), "2026-08-30", 0);
        HoldingResponse withoutFx = new HoldingResponse(
                4L, NORMAL_ACC, "MSFT", "마이크로소프트", AssetCategory.FOREIGN_STOCK.name(), "USD",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, 0);
        givenHoldings(withFx, withoutFx);

        SimulationPrefillResponseDto result = simulationPrefillService.getPrefill(USER_ID);

        assertThat(result.stockAssetBalance()).isEqualTo(1300L);
        assertThat(result.excludedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("D-218: 생년월일이 있으면 만 나이를 채운다")
    void getPrefill_fillsAgeFromBirthDate() {
        givenAccounts();
        givenHoldings();
        User user = mock(User.class);
        when(user.getBirthDate()).thenReturn(LocalDate.now().minusYears(34).minusDays(1));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThat(simulationPrefillService.getPrefill(USER_ID).currentAge()).isEqualTo(34);
    }

    @Test
    @DisplayName("D-218: 시뮬레이터가 받는 나이 범위(20~74) 밖이면 채우지 않는다 — 제출이 400으로 튕기는 것보다 빈칸이 낫다")
    void getPrefill_skipsAgeOutsideAcceptedRange() {
        givenAccounts();
        givenHoldings();
        User user = mock(User.class);
        when(user.getBirthDate()).thenReturn(LocalDate.now().minusYears(80));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThat(simulationPrefillService.getPrefill(USER_ID).currentAge()).isNull();
    }

    @Test
    @DisplayName("D-218: 포트폴리오가 비어 있어도 0으로 정상 응답한다(위저드는 그대로 수기 입력)")
    void getPrefill_emptyPortfolio() {
        givenAccounts();
        givenHoldings();
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        SimulationPrefillResponseDto result = simulationPrefillService.getPrefill(USER_ID);

        assertThat(result.currentAge()).isNull();
        assertThat(result.stockAssetBalance()).isZero();
        assertThat(result.currentIrpBalance()).isZero();
        assertThat(result.currentPensionSavingsBalance()).isZero();
        assertThat(result.excludedCount()).isZero();
        assertThat(result.excludedCashAmount()).isZero();
    }
}
