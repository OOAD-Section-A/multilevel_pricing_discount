package com.pricingos.pricing.approval;

import com.jackfruit.scm.database.model.PricingModels.PriceApproval;
import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;
import com.pricingos.pricing.db.DatabaseModuleSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ApprovalRequestDao {

    interface Store {
        ApprovalRequest get(String id, Clock clock);
        List<ApprovalRequest> findAll(Clock clock);
        void save(ApprovalRequest request);
        void clear();
    }

    private static volatile Store store = new AdapterStore();

    private ApprovalRequestDao() {
    }

    public static ApprovalRequest get(String id, Clock testClock) {
        return store.get(id, testClock);
    }

    public static List<ApprovalRequest> findAll(Clock testClock) {
        return store.findAll(testClock);
    }

    public static void save(ApprovalRequest request) {
        store.save(request);
    }

    public static void useInMemoryStoreForTests() {
        store = new InMemoryStore();
    }

    public static void clearStore() {
        store.clear();
    }

    public static void resetJdbcStore() {
        store = new AdapterStore();
    }

    private static final class AdapterStore implements Store {
        private final Map<String, TransientState> transientStateById = new ConcurrentHashMap<>();

        @Override
        public ApprovalRequest get(String id, Clock clock) {
            return DatabaseModuleSupport.withPricingAdapter(adapter ->
                    adapter.getPriceApproval(id)
                            .map(approval -> map(approval, clock, transientStateById.get(id)))
                            .orElse(null));
        }

        @Override
        public List<ApprovalRequest> findAll(Clock clock) {
            return DatabaseModuleSupport.withPricingAdapter(adapter -> {
                Map<String, PriceApproval> approvalsById = new LinkedHashMap<>();
                collectApprovals(approvalsById, adapter.listPendingApprovals());
                collectApprovals(approvalsById, adapter.listApprovalsByStatus(ApprovalStatus.ESCALATED.name()));
                collectApprovals(approvalsById, adapter.listApprovalsByStatus(ApprovalStatus.APPROVED.name()));
                collectApprovals(approvalsById, adapter.listApprovalsByStatus(ApprovalStatus.REJECTED.name()));

                return approvalsById.values().stream()
                        .map(approval -> map(approval, clock, transientStateById.get(approval.approvalId())))
                        .sorted(Comparator.comparing(ApprovalRequest::getSubmissionTime)
                                .thenComparing(ApprovalRequest::getApprovalId))
                        .toList();
            });
        }

        @Override
        public void save(ApprovalRequest request) {
            DatabaseModuleSupport.usePricingAdapter(adapter -> {
                Optional<PriceApproval> existing = adapter.getPriceApproval(request.getApprovalId());
                if (existing.isPresent()) {
                    String assignee = currentAssignee(request);
                    if (assignee != null && !assignee.isBlank()) {
                        adapter.updatePriceApprovalManager(request.getApprovalId(), assignee);
                    }
                    if (!request.getStatus().name().equals(existing.get().approvalStatus())) {
                        adapter.updatePriceApprovalStatus(request.getApprovalId(), request.getStatus().name());
                    }
                } else {
                    adapter.createPriceApproval(toPriceApproval(request));
                }
            });
            transientStateById.put(request.getApprovalId(), TransientState.from(request));
        }

        @Override
        public void clear() {
            transientStateById.clear();
        }

        private void collectApprovals(Map<String, PriceApproval> approvalsById, List<PriceApproval> approvals) {
            for (PriceApproval approval : approvals) {
                approvalsById.putIfAbsent(approval.approvalId(), approval);
            }
        }
    }

    private static final class InMemoryStore implements Store {
        private final Map<String, ApprovalRequest> requestsById = new ConcurrentHashMap<>();

        @Override
        public ApprovalRequest get(String id, Clock clock) {
            return requestsById.get(id);
        }

        @Override
        public List<ApprovalRequest> findAll(Clock clock) {
            return new ArrayList<>(requestsById.values());
        }

        @Override
        public void save(ApprovalRequest request) {
            requestsById.put(request.getApprovalId(), request);
        }

        @Override
        public void clear() {
            requestsById.clear();
        }
    }

    private static PriceApproval toPriceApproval(ApprovalRequest request) {
        return new PriceApproval(
                request.getApprovalId(),
                request.getRequestType().name(),
                request.getRequestedBy(),
                BigDecimal.valueOf(request.getRequestedDiscountAmt()),
                ApprovalRecordCodec.encode(request.getOrderId(), request.getJustificationText()),
                currentAssignee(request),
                request.getStatus().name(),
                resolveTimestamp(request),
                request.isAuditLogFlag(),
                request.getSubmissionTime());
    }

    private static String currentAssignee(ApprovalRequest request) {
        if (request.getStatus() == ApprovalStatus.APPROVED || request.getStatus() == ApprovalStatus.REJECTED) {
            return request.getApprovingManagerId();
        }
        return request.getRoutedToApproverId();
    }

    private static LocalDateTime resolveTimestamp(ApprovalRequest request) {
        if (request.getStatus() == ApprovalStatus.ESCALATED) {
            return request.getEscalationTime();
        }
        if (request.getStatus() == ApprovalStatus.APPROVED || request.getStatus() == ApprovalStatus.REJECTED) {
            return request.getApprovalTimestamp();
        }
        return null;
    }

    private static ApprovalRequest map(PriceApproval priceApproval, Clock clock, TransientState transientState) {
        ApprovalRecordCodec.DecodedApproval decoded = ApprovalRecordCodec.decode(priceApproval.justificationText());
        ApprovalStatus status = ApprovalStatus.valueOf(priceApproval.approvalStatus());
        String routedToApproverId = transientState == null ? null : transientState.routedToApproverId();
        if (routedToApproverId == null
                && (status == ApprovalStatus.PENDING || status == ApprovalStatus.ESCALATED)) {
            routedToApproverId = priceApproval.approvingManagerId();
        }

        String approvingManagerId = null;
        if (status == ApprovalStatus.APPROVED || status == ApprovalStatus.REJECTED) {
            approvingManagerId = priceApproval.approvingManagerId();
        } else if (transientState != null) {
            approvingManagerId = transientState.approvingManagerId();
        }

        LocalDateTime escalationTime = status == ApprovalStatus.ESCALATED ? priceApproval.approvalTimestamp() : null;
        LocalDateTime approvalTimestamp =
                (status == ApprovalStatus.APPROVED || status == ApprovalStatus.REJECTED)
                        ? priceApproval.approvalTimestamp()
                        : null;
        String rejectionReason = null;
        boolean auditLogFlag = priceApproval.auditLogFlag();
        if (transientState != null) {
            if (transientState.escalationTime() != null) {
                escalationTime = transientState.escalationTime();
            }
            if (transientState.approvalTimestamp() != null) {
                approvalTimestamp = transientState.approvalTimestamp();
            }
            rejectionReason = transientState.rejectionReason();
            auditLogFlag = transientState.auditLogFlag();
        }

        return ApprovalRequest.rehydrate(
                priceApproval.approvalId(),
                ApprovalRequestType.valueOf(priceApproval.requestType()),
                priceApproval.requestedBy(),
                decoded.orderId(),
                priceApproval.requestedDiscountAmount().doubleValue(),
                decoded.justificationText(),
                clock == null ? Clock.systemDefaultZone() : clock,
                priceApproval.createdAt(),
                status,
                routedToApproverId,
                approvingManagerId,
                approvalTimestamp,
                escalationTime,
                auditLogFlag,
                rejectionReason);
    }

    private record TransientState(
            String routedToApproverId,
            String approvingManagerId,
            LocalDateTime escalationTime,
            LocalDateTime approvalTimestamp,
            String rejectionReason,
            boolean auditLogFlag) {

        private static TransientState from(ApprovalRequest request) {
            return new TransientState(
                    request.getRoutedToApproverId(),
                    request.getApprovingManagerId(),
                    request.getEscalationTime(),
                    request.getApprovalTimestamp(),
                    request.getRejectionReason(),
                    request.isAuditLogFlag());
        }
    }

    private static final class ApprovalRecordCodec {
        private static final String PREFIX = "MLPAPPROVAL:";

        private ApprovalRecordCodec() {
        }

        private static String encode(String orderId, String justificationText) {
            return PREFIX
                    + encodePart(orderId)
                    + ":"
                    + encodePart(justificationText);
        }

        private static DecodedApproval decode(String storedText) {
            if (storedText == null || !storedText.startsWith(PREFIX)) {
                return new DecodedApproval("UNKNOWN", storedText == null ? "" : storedText);
            }

            String payload = storedText.substring(PREFIX.length());
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) {
                return new DecodedApproval("UNKNOWN", storedText);
            }
            return new DecodedApproval(decodePart(parts[0]), decodePart(parts[1]));
        }

        private static String encodePart(String value) {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private static String decodePart(String value) {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        }

        private record DecodedApproval(String orderId, String justificationText) {
        }
    }
}
