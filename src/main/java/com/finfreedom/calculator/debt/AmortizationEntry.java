package com.finfreedom.calculator.debt;

import java.math.BigDecimal;

public record AmortizationEntry(
        int month,
        BigDecimal payment,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal remainingBalance
) {}
