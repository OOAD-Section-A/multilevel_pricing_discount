package com.pricingos.pricing.demo;

import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PriceList;
import com.jackfruit.scm.database.model.PricingModels;
import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;
import com.pricingos.pricing.approval.ApprovalRequest;
import com.pricingos.pricing.approval.ApprovalRequestDao;
import com.pricingos.pricing.approval.ApprovalWorkflowEngine;
import com.pricingos.pricing.approval.AuditLogObserver;
import com.pricingos.pricing.approval.ProfitabilityAnalyticsObserver;
import com.pricingos.pricing.promotion.PromotionManager;
import com.pricingos.pricing.promotion.RebateProgramManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class PricingDemoDataSeeder {

    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final PricingAdapter pricingAdapter;
    private final PromotionManager promotionManager;
    private final RebateProgramManager rebateProgramManager;
    private final ApprovalWorkflowEngine approvalEngine;
    private final ProfitabilityAnalyticsObserver analyticsObserver;
    private final AuditLogObserver auditLogObserver;
    private final String requestedBy;
    private final String approverId;
    private final String rejectionReason;
    private final Consumer<String> log;

    private boolean replayedExistingApprovals;

    public PricingDemoDataSeeder(
            PricingAdapter pricingAdapter,
            PromotionManager promotionManager,
            RebateProgramManager rebateProgramManager,
            ApprovalWorkflowEngine approvalEngine,
            ProfitabilityAnalyticsObserver analyticsObserver,
            AuditLogObserver auditLogObserver,
            String requestedBy,
            String approverId,
            String rejectionReason,
            Consumer<String> log) {
        this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
        this.promotionManager = Objects.requireNonNull(promotionManager, "promotionManager cannot be null");
        this.rebateProgramManager = Objects.requireNonNull(rebateProgramManager, "rebateProgramManager cannot be null");
        this.approvalEngine = Objects.requireNonNull(approvalEngine, "approvalEngine cannot be null");
        this.analyticsObserver = Objects.requireNonNull(analyticsObserver, "analyticsObserver cannot be null");
        this.auditLogObserver = Objects.requireNonNull(auditLogObserver, "auditLogObserver cannot be null");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy cannot be null");
        this.approverId = Objects.requireNonNull(approverId, "approverId cannot be null");
        this.rejectionReason = Objects.requireNonNull(rejectionReason, "rejectionReason cannot be null");
        this.log = Objects.requireNonNull(log, "log cannot be null");
    }

    public SeedReport seed() {
        int pricesCreated = seedPrices();
        int tiersCreated = seedTiers();
        int promotionsCreated = seedPromotions();
        RebateSeedResult rebateResult = seedRebates();
        ApprovalSeedResult approvalResult = seedApprovals();

        SeedReport report = new SeedReport(
                pricesCreated,
                tiersCreated,
                promotionsCreated,
                rebateResult.created(),
                rebateResult.adjusted(),
                approvalResult.created(),
                approvalResult.replayed());
        log.accept(report.summary());
        return report;
    }

    private int seedPrices() {
        int created = 0;
        List<PriceList> activePrices = pricingAdapter.getActivePrices();
        for (PriceSeed seed : priceSeeds()) {
            if (hasActivePrice(activePrices, seed)) {
                continue;
            }
            pricingAdapter.publishPrice(new PriceList(
                    "DEMO-PRICE-" + UUID.randomUUID(),
                    seed.skuId(),
                    seed.regionCode(),
                    seed.channel(),
                    seed.priceType(),
                    BigDecimal.valueOf(seed.basePrice()),
                    BigDecimal.valueOf(seed.floorPrice()),
                    seed.currencyCode(),
                    LocalDateTime.now().minusDays(1),
                    FAR_FUTURE,
                    "ACTIVE"));
            created++;
            log.accept("Seeded demo price for SKU " + seed.skuId());
        }
        return created;
    }

    private int seedTiers() {
        int created = 0;
        List<PricingModels.TierDefinition> tiers = pricingAdapter.listAllTierDefinitions();
        int nextTierId = tiers.stream()
                .map(PricingModels.TierDefinition::tierId)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        for (TierSeed seed : tierSeeds()) {
            boolean exists = tiers.stream()
                    .anyMatch(tier -> seed.tierName().equalsIgnoreCase(tier.tierName()));
            if (exists) {
                continue;
            }
            pricingAdapter.createTierDefinition(new PricingModels.TierDefinition(
                    nextTierId++,
                    seed.tierName(),
                    BigDecimal.valueOf(seed.minSpendThreshold()),
                    BigDecimal.valueOf(seed.defaultDiscountPct())));
            created++;
            log.accept("Seeded demo tier " + seed.tierName());
        }
        return created;
    }

    private int seedPromotions() {
        int created = 0;
        LocalDate today = LocalDate.now();
        for (PromotionSeed seed : promotionSeeds()) {
            if (pricingAdapter.getPromotionByCouponCode(seed.couponCode()).isPresent()) {
                continue;
            }
            promotionManager.createPromotion(
                    seed.name(),
                    seed.couponCode(),
                    seed.discountType(),
                    seed.discountValue(),
                    today.minusDays(seed.startsDaysAgo()),
                    today.plusDays(seed.endsInDays()),
                    seed.eligibleSkuIds(),
                    seed.minCartValue(),
                    seed.maxUses());
            created++;
            log.accept("Seeded demo promotion " + seed.couponCode());
        }
        return created;
    }

    private RebateSeedResult seedRebates() {
        int created = 0;
        int adjusted = 0;

        for (RebateSeed seed : rebateSeeds()) {
            List<PricingModels.RebateProgram> customerPrograms =
                    pricingAdapter.listRebateProgramsByCustomer(seed.customerId());
            PricingModels.RebateProgram program = customerPrograms.stream()
                    .filter(candidate -> seed.customerId().equals(candidate.customerId()))
                    .filter(candidate -> seed.skuId().equals(candidate.skuId()))
                    .findFirst()
                    .orElse(null);

            if (program == null) {
                String programId = rebateProgramManager.createRebateProgram(
                        seed.customerId(),
                        seed.skuId(),
                        seed.targetSpend(),
                        seed.rebatePercent());
                program = pricingAdapter.getRebateProgram(programId)
                        .orElseThrow(() -> new IllegalStateException("Seeded rebate program not found: " + programId));
                created++;
                log.accept("Seeded demo rebate program for customer " + seed.customerId()
                        + " and SKU " + seed.skuId());
            }

            BigDecimal desiredAccumulatedSpend = BigDecimal.valueOf(seed.accumulatedSpend());
            if (program.accumulatedSpend().compareTo(desiredAccumulatedSpend) < 0) {
                pricingAdapter.updateRebateAccumulatedSpend(program.programId(), desiredAccumulatedSpend);
                adjusted++;
                log.accept("Adjusted demo rebate spend for program " + program.programId());
            }
        }

        return new RebateSeedResult(created, adjusted);
    }

    private ApprovalSeedResult seedApprovals() {
        int created = 0;
        int replayed = 0;
        List<ApprovalRequest> existingRequests = ApprovalRequestDao.findAll(null);

        for (ApprovalSeed seed : approvalSeeds()) {
            ApprovalRequest matchingRequest = findApproval(existingRequests, seed);
            if (matchingRequest == null) {
                String approvalId = approvalEngine.submitOverrideRequest(
                        requestedBy,
                        ApprovalRequestType.MANUAL_DISCOUNT,
                        pricingReference(seed.skuId()),
                        seed.discountAmount(),
                        seed.justification());
                matchingRequest = approvalEngine.getRequestById(approvalId);
                if (seed.status() == ApprovalStatus.APPROVED) {
                    approvalEngine.approve(approvalId, approverId);
                    matchingRequest = approvalEngine.getRequestById(approvalId);
                } else if (seed.status() == ApprovalStatus.REJECTED) {
                    approvalEngine.reject(approvalId, approverId, rejectionReason);
                    matchingRequest = approvalEngine.getRequestById(approvalId);
                }
                existingRequests.add(matchingRequest);
                created++;
                log.accept("Seeded demo approval request for " + seed.skuId()
                        + " with status " + matchingRequest.getStatus());
                continue;
            }

            if (!replayedExistingApprovals) {
                replayApprovalState(matchingRequest);
                replayed++;
            }
        }

        replayedExistingApprovals = true;
        return new ApprovalSeedResult(created, replayed);
    }

    private ApprovalRequest findApproval(List<ApprovalRequest> existingRequests, ApprovalSeed seed) {
        return existingRequests.stream()
                .filter(request -> requestedBy.equals(request.getRequestedBy()))
                .filter(request -> pricingReference(seed.skuId()).equals(request.getOrderId()))
                .filter(request -> request.getStatus() == seed.status())
                .findFirst()
                .orElse(null);
    }

    private void replayApprovalState(ApprovalRequest request) {
        String routedApprover = request.getRoutedToApproverId();
        if (routedApprover == null || routedApprover.isBlank()) {
            routedApprover = approverId;
        }

        auditLogObserver.onRequestSubmitted(request, routedApprover);
        if (request.getStatus() == ApprovalStatus.APPROVED) {
            auditLogObserver.onRequestApproved(request);
            analyticsObserver.onRequestApproved(request);
        } else if (request.getStatus() == ApprovalStatus.REJECTED) {
            auditLogObserver.onRequestRejected(request);
            analyticsObserver.onRequestRejected(request);
        } else if (request.getStatus() == ApprovalStatus.ESCALATED) {
            auditLogObserver.onRequestEscalated(request, routedApprover);
        }
    }

    private boolean hasActivePrice(List<PriceList> activePrices, PriceSeed seed) {
        return activePrices.stream()
                .filter(price -> seed.skuId().equals(price.getSkuId()))
                .filter(price -> seed.regionCode().equals(price.getRegionCode()))
                .filter(price -> seed.channel().equals(price.getChannel()))
                .filter(price -> seed.priceType().equals(price.getPriceType()))
                .anyMatch(price -> "ACTIVE".equalsIgnoreCase(price.getStatus()));
    }

    private String pricingReference(String skuId) {
        return "SKU:" + skuId;
    }

    private List<PriceSeed> priceSeeds() {
        return List.of(
                new PriceSeed("DEMO-SKU-APPLE", "GLOBAL", "RETAIL", "RETAIL", 120.00, 95.00, "INR"),
                new PriceSeed("DEMO-SKU-BANANA", "GLOBAL", "RETAIL", "RETAIL", 85.00, 65.00, "INR"),
                new PriceSeed("DEMO-SKU-COFFEE", "GLOBAL", "RETAIL", "RETAIL", 240.00, 200.00, "INR"));
    }

    private List<TierSeed> tierSeeds() {
        return List.of(
                new TierSeed("Demo Starter", 250.00, 5.00),
                new TierSeed("Demo Growth", 750.00, 10.00),
                new TierSeed("Demo Enterprise", 1500.00, 15.00));
    }

    private List<PromotionSeed> promotionSeeds() {
        return List.of(
                new PromotionSeed(
                        "Demo 10 Percent Off",
                        "DEMO10",
                        com.pricingos.common.DiscountType.PERCENTAGE_OFF,
                        10.0,
                        List.of("DEMO-SKU-APPLE", "DEMO-SKU-BANANA"),
                        100.0,
                        500,
                        7,
                        90),
                new PromotionSeed(
                        "Demo Fixed Discount",
                        "DEMO20",
                        com.pricingos.common.DiscountType.FIXED_AMOUNT,
                        20.0,
                        List.of("DEMO-SKU-COFFEE"),
                        200.0,
                        200,
                        7,
                        90));
    }

    private List<RebateSeed> rebateSeeds() {
        return List.of(
                new RebateSeed("DEMO-CUST-001", "DEMO-SKU-APPLE", 1000.0, 5.0, 450.0),
                new RebateSeed("DEMO-CUST-001", "DEMO-SKU-COFFEE", 1500.0, 7.5, 1800.0));
    }

    private List<ApprovalSeed> approvalSeeds() {
        return List.of(
                new ApprovalSeed("DEMO-SKU-APPLE", 12.0, ApprovalStatus.PENDING,
                        "Seeded pending approval for workflow testing."),
                new ApprovalSeed("DEMO-SKU-BANANA", 10.0, ApprovalStatus.APPROVED,
                        "Seeded approved override for analytics testing."),
                new ApprovalSeed("DEMO-SKU-COFFEE", 15.0, ApprovalStatus.REJECTED,
                        "Seeded rejected override for analytics testing."));
    }

    private record PriceSeed(
            String skuId,
            String regionCode,
            String channel,
            String priceType,
            double basePrice,
            double floorPrice,
            String currencyCode) {
    }

    private record TierSeed(String tierName, double minSpendThreshold, double defaultDiscountPct) {
    }

    private record PromotionSeed(
            String name,
            String couponCode,
            com.pricingos.common.DiscountType discountType,
            double discountValue,
            List<String> eligibleSkuIds,
            double minCartValue,
            int maxUses,
            int startsDaysAgo,
            int endsInDays) {
    }

    private record RebateSeed(
            String customerId,
            String skuId,
            double targetSpend,
            double rebatePercent,
            double accumulatedSpend) {
    }

    private record ApprovalSeed(
            String skuId,
            double discountAmount,
            ApprovalStatus status,
            String justification) {
    }

    private record RebateSeedResult(int created, int adjusted) {
    }

    private record ApprovalSeedResult(int created, int replayed) {
    }

    public record SeedReport(
            int pricesCreated,
            int tiersCreated,
            int promotionsCreated,
            int rebateProgramsCreated,
            int rebateProgramsAdjusted,
            int approvalsCreated,
            int approvalStatesReplayed) {

        public String summary() {
            return "Demo data seed complete: prices=" + pricesCreated
                    + ", tiers=" + tiersCreated
                    + ", promotions=" + promotionsCreated
                    + ", rebatePrograms=" + rebateProgramsCreated
                    + ", rebateAdjustments=" + rebateProgramsAdjusted
                    + ", approvals=" + approvalsCreated
                    + ", approvalReplays=" + approvalStatesReplayed;
        }
    }
}
