package com.ngxbot.execution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes ALL Playwright browser operations. Every method on TroveBrowserAgent
 * must acquire this lock before interacting with the browser.
 */
@Component
@Slf4j
public class BrowserSessionLock {

    private final ReentrantLock lock = new ReentrantLock(true); // fair lock
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    /**
     * Acquires the browser lock with timeout.
     * @throws IllegalStateException if lock cannot be acquired within timeout
     */
    public void acquire() {
        try {
            boolean acquired = lock.tryLock(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.error("Failed to acquire browser session lock after {}s", DEFAULT_TIMEOUT_SECONDS);
                throw new IllegalStateException("Browser session lock timeout — another operation is in progress");
            }
            log.debug("Browser session lock acquired by thread {}", Thread.currentThread().getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for browser session lock", e);
        }
    }

    /**
     * Releases the browser lock.
     */
    public void release() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Browser session lock released by thread {}", Thread.currentThread().getName());
        }
    }

    /**
     * Executes an operation while holding the browser lock.
     */
    public <T> T executeWithLock(BrowserOperation<T> operation) throws Exception {
        acquire();
        try {
            return operation.execute();
        } finally {
            release();
        }
    }

    /**
     * Checks if the lock is currently held.
     */
    public boolean isLocked() {
        return lock.isLocked();
    }

    @FunctionalInterface
    public interface BrowserOperation<T> {
        T execute() throws Exception;
    }
}
