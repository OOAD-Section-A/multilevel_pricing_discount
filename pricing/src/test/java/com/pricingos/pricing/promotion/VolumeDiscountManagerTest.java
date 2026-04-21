package com.pricingos.pricing.promotion;

import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.VolumeTierRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Component 5 — Volume &amp; Tiered Discounts feature.
 *
 * <p>Covers: tier creation, correct tier selection at boundaries, unlimited top tier,
 * unknown SKU, fallback when no promo exists, line total calculation.
 */
class VolumeDiscountManagerTest {

    /** Stub catalog: only SKU-001 is active. */
    private static final ISkuCatalogService STUB_CATALOG = new ISkuCatalogService() {
        @Override public boolean isSkuActive(String skuId) { return skuId.equals("SKU-001"); }
        @Override public List<String> getAllActiveSkuIds()  { return List.of("SKU-001"); }
    };

    /**
     * Standard three-tier schedule used across most tests:
     * Tier 1: qty 1–100   → 0% off  (full price $10)
     * Tier 2: qty 101–500 → 10% off ($9 per unit)
     * Tier 3: qty 501+    → 20% off ($8 per unit) [unlimited]
     */
    private static final List<VolumeTierRule> STANDARD_TIERS = List.of(
        new VolumeTierRule(1,   100, 0.0),
        new VolumeTierRule(101, 500, 10.0),
        new VolumeTierRule(501, 0,   20.0)   // maxQty = 0 → unlimited
    );

    private VolumeDiscountManager manager;

    
    @org.junit.jupiter.api.AfterEach
    void clearDaoBulk() {
        com.pricingos.pricing.db.DaoBulk.clearAll();
    }

    @BeforeEach
    void setUp() {
        manager = new VolumeDiscountManager(
            STUB_CATALOG,
            new VolumeDiscountManager.InMemoryVolumeStore()
        );
    }

    // ── Create volume promotion ───────────────────────────────────────────────────

    @Test
    void createVolumePromotion_returnsId() {
        String id = manager.createVolumePromotion("SKU-001", STANDARD_TIERS);
        assertNotNull(id);
        assertTrue(id.startsWith("VOL-"));
    }

    @Test
    void createVolumePromotion_unknownSku_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createVolumePromotion("SKU-999", STANDARD_TIERS));
    }

    @Test
    void createVolumePromotion_emptyTiers_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createVolumePromotion("SKU-001", List.of()));
    }

    @Test
    void createVolumePromotion_tiersWithGap_throws() {
        // Tier 1 ends at 100, Tier 2 starts at 102 — gap at 101
        List<VolumeTierRule> gapTiers = List.of(
            new VolumeTierRule(1,   100, 0.0),
            new VolumeTierRule(102, 0,   10.0)
        );
        assertThrows(IllegalArgumentException.class, () ->
            manager.createVolumePromotion("SKU-001", gapTiers));
    }

    @Test
    void createVolumePromotion_noUnlimitedTopTier_throws() {
        // Both tiers have explicit maxQty — missing the open-ended top tier
        List<VolumeTierRule> noUnlimited = List.of(
            new VolumeTierRule(1,   100, 0.0),
            new VolumeTierRule(101, 500, 10.0)
        );
        assertThrows(IllegalArgumentException.class, () ->
            manager.createVolumePromotion("SKU-001", noUnlimited));
    }

    // ── getDiscountedUnitPrice ────────────────────────────────────────────────────

    @Test
    void getDiscountedUnitPrice_tier1_fullPrice() {
        manager.createVolumePromotion("SKU-001", STANDARD_TIERS);
        // qty=50 → Tier 1 (0% off) → $10.00
        assertEquals(10.0, manager.getDiscountedUnitPrice("SKU-001", 50, 10.0), 0.001);
    }

    @Test
    void getDiscountedUnitPrice_tier2_tenPercentOff() {
        manager.createVolumePromotion("SKU-001", STANDARD_TIERS);
        // qty=200 → Tier 2 (10% off) → $9.00
        assertEquals(9.0, manager.getDiscountedUnitPrice("SKU-001", 200, 10.0), 0.001);
    }

    @Test
    void getDiscountedUnitPrice_tier3_twentyPercentOff() {
        manager.createVolumePromotion("SKU-001", STANDARD_TIERS);
        // qty=1000 → Tier 3 (20% off) → $8.00
        assertEquals(8.0, manager.getDiscountedUnitPrice("SKU-001", 1000, 10.0), 0.001);
    }

    @Test
    void getDiscountedUnitPrice_exactBoundary_tier1ToTier2() {
        manager.createVolumePromotion("SKU-001", STANDARD_TIERS);
        // qty=100 → still Tier 1 (0% off); qty=101 → Tier 2 (10% off)
        assertEquals(10.0, manager.getDiscountedUnitPrice("SKU-001", 100,  10.0), 0.001);
        assertEquals(9.0,  manager.getDiscountedUnitPrice("SKU-001", 101, 10.0), 0.001);
    }

    @Test
    void getDiscountedUnitPrice_noPromoForSku_returnsBasePrice() {
        // No promo registered for SKU-001 in this test → full price returned
        assertEquals(10.0, manager.getDiscountedUnitPrice("SKU-001", 200, 10.0), 0.001);
    }

    // ── getLineTotal ──────────────────────────────────────────────────────────────

    @Test
    void getLineTotal_tier2_correctTotal() {
        manager.createVolumePromotion("SKU-001", STANDARD_TIERS);
        // qty=200, price=$10, Tier 2 = $9/unit → 200 × $9 = $1800
        assertEquals(1800.0, manager.getLineTotal("SKU-001", 200, 10.0), 0.001);
    }

    // ── hasVolumePromotion ────────────────────────────────────────────────────────

    @Test
    void hasVolumePromotion_afterCreate_returnsTrue() {
        manager.createVolumePromotion("SKU-001", STANDARD_TIERS);
        assertTrue(manager.hasVolumePromotion("SKU-001"));
    }

    @Test
    void hasVolumePromotion_beforeCreate_returnsFalse() {
        assertFalse(manager.hasVolumePromotion("SKU-001"));
    }
}
