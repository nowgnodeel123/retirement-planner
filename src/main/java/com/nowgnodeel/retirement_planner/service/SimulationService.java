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
        long shortfall = totalMonthlyIncome - target;

        int estimatedRetirementAge = calculateEstimatedRetirementAge(req);
        String message = generateMessage(shortfall, req);
        String shareMessage = estimatedRetirementAge + "세에 은퇴할 수 있대. 너는? → retirement-planner.vercel.app";

        return SimulationResponseDto.builder()
                .summary(SimulationResponseDto.Summary.builder()
                        .totalMonthlyIncome(totalMonthlyIncome)
                        .targetMonthlyExpense(target)
                        .monthlyShortfall(shortfall)
                        .estimatedRetirementAge(estimatedRetirementAge)
                        .message(message)
                        .shareMessage(shareMessage)
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

    private int calculateEstimatedRetirementAge(SimulationRequestDto req) {
        for (int age = req.getCurrentAge() + 1; age <= 75; age++) {
            int years = age - req.getCurrentAge();
            int pensionYears = req.getPensionYearsPaid() + years;

            long np = calculateNationalPension(pensionYears, req.getMonthlyIncome());
            long rp = calculateRetirementPension(years, req.getMonthlyIncome(), req.getPensionReturnRate());
            long irp = calculateIrp(years, req.getMonthlyIrpContribution(), req.getIrpReturnRate());

            if (np + rp + irp >= req.getTargetMonthlyExpense()) {
                return age;
            }
        }
        return 75;
    }

    private String generateMessage(long shortfall, SimulationRequestDto req) {
        if (shortfall >= 0) {
            return "목표 달성! 지금 페이스라면 " + req.getRetirementAge() + "세에 은퇴할 수 있습니다. 🎉";
        }
        long neededIrp = req.getMonthlyIrpContribution().longValue() + Math.abs(shortfall);
        return "IRP 납입액을 월 " + req.getMonthlyIrpContribution().longValue() + "만원 → "
                + neededIrp + "만원으로 늘리면 목표를 달성할 수 있습니다.";
    }

    private long calculateNationalPension(int totalYears, double monthlyIncome) {
        if (totalYears < 10) return 0;
        return Math.round(0.1 * (A_VALUE + monthlyIncome) * (1 + 0.05 * (totalYears - 20)));
    }

    private long calculateRetirementPension(int years, double monthlyIncome, double rate) {
        double fv = monthlyIncome * (Math.pow(1 + rate, years) - 1) / rate;
        return Math.round(fv / (PAYOUT_YEARS * 12));
    }

    private long calculateIrp(int years, double monthlyContribution, double rate) {
        double fv = (monthlyContribution * 12) * (Math.pow(1 + rate, years) - 1) / rate;
        return Math.round(fv / (PAYOUT_YEARS * 12));
    }
}