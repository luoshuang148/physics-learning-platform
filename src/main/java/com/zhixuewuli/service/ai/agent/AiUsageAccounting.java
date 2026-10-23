package com.zhixuewuli.service.ai.agent;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class AiUsageAccounting {
    public enum UsageSource { REPORTED, ESTIMATED, UNKNOWN }
    public record Pricing(String currency, String version, BigDecimal inputPerMillion, BigDecimal outputPerMillion) {}
    public record Snapshot(long modelCalls, Long inputTokens, Long outputTokens, UsageSource usageSource,
                           BigDecimal cost, String currency, String pricingVersion) {}

    public Snapshot empty() { return new Snapshot(0, null, null, UsageSource.UNKNOWN, null, null, null); }

    public Snapshot addCall(Snapshot previous, Integer reportedInput, Integer reportedOutput,
                            Integer estimatedInput, Integer estimatedOutput, Pricing pricing) {
        boolean reported = reportedInput != null && reportedInput >= 0 && reportedOutput != null && reportedOutput >= 0;
        boolean estimated = !reported && estimatedInput != null && estimatedInput >= 0 && estimatedOutput != null && estimatedOutput >= 0;
        UsageSource source = reported ? UsageSource.REPORTED : estimated ? UsageSource.ESTIMATED : UsageSource.UNKNOWN;
        Long input = null, output = null;
        if (reported) { input = reportedInput.longValue(); output = reportedOutput.longValue(); }
        else if (estimated) { input = estimatedInput.longValue(); output = estimatedOutput.longValue(); }
        Long totalInput = previous.modelCalls() == 0 ? input
                : previous.inputTokens() == null || input == null ? null : previous.inputTokens() + input;
        Long totalOutput = previous.modelCalls() == 0 ? output
                : previous.outputTokens() == null || output == null ? null : previous.outputTokens() + output;
        UsageSource totalSource = previous.modelCalls() == 0 ? source : merge(previous.usageSource(), source);
        BigDecimal callCost = cost(input, output, pricing);
        BigDecimal totalCost = previous.modelCalls() == 0 ? callCost
                : previous.cost() == null || callCost == null ? null : previous.cost().add(callCost).stripTrailingZeros();
        String currency = pricing == null ? previous.currency() : pricing.currency();
        String pricingVersion = pricing == null ? previous.pricingVersion() : pricing.version();
        return new Snapshot(previous.modelCalls() + 1, totalInput, totalOutput, totalSource, totalCost, currency, pricingVersion);
    }

    private UsageSource merge(UsageSource left, UsageSource right) {
        if (left == UsageSource.UNKNOWN || right == UsageSource.UNKNOWN) return UsageSource.UNKNOWN;
        if (left == UsageSource.ESTIMATED || right == UsageSource.ESTIMATED) return UsageSource.ESTIMATED;
        return UsageSource.REPORTED;
    }

    private BigDecimal cost(Long input, Long output, Pricing pricing) {
        if (input == null || output == null || pricing == null || pricing.inputPerMillion() == null || pricing.outputPerMillion() == null) return null;
        BigDecimal amount = pricing.inputPerMillion().multiply(BigDecimal.valueOf(input))
                .add(pricing.outputPerMillion().multiply(BigDecimal.valueOf(output)))
                .divide(BigDecimal.valueOf(1_000_000), 12, RoundingMode.HALF_UP);
        return amount.stripTrailingZeros();
    }
}
