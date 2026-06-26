package com.finfreedom.calculator.retirement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "retirement_projections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetirementProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private int currentAge;

    @Column(nullable = false)
    private int retirementAge;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentSavings;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyContribution;

    @Column(nullable = false)
    private double expectedReturnRate;

    @Column(nullable = false)
    private double inflationRate;

    @Column(precision = 19, scale = 2)
    private BigDecimal projectedCorpus;

    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyRetirementIncome;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
