package com.ngxbot.risk.entity;

import java.util.List;

public record RiskCheckResult(
    boolean passed,
    String checkName,
    String description,
    List<String> violations
) {
    public static RiskCheckResult pass(String checkName) {
        return new RiskCheckResult(true, checkName, "Check passed", List.of());
    }

    public static RiskCheckResult fail(String checkName, String description, List<String> violations) {
        return new RiskCheckResult(false, checkName, description, violations);
    }
}
