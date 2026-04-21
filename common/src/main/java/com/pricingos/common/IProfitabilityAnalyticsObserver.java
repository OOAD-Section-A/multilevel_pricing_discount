package com.pricingos.common;

import java.time.LocalDateTime;

// [Requirement 1 - Margin Profitability] Interface contract - implementation to be provided by consuming team
public interface IProfitabilityAnalyticsObserver {

    // Returns total revenue lost to approved manager price overrides in the given period.
    // Data source: profitability_analytics table - filter by final_status='APPROVED'
    //   and recorded_at within [startDate, endDate], sum discount_amount.
    double getApprovedRevenueDelta(LocalDateTime startDate, LocalDateTime endDate);

    // Returns total margin saved by rejecting discount requests in the given period.
    // Data source: profitability_analytics table - filter by final_status='REJECTED'
    //   and recorded_at within [startDate, endDate], sum discount_amount.
    double getRejectedSavings(LocalDateTime startDate, LocalDateTime endDate);
}
