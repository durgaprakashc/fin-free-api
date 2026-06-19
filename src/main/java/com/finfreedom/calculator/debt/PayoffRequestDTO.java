package com.finfreedom.calculator.debt;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PayoffRequestDTO(
        @NotNull PayoffStrategy strategy,
        @NotNull @DecimalMin("0.00") BigDecimal extraMonthlyPayment
) {}
