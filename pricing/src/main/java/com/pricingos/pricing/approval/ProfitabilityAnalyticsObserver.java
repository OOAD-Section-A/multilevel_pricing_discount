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

public class ProfitabilityAnalyticsObserver implements ApprovalEventObserver {

    private final List<ProfitabilityEntry> records = new CopyOnWriteArrayList<>();

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
        records.add(new ProfitabilityEntry(
            request.getApprovalId(),
            request.getRequestType(),
            request.getRequestedDiscountAmt(),
            ApprovalStatus.APPROVED,
            LocalDateTime.now(clock)
        ));
    }

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

    @Override
    public void onRequestEscalated(ApprovalRequest request, String escalationTarget) {}

    public double getApprovedRevenueDelta() {
        return records.stream()
            .filter(r -> r.finalStatus() == ApprovalStatus.APPROVED)
            .mapToDouble(ProfitabilityEntry::discountAmount)
            .sum();
    }

    public double getRejectedSavings() {
        return records.stream()
            .filter(r -> r.finalStatus() == ApprovalStatus.REJECTED)
            .mapToDouble(ProfitabilityEntry::discountAmount)
            .sum();
    }

    public Map<ApprovalRequestType, DoubleSummaryStatistics> getBreakdownByType() {
        return records.stream().collect(
            Collectors.groupingBy(
                ProfitabilityEntry::requestType,
                Collectors.summarizingDouble(ProfitabilityEntry::discountAmount)
            )
        );
    }

    public int getTotalDecisions() {
        return records.size();
    }

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
