package com.finfreedom.calculator.debt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<DebtRecord, Long> {

    List<DebtRecord> findByUsername(String username);

    Optional<DebtRecord> findByIdAndUsername(Long id, String username);
}
