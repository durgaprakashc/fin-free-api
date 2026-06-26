package com.finfreedom.calculator.retirement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calculator/retirement")
@RequiredArgsConstructor
@Tag(name = "Retirement Calculator", description = "Retirement projection endpoints")
public class RetirementController {

    private final RetirementService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new retirement projection")
    public ResponseEntity<RetirementDTO> create(
            @RequestBody @Valid RetirementDTO dto,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(dto, user.getUsername()));
    }

    @GetMapping
    @Operation(summary = "List all retirement projections for the current user")
    public ResponseEntity<List<RetirementDTO>> findAll(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.findAllByUser(user.getUsername()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single retirement projection by ID")
    public ResponseEntity<RetirementDTO> findById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.findById(id, user.getUsername()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a retirement projection")
    public ResponseEntity<RetirementDTO> update(
            @PathVariable Long id,
            @RequestBody @Valid RetirementDTO dto,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.update(id, dto, user.getUsername()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a retirement projection")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        service.delete(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
