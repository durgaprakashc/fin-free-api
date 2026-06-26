package com.finfreedom.calculator.debt;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/calculator/debt")
@RequiredArgsConstructor
@Tag(name = "Debt Payoff Calculator")
public class DebtController {

    private final DebtService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<DebtDTO> create(
            @RequestBody @Valid DebtDTO dto,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(dto, user.getUsername()));
    }

    @GetMapping
    public List<DebtDTO> findAll(@AuthenticationPrincipal UserDetails user) {
        return service.findAllByUser(user.getUsername());
    }

    @GetMapping("/{id}")
    public DebtDTO findById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return service.findById(id, user.getUsername());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        service.delete(id, user.getUsername());
    }

    @PostMapping("/payoff")
    public PayoffResultDTO calculatePayoff(
            @RequestBody @Valid PayoffRequestDTO request,
            @AuthenticationPrincipal UserDetails user) {
        return service.calculatePayoff(user.getUsername(), request.extraMonthlyPayment(), request.strategy());
    }

    @GetMapping("/payoff/compare")
    public ComparisonDTO compare(
            @RequestParam(defaultValue = "0.00") BigDecimal extraMonthlyPayment,
            @AuthenticationPrincipal UserDetails user) {
        return service.compareStrategies(user.getUsername(), extraMonthlyPayment);
    }
}
