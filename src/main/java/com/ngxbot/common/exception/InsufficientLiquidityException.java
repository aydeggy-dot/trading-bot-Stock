package com.ngxbot.common.exception;

public class InsufficientLiquidityException extends RuntimeException {
    public InsufficientLiquidityException(String message) {
        super(message);
    }
}
