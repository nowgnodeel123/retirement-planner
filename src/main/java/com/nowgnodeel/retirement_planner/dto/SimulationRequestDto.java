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

    @NotNull @Min(0)
    private Double monthlyIrpContribution;

    @NotNull @Min(0)
    private Double monthlyPensionSavingsContribution;

    @NotNull @Min(0)
    private Double targetMonthlyExpense;

    private Double irpReturnRate = 0.05;
    private Double pensionReturnRate = 0.04;
    private Double pensionSavingsReturnRate = 0.06;
}