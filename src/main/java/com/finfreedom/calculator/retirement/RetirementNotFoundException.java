package com.finfreedom.calculator.retirement;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RetirementNotFoundException extends RuntimeException {

    public RetirementNotFoundException(Long id) {
        super("Retirement projection not found with id: " + id);
    }
}
