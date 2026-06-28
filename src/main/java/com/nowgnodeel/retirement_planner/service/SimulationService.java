package com.nowgnodeel.retirement_planner.service;

import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    private static final double A_VALUE = 319.35;
    private static final int PAYOUT_YEARS = 25;

    public SimulationResponseDto calculate(SimulationRequestDto req) {
        int yearsUntilRetirement = req.getRetirementAge() - req.getCurrentAge();
        int totalPensionYears = req.getPensionYearsPaid() + yearsUntilRetirement;

        long nationalPension = calculateNationalPension(totalPensionYears, req.getMonthlyIncome());
        long retirementPension = calculateRetirementPension(yearsUntilRetirement, req.getMonthlyIncome(), req.getPensionReturnRate());
        long irp = calculateIrp(yearsUntilRetirement, req.getMonthlyIrpContribution(), req.getIrpReturnRate());

        long totalMonthlyIncome = nationalPension + retirementPension + irp;
        long target = Math.round(req.getTargetMonthlyExpense());

        return SimulationResponseDto.builder()
                .summary(SimulationResponseDto.Summary.builder()
                        .totalMonthlyIncome(totalMonthlyIncome)
                        .targetMonthlyExpense(target)
                        .monthlyShortfall(totalMonthlyIncome - target)
                        .build())
                .breakdown(SimulationResponseDto.Breakdown.builder()
                        .nationalPension(nationalPension)
                        .retirementPension(retirementPension)
                        .irp(irp)
                        .build())
                .meta(SimulationResponseDto.Meta.builder()
                        .yearsUntilRetirement(yearsUntilRetirement)
                        .totalPensionYears(totalPensionYears)
                        .build())
                .build();
    }

    private long calculateNationalPension(int totalYears, double monthlyIncome) {
        if (totalYears < 10) return 0;
        return Math.round(0.1 * (A_VALUE + monthlyIncome) * (1 + 0.05 * (totalYears - 20)));
    }

    private long calculateRetirementPension(int years, double monthlyIncome, double rate) {
        double annualContribution = monthlyIncome;
        double fv = annualContribution * (Math.pow(1 + rate, years) - 1) / rate;
        return Math.round(fv / (PAYOUT_YEARS * 12));
    }

    private long calculateIrp(int years, double monthlyContribution, double rate) {
        double annualContribution = monthlyContribution * 12;
        double fv = annualContribution * (Math.pow(1 + rate, years) - 1) / rate;
        return Math.round(fv / (PAYOUT_YEARS * 12));
    }
}