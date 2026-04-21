package com.pricingos.pricing.approval;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ProfitabilityAnalyticsObserver implements ApprovalEventObserver {
    
    private static final Logger LOGGER = Logger.getLogger(ProfitabilityAnalyticsObserver.class.getName());

    private final Clock clock;

    public ProfitabilityAnalyticsObserver() {
        this(Clock.systemDefaultZone());
    }

    public ProfitabilityAnalyticsObserver(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public void onRequestSubmitted(ApprovalRequest request, String approverId) {}

    @Override
    public void onRequestApproved(ApprovalRequest request) {
        ProfitabilityEntry entry = new ProfitabilityEntry(
            request.getApprovalId(),
            request.getRequestType(),
            request.getRequestedDiscountAmt(),
            ApprovalStatus.APPROVED,
            LocalDateTime.now(clock)
        );
        LOGGER.log(Level.INFO, "[ANALYTICS] Approval recorded - " + entry);
    }

    @Override
    public void onRequestRejected(ApprovalRequest request) {
        ProfitabilityEntry entry = new ProfitabilityEntry(
            request.getApprovalId(),
            request.getRequestType(),
            request.getRequestedDiscountAmt(),
            ApprovalStatus.REJECTED,
            LocalDateTime.now(clock)
        );
        LOGGER.log(Level.INFO, "[ANALYTICS] Rejection recorded - " + entry);
    }

    @Override
    public void onRequestEscalated(ApprovalRequest request, String escalationTarget) {}

    /**
     * Returns 0 - analytics are now logged only, not persisted to database.
     * For persistence, database team needs to provide analytics storage support.
     */
    public double getApprovedRevenueDelta() {
        LOGGER.log(Level.WARNING, "[ANALYTICS] getApprovedRevenueDelta called but database persistence not available");
        return 0.0;
    }

    /**
     * Returns 0 - analytics are now logged only, not persisted to database.
     */
    public double getRejectedSavings() {
        LOGGER.log(Level.WARNING, "[ANALYTICS] getRejectedSavings called but database persistence not available");
        return 0.0;
    }

    /**
     * Returns empty map - analytics are now logged only, not persisted to database.
     */
    public Map<ApprovalRequestType, DoubleSummaryStatistics> getBreakdownByType() {
        LOGGER.log(Level.WARNING, "[ANALYTICS] getBreakdownByType called but database persistence not available");
        return Collections.emptyMap();
    }

    /**
     * Returns 0 - analytics are now logged only, not persisted to database.
     */
    public int getTotalDecisions() {
        LOGGER.log(Level.WARNING, "[ANALYTICS] getTotalDecisions called but database persistence not available");
        return 0;
    }

    /**
     * Returns empty list - analytics are now logged only, not persisted to database.
     */
    public List<ProfitabilityEntry> getAllRecords() {
        LOGGER.log(Level.WARNING, "[ANALYTICS] getAllRecords called but database persistence not available");
        return Collections.emptyList();
    }

    public record ProfitabilityEntry(
        String approvalId,
        ApprovalRequestType requestType,
        double discountAmount,
        ApprovalStatus finalStatus,
        LocalDateTime recordedAt
    ) {}
}
