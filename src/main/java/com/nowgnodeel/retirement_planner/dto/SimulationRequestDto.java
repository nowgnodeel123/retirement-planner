package com.nowgnodeel.retirement_planner.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class SimulationRequestDto {

    // WHY @Max(74): 최소 은퇴나이 탐색이 currentAge+1 ~ 75세 범위라,
    // 75세 이상 입력 시 탐색 루프가 한 번도 돌지 않아 NPE가 발생한다. (검토 A-1)
    @NotNull @Min(20) @Max(74)
    private Integer currentAge;

    @NotNull @Min(0)
    private Double monthlyIncome;

    @NotNull @Min(0)
    private Integer pensionYearsPaid;

    private String pensionType = "DC";

    // DB형 전용: 입사 후 지금까지 이미 쌓인 근속연수.
    // WHY: 퇴직금 = 최종월급 × (과거 근속 + 미래 근속)이므로 과거분이 빠지면 과소 계산된다.
    @Min(0)
    private Integer yearsOfService = 0;

    // DC형 전용: 현재까지 적립된 퇴직연금 잔액(만원).
    // WHY: 기존 잔액도 은퇴 시점까지 수익률로 계속 굴러가므로 계산에 반드시 포함해야 한다.
    @Min(0)
    private Double dcCurrentBalance = 0.0;

    private String nationalPensionReceiptType = "NORMAL";
    private Integer nationalPensionReceiptAge;

    private Integer militaryServiceMonths = 0;
    private Integer childrenCount = 0;

    @NotNull @Min(0)
    private Double monthlyIrpContribution;

    private Double currentIrpBalance = 0.0;

    @NotNull @Min(0)
    private Double monthlyPensionSavingsContribution;

    private Double currentPensionSavingsBalance = 0.0;

    @NotNull @Min(0)
    private Double targetMonthlyExpense;

    // WHY 수익률 상한(연 100%): 프론트는 자릿수 캡으로 막지만 API 직접 호출은
    // 못 막는다. 비현실적 수익률로 오버플로우에 가까운 결과가 나오는 것을 방지. (검토 권고)
    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double irpReturnRate = 0.05;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double pensionReturnRate = 0.04;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double pensionSavingsReturnRate = 0.06;

    private Double stockAssetBalance = 0.0;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double stockReturnRate = 0.07;

    private Double monthlyStockInvestment = 0.0;

    private boolean usePreciseHealthInsurance = false;
    private Double realEstateValue = 0.0;
    private Double financialAssetValue = 0.0;
}