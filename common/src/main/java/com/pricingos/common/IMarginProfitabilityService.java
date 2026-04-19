package com.pricingos.common;

import java.time.LocalDateTime;

// [Requirement 1 - Margin Profitability] Interface contract — implementation to be provided by consuming team
public interface IMarginProfitabilityService {

    // Aggregates marginConceded and marginProtected for the given period.
    // Data source: profitability_analytics table, filtered by recorded_at within [startDate, endDate].
    // Returns a MarginProfitabilityResult combining both APPROVED and REJECTED discount_amount sums.
    MarginProfitabilityResult getMarginProfitabilitySummary(LocalDateTime startDate, LocalDateTime endDate);
}
