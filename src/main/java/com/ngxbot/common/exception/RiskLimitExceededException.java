package com.ngxbot.common.exception;

public class RiskLimitExceededException extends RuntimeException {
    public RiskLimitExceededException(String message) {
        super(message);
    }
}
