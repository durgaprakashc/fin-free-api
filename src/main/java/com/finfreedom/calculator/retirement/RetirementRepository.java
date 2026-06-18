package com.finfreedom.calculator.retirement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RetirementRepository extends JpaRepository<RetirementProjection, Long> {

    List<RetirementProjection> findByUsername(String username);

    Optional<RetirementProjection> findByIdAndUsername(Long id, String username);
}
