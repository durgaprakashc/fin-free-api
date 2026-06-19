package com.finfreedom.calculator.debt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PayoffResultDTO(
        PayoffStrategy strategy,
        BigDecimal totalInterestPaid,
        int totalMonths,
        LocalDate payoffDate,
        List<DebtScheduleDTO> debtSchedules
) {}
