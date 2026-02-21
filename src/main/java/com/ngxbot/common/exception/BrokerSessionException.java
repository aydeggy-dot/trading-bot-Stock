package com.ngxbot.common.exception;

public class BrokerSessionException extends RuntimeException {
    public BrokerSessionException(String message) {
        super(message);
    }

    public BrokerSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
