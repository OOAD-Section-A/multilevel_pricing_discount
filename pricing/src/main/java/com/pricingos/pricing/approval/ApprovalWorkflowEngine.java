package com.pricingos.pricing.approval;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;
import com.pricingos.common.IApprovalWorkflowService;
import com.pricingos.common.IApproverRoleService;
import com.pricingos.common.IFloorPriceService;
import com.pricingos.common.ValidationUtils;
import com.scm.subsystems.MultiLevelPricingSubsystem;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ApprovalWorkflowEngine implements IApprovalWorkflowService {

    private static final long ESCALATION_THRESHOLD_HOURS = 48L;
    private static final long AUTO_REJECT_THRESHOLD_HOURS = 48L;

    private final ApprovalRoutingStrategy routingStrategy;
    private final IApproverRoleService approverRoleService;
    private final Clock clock;
    private final List<ApprovalEventObserver> observers = new CopyOnWriteArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger((int)(Math.random() * 1000000));

    public ApprovalWorkflowEngine(ApprovalRoutingStrategy routingStrategy,
                                  IApproverRoleService approverRoleService) {
        this(routingStrategy, approverRoleService, Clock.systemDefaultZone());
    }

    ApprovalWorkflowEngine(ApprovalRoutingStrategy routingStrategy,
                           IApproverRoleService approverRoleService, Clock clock) {
        this.routingStrategy = Objects.requireNonNull(routingStrategy, "routingStrategy cannot be null");
        this.approverRoleService = Objects.requireNonNull(approverRoleService, "approverRoleService cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public ApprovalWorkflowEngine withFloorPriceService(IFloorPriceService floorPriceService) {
        this.floorPriceService = floorPriceService;
        return this;
    }

    private volatile IFloorPriceService floorPriceService;

    public void addObserver(ApprovalEventObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer cannot be null"));
    }

    public void removeObserver(ApprovalEventObserver observer) {
        observers.remove(observer);
    }

    @Override
    public String submitOverrideRequest(String requestedBy, ApprovalRequestType requestType,
                                        String orderId, double requestedDiscountAmt,
                                        String justificationText) {
        String approvalId = "APR-" + idCounter.incrementAndGet();
        ApprovalRequest request = new ApprovalRequest(
            approvalId, requestType, requestedBy, orderId, requestedDiscountAmt, justificationText, clock
        );

        String targetApproverId = routingStrategy.resolveApproverId(request);
        if (targetApproverId == null || targetApproverId.isBlank()) {
            throw new IllegalStateException("Routing strategy returned a blank approver ID for request " + approvalId);
        }
        request.setRoutedToApproverId(targetApproverId);
        ApprovalRequestDao.save(request);
        notifySubmitted(request, targetApproverId);
        return approvalId;
    }

    @Override
    public void approve(String approvalId, String approverId) {
        ApprovalRequest request = getRequest(approvalId);
        if (!approverRoleService.canApprove(approverId, request.getRequestType(), request.getRequestedDiscountAmt())) {
            throw new IllegalArgumentException("Approver [" + approverId + "] does not have authority to approve this request.");
        }

        IFloorPriceService localFloorService = floorPriceService;
        if (localFloorService != null
                && localFloorService.wouldViolateMargin(request.getOrderId(), request.getRequestedDiscountAmt())) {
            double floorPrice = localFloorService.getEffectiveFloorPrice(request.getOrderId());
            notifyMarginViolation(request, floorPrice);
            throw new MarginViolationException(request.getOrderId(), floorPrice, request.getRequestedDiscountAmt());
        }

        request.markAsApproved(approverId);
        ApprovalRequestDao.save(request);
        notifyApproved(request);
    }

    @Override
    public void reject(String approvalId, String approverId, String reason) {
        ValidationUtils.requireNonBlank(reason, "reason");
        ApprovalRequest request = getRequest(approvalId);

        if (!approverRoleService.canApprove(approverId, request.getRequestType(), request.getRequestedDiscountAmt())) {
            throw new IllegalArgumentException("Approver [" + approverId + "] does not have authority to reject this request.");
        }

        request.markAsRejected(approverId, reason);
        ApprovalRequestDao.save(request);
        notifyRejected(request);
    }

    @Override
    public List<String> getPendingApprovals(String approverId) {
        ValidationUtils.requireNonBlank(approverId, "approverId");
        return ApprovalRequestDao.findAll(clock).stream()
            .filter(r -> approverId.equals(r.getRoutedToApproverId()))
            .filter(r -> r.getStatus() == ApprovalStatus.PENDING || r.getStatus() == ApprovalStatus.ESCALATED)
            .map(ApprovalRequest::getApprovalId)
            .sorted()
            .collect(Collectors.toList());
    }

    @Override
    public void escalateStaleRequests() {
        List<ApprovalRequest> toEscalate = new ArrayList<>();
        List<ApprovalRequest> toAutoReject = new ArrayList<>();

        for (ApprovalRequest request : ApprovalRequestDao.findAll(clock)) {
            ApprovalStatus status = request.getStatus();
            if (status == ApprovalStatus.PENDING && request.getPendingHours() >= ESCALATION_THRESHOLD_HOURS) {
                toEscalate.add(request);
            } else if (status == ApprovalStatus.ESCALATED && request.getEscalatedHours() >= AUTO_REJECT_THRESHOLD_HOURS) {
                toAutoReject.add(request);
            }
        }

        for (ApprovalRequest request : toEscalate) {
            if (request.getStatus() != ApprovalStatus.PENDING) {
                continue;
            }
            try {
                request.markAsEscalated();
            } catch (IllegalStateException ignored) {
                continue;
            }

            String currentApprover = request.getRoutedToApproverId();
            String escalationTarget = approverRoleService.getEscalationManagerId(currentApprover);
            if (escalationTarget == null) {
                escalationTarget = "REGIONAL_MANAGER";
            }
            request.setRoutedToApproverId(escalationTarget);
            try {
                MultiLevelPricingSubsystem.INSTANCE.onApprovalEscalationTimeout(request.getApprovalId(), request.getPendingHours() * 3600000);
            } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
                // Database not available during tests
            }
            ApprovalRequestDao.save(request);
            notifyEscalated(request, escalationTarget);
        }

        for (ApprovalRequest request : toAutoReject) {
            if (request.getStatus() != ApprovalStatus.ESCALATED) {
                continue;
            }
            try {
                request.markAsRejected(
                    "SYSTEM",
                    "Auto-rejected: no manager action within SLA window. Transaction reverted to standard pricing."
                );
            } catch (IllegalStateException ignored) {
                continue;
            }
            ApprovalRequestDao.save(request);
            notifyRejected(request);
        }
    }

    @Override
    public ApprovalStatus getStatus(String approvalId) {
        return getRequest(approvalId).getStatus();
    }

    ApprovalRequest getRequestById(String approvalId) {
        return getRequest(approvalId);
    }

    private void notifySubmitted(ApprovalRequest request, String approverId) {
        for (ApprovalEventObserver observer : observers) {
            observer.onRequestSubmitted(request, approverId);
        }
    }

    private void notifyApproved(ApprovalRequest request) {
        for (ApprovalEventObserver observer : observers) {
            observer.onRequestApproved(request);
        }
    }

    private void notifyRejected(ApprovalRequest request) {
        for (ApprovalEventObserver observer : observers) {
            observer.onRequestRejected(request);
        }
    }

    private void notifyEscalated(ApprovalRequest request, String escalationTarget) {
        for (ApprovalEventObserver observer : observers) {
            observer.onRequestEscalated(request, escalationTarget);
        }
    }

    private void notifyMarginViolation(ApprovalRequest request, double floorPrice) {
        for (ApprovalEventObserver observer : observers) {
            observer.onMarginViolationBlocked(request, floorPrice);
        }
    }

    private ApprovalRequest getRequest(String approvalId) {
        String normalizedId = ValidationUtils.requireNonBlank(approvalId, "approvalId");
        ApprovalRequest request = ApprovalRequestDao.get(normalizedId, clock);
        if (request == null) {
            throw new IllegalArgumentException("No approval request found with ID: " + normalizedId);
        }
        return request;
    }
}
