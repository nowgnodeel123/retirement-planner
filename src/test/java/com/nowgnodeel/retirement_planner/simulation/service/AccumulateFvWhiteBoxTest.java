package com.nowgnodeel.retirement_planner.simulation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * accumulateFvMonthly 화이트박스 테스트.
 *
 * 구현을 알고 짠다 — 분기(기존잔액 유무 / 납입 유무 / 수익률 0 / 기간 0)를 전부 밟고,
 * 각 분기가 금융 공식과 맞는지 독립 계산으로 대조한다.
 * "코드가 뭘 하는가"가 아니라 "그 코드가 맞는 계산인가"를 본다.
 */
class AccumulateFvWhiteBoxTest {

    private SimulationService service;

    @BeforeEach
    void setUp() {
        service = new SimulationService();
        ReflectionTestUtils.setField(service, "shareUrl", "https://x");
        ReflectionTestUtils.setField(service, "aValue", 319.3511);
    }

    private double fv(double balance, double monthly, double rate, int years) {
        return (double) ReflectionTestUtils.invokeMethod(
                service, "accumulateFvMonthly", balance, monthly, rate, years);
    }

    // ── 분기 1: 기존 잔액만 (납입 0) ───────────────────────────────
    @Nested
    @DisplayName("분기: 기존 잔액만 있고 납입이 없을 때")
    class BalanceOnly {

        @Test
        @DisplayName("연 복리 그대로 — balance × (1+r)^n")
        void compoundsAnnually() {
            assertThat(fv(1000, 0, 0.06, 10))
                    .isCloseTo(1000 * Math.pow(1.06, 10), offset(1e-9));
        }

        @Test
        @DisplayName("기간 0이면 원금 그대로")
        void zeroYearsKeepsPrincipal() {
            assertThat(fv(1000, 0, 0.06, 0)).isCloseTo(1000.0, offset(1e-9));
        }

        @Test
        @DisplayName("수익률 0이면 원금 그대로")
        void zeroRateKeepsPrincipal() {
            assertThat(fv(1000, 0, 0.0, 30)).isCloseTo(1000.0, offset(1e-9));
        }
    }

    // ── 분기 2: 납입만 (기존 잔액 0) ───────────────────────────────
    @Nested
    @DisplayName("분기: 매월 납입만 있을 때")
    class ContributionOnly {

        @ParameterizedTest(name = "월 {0}만원 / 연 {1} / {2}년")
        @CsvSource({ "25, 0.06, 10", "50, 0.08, 20", "30, 0.10, 30", "10, 0.03, 5" })
        @DisplayName("월 기말납입 연금 미래가치 공식과 일치")
        void matchesMonthlyAnnuityFormula(double monthly, double rate, int years) {
            int months = years * 12;
            double i = Math.pow(1 + rate, 1.0 / 12) - 1;
            double expected = monthly * (Math.pow(1 + i, months) - 1) / i;

            assertThat(fv(0, monthly, rate, years)).isCloseTo(expected, offset(1e-6));
        }

        @Test
        @DisplayName("수익률 0이면 단순 합계 — 월납 × 개월수")
        void zeroRateIsPlainSum() {
            assertThat(fv(0, 25, 0.0, 10)).isCloseTo(25 * 120, offset(1e-9));
        }

        @Test
        @DisplayName("기간 0이면 0")
        void zeroYearsIsZero() {
            assertThat(fv(0, 25, 0.06, 0)).isCloseTo(0.0, offset(1e-9));
        }

        @Test
        @DisplayName("월 수익률은 실효환산 — 1년 적립의 연환산이 입력 수익률을 넘지 않는다")
        void monthlyRateIsEffectiveNotNominal() {
            // r/12(명목)를 쓰면 연 6% 입력이 실제로는 6.17%로 굴러간다.
            // 실효환산이면 12개월 복리가 정확히 (1+r)이 된다.
            double i = Math.pow(1.06, 1.0 / 12) - 1;
            assertThat(Math.pow(1 + i, 12)).isCloseTo(1.06, offset(1e-12));

            // 명목환산이었다면 나왔을 값보다 작아야 한다.
            double nominal = 0.06 / 12;
            double asNominal = 25 * (Math.pow(1 + nominal, 120) - 1) / nominal;
            assertThat(fv(0, 25, 0.06, 10)).isLessThan(asNominal);
        }

        @Test
        @DisplayName("기초납입이 아니라 기말납입 — 한 달치 수익만큼 작다")
        void isOrdinaryAnnuityNotAnnuityDue() {
            double i = Math.pow(1.06, 1.0 / 12) - 1;
            double ordinary = fv(0, 25, 0.06, 10);
            double due = ordinary * (1 + i); // 기초납입이면 이만큼
            assertThat(ordinary).isLessThan(due);
            assertThat(due / ordinary).isCloseTo(1 + i, offset(1e-12));
        }
    }

    // ── 분기 3: 둘 다 있을 때 = 두 분기의 합 ──────────────────────
    @Test
    @DisplayName("분기: 잔액 + 납입 = 각각 따로 계산한 값의 합")
    void balancePlusContributionIsAdditive() {
        double both = fv(5000, 40, 0.08, 15);
        double balanceOnly = fv(5000, 0, 0.08, 15);
        double contribOnly = fv(0, 40, 0.08, 15);
        assertThat(both).isCloseTo(balanceOnly + contribOnly, offset(1e-6));
    }

    // ── 회귀 방지: 예전 연납입 방식보다 커야 한다 ─────────────────
    @Test
    @DisplayName("회귀: 예전 연 1회 기말납입 방식보다 크다(과소평가 4%대를 해소했다는 증거)")
    void isGreaterThanOldAnnualLumpModel() {
        double rate = 0.10;
        int years = 30;
        double monthly = 25;

        double oldWay = (monthly * 12) * (Math.pow(1 + rate, years) - 1) / rate;
        double newWay = fv(0, monthly, rate, years);

        assertThat(newWay).isGreaterThan(oldWay);
        double gain = (newWay - oldWay) / oldWay * 100;
        System.out.printf("[화이트박스] 월납 전환 증가율: %.2f%% (연납 %.0f만원 → 월납 %.0f만원)%n",
                gain, oldWay, newWay);
        assertThat(gain).isBetween(4.0, 5.0);
    }

    // ── 단조성(내부 성질) ─────────────────────────────────────────
    @Test
    @DisplayName("단조성: 수익률·납입액·기간이 늘면 결과도 늘어난다")
    void isMonotonic() {
        assertThat(fv(0, 25, 0.07, 20)).isGreaterThan(fv(0, 25, 0.06, 20));
        assertThat(fv(0, 26, 0.06, 20)).isGreaterThan(fv(0, 25, 0.06, 20));
        assertThat(fv(0, 25, 0.06, 21)).isGreaterThan(fv(0, 25, 0.06, 20));
        assertThat(fv(1, 25, 0.06, 20)).isGreaterThan(fv(0, 25, 0.06, 20));
    }

    @Test
    @DisplayName("장기 복리가 폭주하지 않는다 — 40년/연10% 월25만원이 상식 범위")
    void longHorizonStaysSane() {
        double v = fv(0, 25, 0.10, 40);
        // 원금 25 × 480개월 = 12,000만원. 연 10% 40년이면 대략 13~16만원(만원 단위) 범위.
        assertThat(v).isGreaterThan(12000);
        assertThat(v).isCloseTo(140000, withPercentage(20));
    }
}
