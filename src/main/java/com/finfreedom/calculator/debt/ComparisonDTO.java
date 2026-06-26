package com.finfreedom.calculator.debt;

import java.math.BigDecimal;

public record ComparisonDTO(
        PayoffResultDTO snowball,
        PayoffResultDTO avalanche,
        BigDecimal interestSavedByAvalanche,
        int monthsSavedByAvalanche
) {}
