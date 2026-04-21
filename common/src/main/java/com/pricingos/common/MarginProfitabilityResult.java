package com.pricingos.common;

import java.time.LocalDateTime;

// [Requirement 1 - Margin Profitability] Interface contract - implementation to be provided by consuming team
// All monetary values are sourced from profitability_analytics.discount_amount (DECIMAL(19,4))
public record MarginProfitabilityResult(
    double marginConceded,      // sum of discount_amount where final_status='APPROVED' in period
    double marginProtected,     // sum of discount_amount where final_status='REJECTED' in period
    DateRange period            // the time window this result covers (maps to recorded_at range)
) {

    public record DateRange(
        LocalDateTime start,    // inclusive lower bound for profitability_analytics.recorded_at
        LocalDateTime end       // inclusive upper bound for profitability_analytics.recorded_at
    ) {}
}
