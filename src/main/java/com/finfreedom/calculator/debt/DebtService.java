package com.finfreedom.calculator.debt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository repository;
    private final DebtPayoffCalculator calculator;

    public DebtDTO create(DebtDTO dto, String username) {
        DebtRecord entity = DebtRecord.builder()
                .username(username)
                .name(dto.getName())
                .balance(dto.getBalance())
                .interestRate(dto.getInterestRate())
                .minimumPayment(dto.getMinimumPayment())
                .build();
        return DebtDTO.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<DebtDTO> findAllByUser(String username) {
        return repository.findByUsername(username).stream()
                .map(DebtDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DebtDTO findById(Long id, String username) {
        return repository.findByIdAndUsername(id, username)
                .map(DebtDTO::from)
                .orElseThrow(() -> new DebtNotFoundException(id));
    }

    public void delete(Long id, String username) {
        DebtRecord entity = repository.findByIdAndUsername(id, username)
                .orElseThrow(() -> new DebtNotFoundException(id));
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public PayoffResultDTO calculatePayoff(String username, BigDecimal extraPayment, PayoffStrategy strategy) {
        List<DebtRecord> debts = repository.findByUsername(username);
        return calculator.calculate(debts, extraPayment, strategy);
    }

    @Transactional(readOnly = true)
    public ComparisonDTO compareStrategies(String username, BigDecimal extraPayment) {
        List<DebtRecord> debts = repository.findByUsername(username);
        PayoffResultDTO snowball = calculator.calculate(debts, extraPayment, PayoffStrategy.SNOWBALL);
        PayoffResultDTO avalanche = calculator.calculate(debts, extraPayment, PayoffStrategy.AVALANCHE);

        BigDecimal interestSaved = snowball.totalInterestPaid()
                .subtract(avalanche.totalInterestPaid());
        int monthsSaved = snowball.totalMonths() - avalanche.totalMonths();

        return new ComparisonDTO(snowball, avalanche, interestSaved, monthsSaved);
    }
}
