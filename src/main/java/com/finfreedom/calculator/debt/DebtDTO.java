package com.finfreedom.calculator.debt;

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
public class DebtDTO {

    private Long id;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal balance;

    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "100.00")
    private BigDecimal interestRate;

    @NotNull
    @DecimalMin(value = "1.00")
    private BigDecimal minimumPayment;

    private Instant createdAt;

    public static DebtDTO from(DebtRecord entity) {
        return DebtDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .balance(entity.getBalance())
                .interestRate(entity.getInterestRate())
                .minimumPayment(entity.getMinimumPayment())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
