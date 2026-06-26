package com.finfreedom.calculator.debt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebtServiceTest {

    @Mock
    private DebtRepository repository;

    @Mock
    private DebtPayoffCalculator calculator;

    @InjectMocks
    private DebtService service;

    // --- CREATE ---

    @Test
    @DisplayName("create: saves entity and returns DTO")
    void should_returnSavedDto_when_createCalled() {
        DebtDTO dto = buildDto(null, "Credit Card", bd("1000"), bd("20"), bd("30"));
        DebtRecord saved = buildRecord(1L, "testuser", dto);
        when(repository.save(any())).thenReturn(saved);

        DebtDTO result = service.create(dto, "testuser");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Credit Card");
        verify(repository).save(any(DebtRecord.class));
    }

    @Test
    @DisplayName("create: username from param, not from DTO")
    void should_useUsernameParam_when_creating() {
        DebtDTO dto = buildDto(null, "Loan", bd("5000"), bd("8"), bd("100"));
        DebtRecord saved = buildRecord(2L, "alice", dto);
        when(repository.save(any())).thenReturn(saved);

        service.create(dto, "alice");

        verify(repository).save(argThat(r -> "alice".equals(r.getUsername())));
    }

    // --- FIND ALL ---

    @Test
    @DisplayName("findAllByUser: returns mapped DTOs")
    void should_returnAllDebts_when_userHasRecords() {
        when(repository.findByUsername("alice")).thenReturn(List.of(
                buildRecord(1L, "alice", buildDto(null, "D1", bd("100"), bd("5"), bd("10"))),
                buildRecord(2L, "alice", buildDto(null, "D2", bd("200"), bd("10"), bd("20")))
        ));

        List<DebtDTO> result = service.findAllByUser("alice");

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findAllByUser: returns empty list when user has no debts")
    void should_returnEmptyList_when_noDebtsExist() {
        when(repository.findByUsername("bob")).thenReturn(List.of());

        List<DebtDTO> result = service.findAllByUser("bob");

        assertThat(result).isEmpty();
    }

    // --- FIND BY ID ---

    @Test
    @DisplayName("findById: returns DTO for valid id and owner")
    void should_returnDto_when_debtBelongsToUser() {
        DebtRecord record = buildRecord(5L, "alice", buildDto(null, "Car Loan", bd("8000"), bd("6"), bd("150")));
        when(repository.findByIdAndUsername(5L, "alice")).thenReturn(Optional.of(record));

        DebtDTO result = service.findById(5L, "alice");

        assertThat(result.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("findById: throws DebtNotFoundException for unknown id")
    void should_throwNotFound_when_idDoesNotExist() {
        when(repository.findByIdAndUsername(99L, "alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L, "alice"))
                .isInstanceOf(DebtNotFoundException.class);
    }

    @Test
    @DisplayName("findById: throws DebtNotFoundException for another user's debt (no id leak)")
    void should_throwNotFound_when_debtBelongsToDifferentUser() {
        when(repository.findByIdAndUsername(1L, "bob")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(1L, "bob"))
                .isInstanceOf(DebtNotFoundException.class);
    }

    // --- DELETE ---

    @Test
    @DisplayName("delete: calls repository.delete for owned record")
    void should_deleteRecord_when_ownerDeletes() {
        DebtRecord record = buildRecord(3L, "alice", buildDto(null, "Loan", bd("500"), bd("5"), bd("50")));
        when(repository.findByIdAndUsername(3L, "alice")).thenReturn(Optional.of(record));

        service.delete(3L, "alice");

        verify(repository).delete(record);
    }

    @Test
    @DisplayName("delete: throws DebtNotFoundException when record not found")
    void should_throwNotFound_when_deletingNonExistentDebt() {
        when(repository.findByIdAndUsername(42L, "alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(42L, "alice"))
                .isInstanceOf(DebtNotFoundException.class);
    }

    // --- PAYOFF ---

    @Test
    @DisplayName("calculatePayoff: delegates to DebtPayoffCalculator")
    void should_delegateToCalculator_when_payoffCalled() {
        List<DebtRecord> records = List.of(buildRecord(1L, "alice",
                buildDto(null, "Card", bd("1000"), bd("20"), bd("30"))));
        when(repository.findByUsername("alice")).thenReturn(records);
        PayoffResultDTO expected = mock(PayoffResultDTO.class);
        when(calculator.calculate(records, bd("50"), PayoffStrategy.SNOWBALL)).thenReturn(expected);

        PayoffResultDTO result = service.calculatePayoff("alice", bd("50"), PayoffStrategy.SNOWBALL);

        assertThat(result).isSameAs(expected);
    }

    // --- COMPARE ---

    @Test
    @DisplayName("compareStrategies: returns both results with savings metrics")
    void should_returnComparisonWithSavings_when_compareCalledWithDebts() {
        List<DebtRecord> records = List.of(buildRecord(1L, "alice",
                buildDto(null, "Card", bd("1000"), bd("20"), bd("30"))));
        when(repository.findByUsername("alice")).thenReturn(records);

        PayoffResultDTO snowball = new PayoffResultDTO(PayoffStrategy.SNOWBALL, bd("300"), 24,
                java.time.LocalDate.now().plusMonths(24), List.of());
        PayoffResultDTO avalanche = new PayoffResultDTO(PayoffStrategy.AVALANCHE, bd("250"), 22,
                java.time.LocalDate.now().plusMonths(22), List.of());

        when(calculator.calculate(records, BigDecimal.ZERO, PayoffStrategy.SNOWBALL)).thenReturn(snowball);
        when(calculator.calculate(records, BigDecimal.ZERO, PayoffStrategy.AVALANCHE)).thenReturn(avalanche);

        ComparisonDTO comparison = service.compareStrategies("alice", BigDecimal.ZERO);

        assertThat(comparison.snowball()).isSameAs(snowball);
        assertThat(comparison.avalanche()).isSameAs(avalanche);
        assertThat(comparison.interestSavedByAvalanche()).isEqualByComparingTo(bd("50"));
        assertThat(comparison.monthsSavedByAvalanche()).isEqualTo(2);
    }

    // --- helpers ---

    private DebtDTO buildDto(Long id, String name, BigDecimal balance, BigDecimal rate, BigDecimal min) {
        return DebtDTO.builder().id(id).name(name).balance(balance).interestRate(rate).minimumPayment(min).build();
    }

    private DebtRecord buildRecord(Long id, String username, DebtDTO dto) {
        return DebtRecord.builder()
                .id(id).username(username)
                .name(dto.getName()).balance(dto.getBalance())
                .interestRate(dto.getInterestRate()).minimumPayment(dto.getMinimumPayment())
                .build();
    }

    private BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
