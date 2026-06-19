package com.finfreedom.calculator.debt;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Component
public class DebtPayoffCalculator {

    private static final int MAX_MONTHS = 1200;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public PayoffResultDTO calculate(List<DebtRecord> debts, BigDecimal extraMonthlyPayment, PayoffStrategy strategy) {
        if (debts.isEmpty()) {
            return new PayoffResultDTO(strategy, ZERO, 0, LocalDate.now(), List.of());
        }

        List<DebtRecord> ordered = orderByStrategy(debts, strategy);
        List<DebtScheduleDTO> schedules = simulatePayoff(ordered, extraMonthlyPayment);

        BigDecimal totalInterest = schedules.stream()
                .map(DebtScheduleDTO::totalInterestPaid)
                .reduce(ZERO, BigDecimal::add);

        int totalMonths = schedules.stream()
                .mapToInt(DebtScheduleDTO::monthsToPayoff)
                .max()
                .orElse(0);

        return new PayoffResultDTO(strategy, totalInterest, totalMonths,
                LocalDate.now().plusMonths(totalMonths), schedules);
    }

    private List<DebtRecord> orderByStrategy(List<DebtRecord> debts, PayoffStrategy strategy) {
        Comparator<DebtRecord> comparator = switch (strategy) {
            case SNOWBALL -> Comparator.comparing(DebtRecord::getBalance)
                    .thenComparing(Comparator.comparing(DebtRecord::getInterestRate).reversed());
            case AVALANCHE -> Comparator.comparing(DebtRecord::getInterestRate).reversed()
                    .thenComparing(Comparator.comparing(DebtRecord::getBalance).reversed());
        };
        return debts.stream().sorted(comparator).toList();
    }

    /**
     * Month-by-month simulation: all debts run simultaneously, minimum payments on non-targets,
     * extra (+ freed minimums) routed to the current target (first unpaid in ordered list).
     */
    private List<DebtScheduleDTO> simulatePayoff(List<DebtRecord> ordered, BigDecimal extraMonthlyPayment) {
        int n = ordered.size();
        BigDecimal[] balances = ordered.stream()
                .map(d -> d.getBalance().setScale(2, RoundingMode.HALF_UP))
                .toArray(BigDecimal[]::new);
        BigDecimal[] monthlyRates = ordered.stream()
                .map(d -> d.getInterestRate().divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP))
                .toArray(BigDecimal[]::new);

        List<List<AmortizationEntry>> allEntries = new ArrayList<>();
        for (int i = 0; i < n; i++) allEntries.add(new ArrayList<>());

        boolean[] paid = new boolean[n];
        int paidCount = 0;
        BigDecimal rollingExtra = extraMonthlyPayment;

        for (int month = 1; paidCount < n && month <= MAX_MONTHS; month++) {
            int targetIdx = firstUnpaidIndex(paid, n);

            for (int i = 0; i < n; i++) {
                if (paid[i]) continue;

                BigDecimal interest = balances[i].multiply(monthlyRates[i]).setScale(2, RoundingMode.HALF_UP);
                BigDecimal minPayment = ordered.get(i).getMinimumPayment();
                BigDecimal extra = i == targetIdx ? rollingExtra : ZERO;
                BigDecimal payment = minPayment.add(extra).min(balances[i].add(interest));

                BigDecimal principal = payment.subtract(interest).max(ZERO).setScale(2, RoundingMode.HALF_UP);
                balances[i] = balances[i].subtract(principal).max(ZERO).setScale(2, RoundingMode.HALF_UP);

                allEntries.get(i).add(new AmortizationEntry(month, payment, principal, interest, balances[i]));

                if (balances[i].compareTo(ZERO) == 0 && !paid[i]) {
                    paid[i] = true;
                    paidCount++;
                    rollingExtra = rollingExtra.add(minPayment);
                }
            }
        }

        List<DebtScheduleDTO> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(toScheduleDTO(ordered.get(i), allEntries.get(i)));
        }
        return result;
    }

    private int firstUnpaidIndex(boolean[] paid, int n) {
        for (int i = 0; i < n; i++) {
            if (!paid[i]) return i;
        }
        return -1;
    }

    private DebtScheduleDTO toScheduleDTO(DebtRecord record, List<AmortizationEntry> entries) {
        BigDecimal totalInterest = entries.stream()
                .map(AmortizationEntry::interest)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new DebtScheduleDTO(record.getId(), record.getName(), record.getBalance(),
                record.getInterestRate(), totalInterest, entries.size(), entries);
    }
}
