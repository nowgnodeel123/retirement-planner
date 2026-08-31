package com.nowgnodeel.retirement_planner.entity;

import com.nowgnodeel.retirement_planner.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * D-219: 은퇴 시뮬레이터에 사용자가 직접 입력한 값. 포트폴리오 메인의
 * "은퇴 가능 나이" 카드가 위저드를 다시 열지 않고 계산할 수 있게 한다.
 *
 * 저장하지 않는 것(V16 주석 참조): 계산 결과 일체. 컬럼 자체가 없다 — 캐싱하고 싶은
 * 압력이 생겨도 넣을 자리를 두지 않는 게 D-050을 지키는 가장 확실한 방법이라 판단.
 *
 * *_manual 잔액은 포트폴리오 프리필이 0일 때만 쓰이는 대체값이다. 즉 앱에 등록된
 * 계좌의 단일 소스는 여전히 transactions이고, 이 값은 "등록하지 않은 계좌"를 위저드에
 * 적어둔 것의 보관소다.
 */
@Entity
@Table(name = "retirement_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetirementProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // 나이는 시간이 지나면 낡으므로 기록 시점을 함께 남긴다(ageToday() 참조).
    @Column(name = "current_age", nullable = false)
    private Integer currentAge;

    @Column(name = "age_as_of", nullable = false)
    private LocalDate ageAsOf;

    @Column(name = "monthly_income", nullable = false)
    private Double monthlyIncome;

    @Column(name = "target_monthly_expense", nullable = false)
    private Double targetMonthlyExpense;

    @Column(name = "pension_years_paid", nullable = false)
    private Integer pensionYearsPaid;

    @Column(name = "pension_type", nullable = false, length = 10)
    private String pensionType;

    @Column(name = "years_of_service", nullable = false)
    private Integer yearsOfService;

    @Column(name = "national_pension_receipt_type", nullable = false, length = 20)
    private String nationalPensionReceiptType;

    @Column(name = "national_pension_receipt_age")
    private Integer nationalPensionReceiptAge;

    @Column(name = "military_service_months", nullable = false)
    private Integer militaryServiceMonths;

    @Column(name = "children_count", nullable = false)
    private Integer childrenCount;

    @Column(name = "monthly_irp_contribution", nullable = false)
    private Double monthlyIrpContribution;

    @Column(name = "monthly_pension_savings_contribution", nullable = false)
    private Double monthlyPensionSavingsContribution;

    @Column(name = "monthly_stock_investment", nullable = false)
    private Double monthlyStockInvestment;

    @Column(name = "irp_return_rate", nullable = false)
    private Double irpReturnRate;

    @Column(name = "pension_return_rate", nullable = false)
    private Double pensionReturnRate;

    @Column(name = "pension_savings_return_rate", nullable = false)
    private Double pensionSavingsReturnRate;

    @Column(name = "stock_return_rate", nullable = false)
    private Double stockReturnRate;

    // DC 퇴직연금은 포트폴리오에 대응하는 계좌 유형이 없어 프리필 소스가 아예 없다.
    // 나머지 셋과 달리 항상 이 값이 쓰인다.
    @Column(name = "dc_current_balance_manual", nullable = false)
    private Double dcCurrentBalanceManual;

    @Column(name = "irp_balance_manual", nullable = false)
    private Double irpBalanceManual;

    @Column(name = "pension_savings_balance_manual", nullable = false)
    private Double pensionSavingsBalanceManual;

    @Column(name = "stock_asset_balance_manual", nullable = false)
    private Double stockAssetBalanceManual;

    @Column(name = "use_precise_health_insurance", nullable = false)
    private boolean usePreciseHealthInsurance;

    @Column(name = "real_estate_value", nullable = false)
    private Double realEstateValue;

    @Column(name = "financial_asset_value", nullable = false)
    private Double financialAssetValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private RetirementProfile(User user, Integer currentAge, LocalDate ageAsOf, Double monthlyIncome,
                              Double targetMonthlyExpense, Integer pensionYearsPaid, String pensionType,
                              Integer yearsOfService, String nationalPensionReceiptType,
                              Integer nationalPensionReceiptAge, Integer militaryServiceMonths,
                              Integer childrenCount, Double monthlyIrpContribution,
                              Double monthlyPensionSavingsContribution, Double monthlyStockInvestment,
                              Double irpReturnRate, Double pensionReturnRate, Double pensionSavingsReturnRate,
                              Double stockReturnRate, Double dcCurrentBalanceManual, Double irpBalanceManual,
                              Double pensionSavingsBalanceManual, Double stockAssetBalanceManual,
                              boolean usePreciseHealthInsurance, Double realEstateValue,
                              Double financialAssetValue) {
        this.user = user;
        apply(currentAge, ageAsOf, monthlyIncome, targetMonthlyExpense, pensionYearsPaid, pensionType,
                yearsOfService, nationalPensionReceiptType, nationalPensionReceiptAge, militaryServiceMonths,
                childrenCount, monthlyIrpContribution, monthlyPensionSavingsContribution, monthlyStockInvestment,
                irpReturnRate, pensionReturnRate, pensionSavingsReturnRate, stockReturnRate,
                dcCurrentBalanceManual, irpBalanceManual, pensionSavingsBalanceManual, stockAssetBalanceManual,
                usePreciseHealthInsurance, realEstateValue, financialAssetValue);
    }

    /** 시뮬레이션을 다시 돌릴 때마다 최신 입력으로 덮어쓴다(사용자당 1행). */
    public void update(RetirementProfile source) {
        apply(source.currentAge, source.ageAsOf, source.monthlyIncome, source.targetMonthlyExpense,
                source.pensionYearsPaid, source.pensionType, source.yearsOfService,
                source.nationalPensionReceiptType, source.nationalPensionReceiptAge,
                source.militaryServiceMonths, source.childrenCount, source.monthlyIrpContribution,
                source.monthlyPensionSavingsContribution, source.monthlyStockInvestment,
                source.irpReturnRate, source.pensionReturnRate, source.pensionSavingsReturnRate,
                source.stockReturnRate, source.dcCurrentBalanceManual, source.irpBalanceManual,
                source.pensionSavingsBalanceManual, source.stockAssetBalanceManual,
                source.usePreciseHealthInsurance, source.realEstateValue, source.financialAssetValue);
    }

    private void apply(Integer currentAge, LocalDate ageAsOf, Double monthlyIncome, Double targetMonthlyExpense,
                       Integer pensionYearsPaid, String pensionType, Integer yearsOfService,
                       String nationalPensionReceiptType, Integer nationalPensionReceiptAge,
                       Integer militaryServiceMonths, Integer childrenCount, Double monthlyIrpContribution,
                       Double monthlyPensionSavingsContribution, Double monthlyStockInvestment,
                       Double irpReturnRate, Double pensionReturnRate, Double pensionSavingsReturnRate,
                       Double stockReturnRate, Double dcCurrentBalanceManual, Double irpBalanceManual,
                       Double pensionSavingsBalanceManual, Double stockAssetBalanceManual,
                       boolean usePreciseHealthInsurance, Double realEstateValue, Double financialAssetValue) {
        this.currentAge = currentAge;
        this.ageAsOf = ageAsOf;
        this.monthlyIncome = monthlyIncome;
        this.targetMonthlyExpense = targetMonthlyExpense;
        this.pensionYearsPaid = pensionYearsPaid;
        this.pensionType = pensionType;
        this.yearsOfService = yearsOfService;
        this.nationalPensionReceiptType = nationalPensionReceiptType;
        this.nationalPensionReceiptAge = nationalPensionReceiptAge;
        this.militaryServiceMonths = militaryServiceMonths;
        this.childrenCount = childrenCount;
        this.monthlyIrpContribution = monthlyIrpContribution;
        this.monthlyPensionSavingsContribution = monthlyPensionSavingsContribution;
        this.monthlyStockInvestment = monthlyStockInvestment;
        this.irpReturnRate = irpReturnRate;
        this.pensionReturnRate = pensionReturnRate;
        this.pensionSavingsReturnRate = pensionSavingsReturnRate;
        this.stockReturnRate = stockReturnRate;
        this.dcCurrentBalanceManual = dcCurrentBalanceManual;
        this.irpBalanceManual = irpBalanceManual;
        this.pensionSavingsBalanceManual = pensionSavingsBalanceManual;
        this.stockAssetBalanceManual = stockAssetBalanceManual;
        this.usePreciseHealthInsurance = usePreciseHealthInsurance;
        this.realEstateValue = realEstateValue;
        this.financialAssetValue = financialAssetValue;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 기록 시점 이후 흐른 햇수를 더한 오늘 기준 나이.
     * 생년월일이 아니라 나이를 받았으므로 최대 1년 오차가 있다 — 정확히 하려면
     * 생년월일 입력 수단이 필요하고, 카카오 계정은 그게 카카오 쪽에 있다(별도 과제).
     */
    public int ageToday(LocalDate today) {
        return currentAge + (int) java.time.temporal.ChronoUnit.YEARS.between(ageAsOf, today);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
