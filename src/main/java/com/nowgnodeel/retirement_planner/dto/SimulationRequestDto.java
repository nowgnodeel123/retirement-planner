package com.nowgnodeel.retirement_planner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class SimulationRequestDto {

    @NotNull @Min(20)
    private Integer currentAge;

    @NotNull @Min(40)
    private Integer retirementAge;

    @NotNull @Min(0)
    private Double monthlyIncome;

    @NotNull @Min(0)
    private Integer pensionYearsPaid;

    private String pensionType = "DC";

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

    private Double irpReturnRate = 0.05;
    private Double pensionReturnRate = 0.04;
    private Double pensionSavingsReturnRate = 0.06;

    private Double stockAssetBalance = 0.0;
    private Double stockReturnRate = 0.07;
    private Double monthlyStockInvestment = 0.0;

    private Double depositBalance = 0.0;
    private Double depositReturnRate = 0.03;
    private Double monthlyDepositInvestment = 0.0;

    // ── 건보료 정밀 계산용 (전문가 모드, 선택 입력) ──
    private boolean usePreciseHealthInsurance = false;
    private Double realEstateValue = 0.0;     // 부동산 가액 (만원)
    private Double financialAssetValue = 0.0; // 금융재산 (만원, ISA/IRP/연금저축 제외)
}