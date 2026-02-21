package com.ngxbot.common.exception;

public class KillSwitchActiveException extends RuntimeException {
    public KillSwitchActiveException(String message) {
        super(message);
    }
}
