package com.finfreedom.calculator.retirement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class RetirementService {

    private final RetirementRepository repository;

    public RetirementDTO create(RetirementDTO dto, String username) {
        RetirementProjection entity = RetirementProjection.builder()
                .username(username)
                .currentAge(dto.getCurrentAge())
                .retirementAge(dto.getRetirementAge())
                .currentSavings(dto.getCurrentSavings())
                .monthlyContribution(dto.getMonthlyContribution())
                .expectedReturnRate(dto.getExpectedReturnRate())
                .inflationRate(dto.getInflationRate())
                .build();

        calculate(entity);
        return RetirementDTO.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<RetirementDTO> findAllByUser(String username) {
        return repository.findByUsername(username).stream()
                .map(RetirementDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RetirementDTO findById(Long id, String username) {
        return repository.findByIdAndUsername(id, username)
                .map(RetirementDTO::from)
                .orElseThrow(() -> new RetirementNotFoundException(id));
    }

    public RetirementDTO update(Long id, RetirementDTO dto, String username) {
        RetirementProjection entity = repository.findByIdAndUsername(id, username)
                .orElseThrow(() -> new RetirementNotFoundException(id));

        entity.setCurrentAge(dto.getCurrentAge());
        entity.setRetirementAge(dto.getRetirementAge());
        entity.setCurrentSavings(dto.getCurrentSavings());
        entity.setMonthlyContribution(dto.getMonthlyContribution());
        entity.setExpectedReturnRate(dto.getExpectedReturnRate());
        entity.setInflationRate(dto.getInflationRate());
        calculate(entity);

        return RetirementDTO.from(repository.save(entity));
    }

    public void delete(Long id, String username) {
        RetirementProjection entity = repository.findByIdAndUsername(id, username)
                .orElseThrow(() -> new RetirementNotFoundException(id));
        repository.delete(entity);
    }

    private void calculate(RetirementProjection entity) {
        int yearsToRetirement = entity.getRetirementAge() - entity.getCurrentAge();
        if (yearsToRetirement <= 0) {
            entity.setProjectedCorpus(entity.getCurrentSavings());
            entity.setMonthlyRetirementIncome(entity.getCurrentSavings()
                    .divide(BigDecimal.valueOf(240), 2, RoundingMode.HALF_UP));
            return;
        }

        double annualRate = entity.getExpectedReturnRate() / 100.0;
        double monthlyRate = annualRate / 12.0;
        int months = yearsToRetirement * 12;

        // FV of current savings
        double fvSavings = entity.getCurrentSavings().doubleValue()
                * Math.pow(1 + annualRate, yearsToRetirement);

        // FV of monthly contributions (annuity)
        double fvContributions = 0;
        if (monthlyRate > 0) {
            fvContributions = entity.getMonthlyContribution().doubleValue()
                    * ((Math.pow(1 + monthlyRate, months) - 1) / monthlyRate);
        } else {
            fvContributions = entity.getMonthlyContribution().doubleValue() * months;
        }

        double corpus = fvSavings + fvContributions;

        // Inflation-adjusted corpus
        double realCorpus = corpus / Math.pow(1 + entity.getInflationRate() / 100.0, yearsToRetirement);

        BigDecimal projectedCorpus = BigDecimal.valueOf(corpus).setScale(2, RoundingMode.HALF_UP);
        // 4% safe withdrawal rate, monthly
        BigDecimal monthlyIncome = BigDecimal.valueOf(realCorpus * 0.04 / 12)
                .setScale(2, RoundingMode.HALF_UP);

        entity.setProjectedCorpus(projectedCorpus);
        entity.setMonthlyRetirementIncome(monthlyIncome);
    }
}
