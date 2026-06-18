package com.finfreedom.calculator.retirement;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetirementDTO {

    private Long id;

    @NotNull
    @Min(18) @Max(100)
    private Integer currentAge;

    @NotNull
    @Min(30) @Max(100)
    private Integer retirementAge;

    @NotNull
    @PositiveOrZero
    private BigDecimal currentSavings;

    @NotNull
    @Positive
    private BigDecimal monthlyContribution;

    @NotNull
    @DecimalMin("0.0") @DecimalMax("30.0")
    private Double expectedReturnRate;

    @NotNull
    @DecimalMin("0.0") @DecimalMax("20.0")
    private Double inflationRate;

    private BigDecimal projectedCorpus;
    private BigDecimal monthlyRetirementIncome;
    private Instant createdAt;
    private Instant updatedAt;

    public static RetirementDTO from(RetirementProjection entity) {
        return RetirementDTO.builder()
                .id(entity.getId())
                .currentAge(entity.getCurrentAge())
                .retirementAge(entity.getRetirementAge())
                .currentSavings(entity.getCurrentSavings())
                .monthlyContribution(entity.getMonthlyContribution())
                .expectedReturnRate(entity.getExpectedReturnRate())
                .inflationRate(entity.getInflationRate())
                .projectedCorpus(entity.getProjectedCorpus())
                .monthlyRetirementIncome(entity.getMonthlyRetirementIncome())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
