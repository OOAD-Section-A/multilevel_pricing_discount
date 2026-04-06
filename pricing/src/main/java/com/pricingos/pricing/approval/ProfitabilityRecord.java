package com.pricingos.pricing.approval;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;

import java.time.LocalDateTime;

/**
 * Immutable record capturing the outcome of a single approval decision for
 * profitability analytics purposes.
 *
 * <p>Component 8 — Price Approval Workflow: Profitability Analytics feature.
 * Supports: "Generates reports showing which pricing strategies are driving the
 * most revenue versus which are eroding margins."
 *
 * <p>Recorded by {@link ProfitabilityAnalyticsObserver} on every APPROVED or REJECTED event.
 * The {@code discountAmount} field drives revenue-delta and savings calculations.
 *
 * <p>Uses Java 16+ {@code record} syntax (concise, immutable, auto-generated accessors).
 */
public record ProfitabilityRecord(

    /** The approval request ID this record corresponds to. */
    String approvalId,

    /** The type of override request (MANUAL_DISCOUNT, CONTRACT_BYPASS, POLICY_EXCEPTION). */
    ApprovalRequestType requestType,

    /** The discount amount that was requested (revenue at risk if approved). */
    double discountAmount,

    /** The final decision — APPROVED (margin eroded) or REJECTED (margin saved). */
    ApprovalStatus finalStatus,

    /** Wall-clock time when this record was created (at decision point). */
    LocalDateTime recordedAt

) {}
