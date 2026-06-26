package com.finfreedom.calculator.debt;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DebtNotFoundException extends RuntimeException {

    public DebtNotFoundException(Long id) {
        super("Debt record not found with id: " + id);
    }
}
