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

/**
 * Concrete implementation of {@link ApprovalEventObserver} that records every approval
 * decision and provides profitability analytics reports.
 *
 * <p>Component 8 — Price Approval Workflow: Profitability Analytics feature.
 * Supports: "Generates reports showing which pricing strategies are driving the most
 * revenue versus which are eroding margins."
 *
 * <p>Behavioural pattern (Observer): registered with {@link ApprovalWorkflowEngine} to
 * receive lifecycle events. The engine never knows this class exists — SOLID OCP.
 *
 * <p>Key metrics exposed:
 * <ul>
 *   <li><b>Approved Revenue Delta</b>: total discount burden approved (margin eroded).</li>
 *   <li><b>Rejected Savings</b>: total discount amount blocked (margin protected).</li>
 *   <li><b>Breakdown by Type</b>: per-{@link ApprovalRequestType} discount statistics.</li>
 * </ul>
 */
public class ProfitabilityAnalyticsObserver implements ApprovalEventObserver {

    /** Thread-safe accumulator of all decision records. */
    private final List<ProfitabilityEntry> records = new CopyOnWriteArrayList<>();

    /** Clock for deterministic timestamps in tests. */
    private final Clock clock;

    /** Production constructor. */
    public ProfitabilityAnalyticsObserver() {
        this(Clock.systemDefaultZone());
    }

    /** Testing constructor — accepts an explicit clock. */
    public ProfitabilityAnalyticsObserver(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    // ── ApprovalEventObserver ─────────────────────────────────────────────────────

    /** Not recorded — submission doesn't affect profitability until a decision is made. */
    @Override
    public void onRequestSubmitted(ApprovalRequest request, String approverId) {}

    /**
     * Records an APPROVED decision. This discount amount is now a cost against margin
     * (revenue delta — margin eroded).
     */
    @Override
    public void onRequestApproved(ApprovalRequest request) {
        records.add(new ProfitabilityEntry(
            request.getApprovalId(),
            request.getRequestType(),
            request.getRequestedDiscountAmt(),
            ApprovalStatus.APPROVED,
            LocalDateTime.now(clock)
        ));
    }

    /**
     * Records a REJECTED decision. This discount amount was blocked, protecting the margin
     * (rejected savings — margin protected).
     */
    @Override
    public void onRequestRejected(ApprovalRequest request) {
        records.add(new ProfitabilityEntry(
            request.getApprovalId(),
            request.getRequestType(),
            request.getRequestedDiscountAmt(),
            ApprovalStatus.REJECTED,
            LocalDateTime.now(clock)
        ));
    }

    /** Not recorded — escalation is a routing event, not a financial decision. */
    @Override
    public void onRequestEscalated(ApprovalRequest request, String escalationTarget) {}

    // ── Analytics API ─────────────────────────────────────────────────────────────

    /**
     * Returns the total monetary value of all approved discounts.
     * This represents the total revenue delta (margin cost) from approved overrides.
     *
     * @return sum of {@code discountAmount} for all APPROVED records
     */
    public double getApprovedRevenueDelta() {
        return records.stream()
            .filter(r -> r.finalStatus() == ApprovalStatus.APPROVED)
            .mapToDouble(ProfitabilityEntry::discountAmount)
            .sum();
    }

    /**
     * Returns the total monetary value of all rejected discounts.
     * This represents the margin saved by the approval governance process.
     *
     * @return sum of {@code discountAmount} for all REJECTED records
     */
    public double getRejectedSavings() {
        return records.stream()
            .filter(r -> r.finalStatus() == ApprovalStatus.REJECTED)
            .mapToDouble(ProfitabilityEntry::discountAmount)
            .sum();
    }

    /**
     * Returns {@link DoubleSummaryStatistics} for discount amounts grouped by request type.
     * Allows the Pricing Admin to identify which override categories drive the most margin erosion.
     *
     * <p>Example use: a high sum for MANUAL_DISCOUNT indicates cashiers are overriding too often.
     *
     * @return map of ApprovalRequestType → summary statistics of discount amounts
     */
    public Map<ApprovalRequestType, DoubleSummaryStatistics> getBreakdownByType() {
        return records.stream().collect(
            Collectors.groupingBy(
                ProfitabilityEntry::requestType,
                Collectors.summarizingDouble(ProfitabilityEntry::discountAmount)
            )
        );
    }

    /**
     * Returns the total number of decisions recorded (approved + rejected).
     *
     * @return count of all records
     */
    public int getTotalDecisions() {
        return records.size();
    }

    /**
     * Returns an unmodifiable snapshot of all records, ordered by insertion time.
     * Used by the Pricing Admin dashboard for detailed drill-down analysis.
     *
     * @return unmodifiable list of profitability records
     */
    public List<ProfitabilityEntry> getAllRecords() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public record ProfitabilityEntry(
        String approvalId,
        ApprovalRequestType requestType,
        double discountAmount,
        ApprovalStatus finalStatus,
        LocalDateTime recordedAt
    ) {}
}
