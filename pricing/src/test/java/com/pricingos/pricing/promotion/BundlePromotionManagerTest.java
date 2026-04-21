package com.pricingos.pricing.promotion;

import com.pricingos.common.ISkuCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Component 5 — Bundled Pricing feature.
 *
 * <p>Covers: bundle creation, full-bundle discount, partial-bundle (no discount),
 * expired bundle, unknown SKU rejection, and multi-bundle selection (best deal).
 */
class BundlePromotionManagerTest {

    private static final LocalDate TODAY  = LocalDate.of(2024, 6, 15);
    private static final LocalDate PAST   = TODAY.minusDays(1);
    private static final LocalDate FUTURE = TODAY.plusDays(30);

    private static final Clock FIXED_CLOCK =
        Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    /** Stub catalog: SKU-001, SKU-002, SKU-003 are active. */
    private static final ISkuCatalogService STUB_CATALOG = new ISkuCatalogService() {
        @Override public boolean isSkuActive(String skuId) {
            return skuId.equals("SKU-001") || skuId.equals("SKU-002") || skuId.equals("SKU-003");
        }
        @Override public List<String> getAllActiveSkuIds() {
            return List.of("SKU-001", "SKU-002", "SKU-003");
        }
    };

    private BundlePromotionManager manager;

    
    @org.junit.jupiter.api.AfterEach
    void clearDaoBulk() {
        com.pricingos.pricing.db.DaoBulk.clearAll();
    }

    @BeforeEach
    void setUp() {
        manager = new BundlePromotionManager(
            STUB_CATALOG,
            FIXED_CLOCK,
            new BundlePromotionManager.InMemoryBundleStore()
        );
    }

    // ── Create bundle ─────────────────────────────────────────────────────────────

    @Test
    void createBundlePromotion_returnsId() {
        String id = manager.createBundlePromotion(
            "Printer Bundle", List.of("SKU-001", "SKU-002"), 15.0, TODAY, FUTURE);
        assertNotNull(id);
        assertTrue(id.startsWith("BNDL-"));
    }

    @Test
    void createBundlePromotion_unknownSku_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createBundlePromotion("Bad", List.of("SKU-001", "SKU-999"), 10.0, TODAY, FUTURE));
    }

    @Test
    void createBundlePromotion_emptySkuList_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createBundlePromotion("Empty", List.of(), 10.0, TODAY, FUTURE));
    }

    @Test
    void createBundlePromotion_endBeforeStart_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createBundlePromotion("Bad Dates", List.of("SKU-001"), 10.0, FUTURE, TODAY));
    }

    // ── getBestBundleDiscount ─────────────────────────────────────────────────────

    @Test
    void getBestBundleDiscount_allSkusPresent_returnsCorrectDiscount() {
        manager.createBundlePromotion(
            "Printer Bundle", List.of("SKU-001", "SKU-002"), 15.0, TODAY, FUTURE);
        // 15% of 1000 = 150
        double discount = manager.getBestBundleDiscount(List.of("SKU-001", "SKU-002"), 1000.0);
        assertEquals(150.0, discount, 0.001);
    }

    @Test
    void getBestBundleDiscount_missingOneSku_returnsZero() {
        manager.createBundlePromotion(
            "Printer Bundle", List.of("SKU-001", "SKU-002"), 15.0, TODAY, FUTURE);
        // Cart has only SKU-001, not SKU-002 → no bundle
        double discount = manager.getBestBundleDiscount(List.of("SKU-001", "SKU-003"), 500.0);
        assertEquals(0.0, discount, 0.001);
    }

    @Test
    void getBestBundleDiscount_expiredBundle_returnsZero() {
        // Bundle ended yesterday
        manager.createBundlePromotion(
            "Old Bundle", List.of("SKU-001", "SKU-002"), 20.0,
            PAST.minusDays(10), PAST);
        double discount = manager.getBestBundleDiscount(List.of("SKU-001", "SKU-002"), 1000.0);
        assertEquals(0.0, discount, 0.001);
    }

    @Test
    void getBestBundleDiscount_noBundles_returnsZero() {
        double discount = manager.getBestBundleDiscount(List.of("SKU-001", "SKU-002"), 500.0);
        assertEquals(0.0, discount, 0.001);
    }

    @Test
    void getBestBundleDiscount_multipleBundles_returnsBestDiscount() {
        // Bundle A: SKU-001 + SKU-002 → 10%
        manager.createBundlePromotion("Bundle A", List.of("SKU-001", "SKU-002"), 10.0, TODAY, FUTURE);
        // Bundle B: SKU-001 + SKU-002 + SKU-003 → 20% (better deal)
        manager.createBundlePromotion("Bundle B", List.of("SKU-001", "SKU-002", "SKU-003"), 20.0, TODAY, FUTURE);

        // Cart contains all 3 SKUs → both bundles apply → best = 20%
        double discount = manager.getBestBundleDiscount(List.of("SKU-001", "SKU-002", "SKU-003"), 1000.0);
        assertEquals(200.0, discount, 0.001);
    }

    @Test
    void getBestBundleDiscount_skuIdCaseAndWhitespaceTrimmed() {
        manager.createBundlePromotion("Bundle", List.of("SKU-001", "SKU-002"), 10.0, TODAY, FUTURE);
        // SKU IDs with leading/trailing spaces should still match
        double discount = manager.getBestBundleDiscount(List.of(" SKU-001 ", " SKU-002 "), 500.0);
        assertEquals(50.0, discount, 0.001);
    }

    // ── getActiveBundlePromotions ────────────────────────────────────────────────

    @Test
    void getActiveBundlePromotions_returnsOnlyCurrentlyActive() {
        String active = manager.createBundlePromotion(
            "Active", List.of("SKU-001"), 10.0, TODAY, FUTURE);
        manager.createBundlePromotion(
            "Expired", List.of("SKU-002"), 5.0, PAST.minusDays(5), PAST);

        List<String> actives = manager.getActiveBundlePromotions();
        assertTrue(actives.contains(active));
        assertEquals(1, actives.size());
    }
}
