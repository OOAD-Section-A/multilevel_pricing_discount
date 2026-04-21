package com.pricingos.pricing.db;

import com.jackfruit.scm.database.model.PackagingModels.PackagingDiscountPolicy;
import com.jackfruit.scm.database.model.PricingModels;
import com.pricingos.pricing.approval.ApprovalRequestDao;
import com.pricingos.pricing.approval.AuditLogObserver.AuditEntry;
import com.pricingos.pricing.approval.ProfitabilityAnalyticsObserver.ProfitabilityEntry;
import com.pricingos.pricing.discount.DiscountPolicy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class DaoBulk {

    private DaoBulk() {
    }

    public static final class AuditLogDao {
        private static final List<AuditEntry> ENTRIES = new CopyOnWriteArrayList<>();

        private AuditLogDao() {
        }

        public static void save(AuditEntry entry) {
            ENTRIES.add(entry);
        }

        public static List<AuditEntry> findAll() {
            return new ArrayList<>(ENTRIES);
        }

        static void useInMemoryStoreForTests() {
            clearStore();
        }

        static void clearStore() {
            ENTRIES.clear();
        }
    }

    public static final class AnalyticsDao {
        private static final List<ProfitabilityEntry> ENTRIES = new CopyOnWriteArrayList<>();

        private AnalyticsDao() {
        }

        public static void save(ProfitabilityEntry entry) {
            ENTRIES.add(entry);
        }

        public static List<ProfitabilityEntry> findAll() {
            return new ArrayList<>(ENTRIES);
        }

        static void useInMemoryStoreForTests() {
            clearStore();
        }

        static void clearStore() {
            ENTRIES.clear();
        }
    }

    public static final class PolicyDao {
        private PolicyDao() {
        }

        public static void save(DiscountPolicy policy) {
            DatabaseModuleSupport.usePricingAdapter(adapter -> adapter.createDiscountPolicy(
                    new PricingModels.DiscountPolicy(
                            policy.getPolicyId(),
                            policy.getPolicyName(),
                            policy.getStackingRule(),
                            policy.getPriorityLevel(),
                            BigDecimal.valueOf(100.0),
                            30,
                            BigDecimal.ZERO,
                            policy.isActive())));
        }

        public static List<DiscountPolicy> findAll() {
            return DatabaseModuleSupport.withPackagingAdapter(packagingAdapter ->
                    packagingAdapter.listDiscountPolicies().stream()
                            .map(PolicyDao::mapPolicy)
                            .sorted(Comparator.comparingInt(DiscountPolicy::getPriorityLevel))
                            .collect(Collectors.toCollection(ArrayList::new)));
        }

        private static DiscountPolicy mapPolicy(PackagingDiscountPolicy policy) {
            return DiscountPolicy.builder(policy.policyName())
                    .policyId(policy.policyId())
                    .priorityLevel(policy.priorityLevel())
                    .stackingRule(policy.stackingRule())
                    .isActive(policy.active())
                    .build();
        }
    }

    public static void clearAll() {
        ApprovalRequestDao.useInMemoryStoreForTests();
        ApprovalRequestDao.clearStore();
        AuditLogDao.useInMemoryStoreForTests();
        AnalyticsDao.useInMemoryStoreForTests();
    }
}
