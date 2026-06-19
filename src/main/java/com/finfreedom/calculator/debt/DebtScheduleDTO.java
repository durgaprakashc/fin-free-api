package com.finfreedom.calculator.debt;

import java.math.BigDecimal;
import java.util.List;

public record DebtScheduleDTO(
        Long debtId,
        String debtName,
        BigDecimal originalBalance,
        BigDecimal interestRate,
        BigDecimal totalInterestPaid,
        int monthsToPayoff,
        List<AmortizationEntry> schedule
) {}
