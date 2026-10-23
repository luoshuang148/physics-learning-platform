package com.zhixuewuli.service.ai.agent;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class AiUsageAccountingTest {
    private final AiUsageAccounting accounting = new AiUsageAccounting();
    private final AiUsageAccounting.Pricing price = new AiUsageAccounting.Pricing("USD", "fixture-v1",
            new BigDecimal("2.0"), new BigDecimal("8.0"));

    @Test void missingUsageWithoutAnEstimateRemainsUnknownNotZero() {
        var result = accounting.addCall(accounting.empty(), null, null, null, null, null);
        assertEquals(AiUsageAccounting.UsageSource.UNKNOWN, result.usageSource());
        assertNull(result.inputTokens()); assertNull(result.outputTokens()); assertNull(result.cost());
    }

    @Test void usageCanBeExplicitlyEstimatedWhenOnlyCharacterCountsAreAvailable() {
        var result = accounting.addCall(accounting.empty(), null, null, 80, 20, price);
        assertEquals(AiUsageAccounting.UsageSource.ESTIMATED, result.usageSource());
        assertEquals(80L, result.inputTokens()); assertEquals(20L, result.outputTokens());
        assertEquals(new BigDecimal("0.00032"), result.cost());
    }

    @Test void retryAttemptsAccumulateTokensAndCostInsteadOfDroppingEarlierCalls() {
        var first = accounting.addCall(accounting.empty(), 100, 20, null, null, price);
        var total = accounting.addCall(first, 50, 10, null, null, price);
        assertEquals(2, total.modelCalls());
        assertEquals(150L, total.inputTokens()); assertEquals(30L, total.outputTokens());
        assertEquals(0, new BigDecimal("0.00054").compareTo(total.cost()));
        assertEquals(AiUsageAccounting.UsageSource.REPORTED, total.usageSource());
    }

    @Test void unpricedModelsKeepCostUnknownEvenWhenTokenUsageIsReported() {
        var result = accounting.addCall(accounting.empty(), 30, 5, null, null, null);
        assertEquals(30L, result.inputTokens()); assertNull(result.cost()); assertNull(result.currency());
    }
}
