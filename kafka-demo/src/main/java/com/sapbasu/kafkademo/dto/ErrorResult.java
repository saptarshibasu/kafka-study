package com.sapbasu.kafkademo.dto;

import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class ErrorResult {
    private final String errorMessage;
    private final HttpStatus httpStatus;

    public ErrorResult(String errorMessage, HttpStatus httpStatus) {
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
    }
}
