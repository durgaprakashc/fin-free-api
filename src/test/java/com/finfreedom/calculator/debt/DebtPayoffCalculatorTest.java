package com.finfreedom.calculator.debt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DebtPayoffCalculatorTest {

    private DebtPayoffCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DebtPayoffCalculator();
    }

    // --- SNOWBALL ---

    @Test
    @DisplayName("snowball: orders debts smallest-balance-first")
    void should_orderSmallestFirst_when_strategyIsSnowball() {
        List<DebtRecord> debts = List.of(
                debtRecord(1L, "Big", bd("2000"), bd("5"), bd("50")),
                debtRecord(2L, "Small", bd("500"), bd("10"), bd("25"))
        );

        var result = calculator.calculate(debts, BigDecimal.ZERO, PayoffStrategy.SNOWBALL);

        assertThat(result.debtSchedules().get(0).debtName()).isEqualTo("Small");
        assertThat(result.debtSchedules().get(1).debtName()).isEqualTo("Big");
    }

    @Test
    @DisplayName("snowball: applies extra payment to current target debt")
    void should_reduceMonths_when_extraPaymentProvidedWithSnowball() {
        List<DebtRecord> debts = List.of(
                debtRecord(1L, "D1", bd("1000"), bd("12"), bd("50"))
        );

        var withoutExtra = calculator.calculate(debts, BigDecimal.ZERO, PayoffStrategy.SNOWBALL);
        var withExtra = calculator.calculate(debts, bd("100"), PayoffStrategy.SNOWBALL);

        assertThat(withExtra.totalMonths()).isLessThan(withoutExtra.totalMonths());
    }

    @Test
    @DisplayName("snowball: total interest is positive for non-zero balances")
    void should_returnPositiveInterest_when_debtsHaveBalance() {
        List<DebtRecord> debts = List.of(
                debtRecord(1L, "Card", bd("1000"), bd("20"), bd("30"))
        );

        var result = calculator.calculate(debts, BigDecimal.ZERO, PayoffStrategy.SNOWBALL);

        assertThat(result.totalInterestPaid()).isGreaterThan(BigDecimal.ZERO);
    }

    // --- AVALANCHE ---

    @Test
    @DisplayName("avalanche: orders debts highest-rate-first")
    void should_orderHighestRateFirst_when_strategyIsAvalanche() {
        List<DebtRecord> debts = List.of(
                debtRecord(1L, "Low Rate", bd("2000"), bd("5"), bd("50")),
                debtRecord(2L, "High Rate", bd("500"), bd("20"), bd("25"))
        );

        var result = calculator.calculate(debts, BigDecimal.ZERO, PayoffStrategy.AVALANCHE);

        assertThat(result.debtSchedules().get(0).debtName()).isEqualTo("High Rate");
    }

    @Test
    @DisplayName("avalanche: tiebreaker is larger balance when rates are equal")
    void should_orderLargerBalanceFirst_when_ratesAreEqual() {
        List<DebtRecord> debts = List.of(
                debtRecord(1L, "Smaller", bd("300"), bd("15"), bd("30")),
                debtRecord(2L, "Larger", bd("800"), bd("15"), bd("40"))
        );

        var result = calculator.calculate(debts, BigDecimal.ZERO, PayoffStrategy.AVALANCHE);

        assertThat(result.debtSchedules().get(0).debtName()).isEqualTo("Larger");
    }

    @Test
    @DisplayName("avalanche: typically has same or less total interest than snowball")
    void should_haveEqualOrLessInterest_when_usingAvalancheVsSnowball() {
        List<DebtRecord> debts = List.of(
                debtRecord(1L, "Low Rate", bd("2000"), bd("5"), bd("50")),
                debtRecord(2L, "High Rate", bd("500"), bd("20"), bd("25"))
        );

        var snowball = calculator.calculate(debts, bd("50"), PayoffStrategy.SNOWBALL);
        var avalanche = calculator.calculate(debts, bd("50"), PayoffStrategy.AVALANCHE);

        assertThat(avalanche.totalInterestPaid()).isLessThanOrEqualTo(snowball.totalInterestPaid());
    }

    // --- EDGE CASES ---

    @Test
    @DisplayName("returns empty result when debt list is empty")
    void should_returnEmptyResult_when_noDebts() {
        var result = calculator.calculate(List.of(), BigDecimal.ZERO, PayoffStrategy.SNOWBALL);

        assertThat(result.totalMonths()).isZero();
        assertThat(result.totalInterestPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.debtSchedules()).isEmpty();
    }

    @Test
    @DisplayName("amortization entries are non-empty for each debt")
    void should_haveAmortizationEntries_when_debtHasBalance() {
        List<DebtRecord> debts = List.of(
                debtRecord(1L, "Card", bd("500"), bd("18"), bd("25"))
        );

        var result = calculator.calculate(debts, BigDecimal.ZERO, PayoffStrategy.SNOWBALL);

        assertThat(result.debtSchedules().get(0).schedule()).isNotEmpty();
    }

    @Test
    @DisplayName("remaining balance in last entry is zero")
    void should_reachZeroBalance_when_amortizationCompletes() {
        List<DebtRecord> debts = List.of(
                debtRecord(1L, "Loan", bd("1000"), bd("12"), bd("100"))
        );

        var result = calculator.calculate(debts, BigDecimal.ZERO, PayoffStrategy.SNOWBALL);
        var entries = result.debtSchedules().get(0).schedule();

        assertThat(entries.getLast().remainingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("payoff date is in the future")
    void should_setPayoffDateInFuture_when_debtExists() {
        List<DebtRecord> debts = List.of(
                debtRecord(1L, "D", bd("500"), bd("10"), bd("50"))
        );

        var result = calculator.calculate(debts, BigDecimal.ZERO, PayoffStrategy.SNOWBALL);

        assertThat(result.payoffDate()).isAfterOrEqualTo(java.time.LocalDate.now());
    }

    // --- helpers ---

    private DebtRecord debtRecord(Long id, String name, BigDecimal balance, BigDecimal rate, BigDecimal minPayment) {
        return DebtRecord.builder()
                .id(id)
                .username("testuser")
                .name(name)
                .balance(balance)
                .interestRate(rate)
                .minimumPayment(minPayment)
                .build();
    }

    private BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
