package com.pricingos.pricing.promotion;

import com.pricingos.common.DiscountType;
import com.pricingos.common.ISkuCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Component 5 — Promotion & Campaign Manager.
 *
 * <p>Covers the INVALID_PROMO_CODE exception (all Reason variants) and the
 * core promotion lifecycle: create → validate → redeem → expire.
 *
 * <p>Uses a simple stub for {@link ISkuCatalogService} (Team "Better Call Objects")
 * so the test has no external dependency.
 */
class PromotionManagerTest {

    /** Stub SKU catalog that recognises only SKU-001 and SKU-002. */
    private static final ISkuCatalogService STUB_CATALOG = new ISkuCatalogService() {
        @Override public boolean isSkuActive(String skuId) {
            return skuId.equals("SKU-001") || skuId.equals("SKU-002");
        }
        @Override public List<String> getAllActiveSkuIds() {
            return List.of("SKU-001", "SKU-002");
        }
    };

    private PromotionManager manager;

    /**
     * Fixed reference date used as "today" throughout all tests.
     * Using a fixed constant avoids midnight flakiness (where LocalDate.now() could
     * disagree between test-class initialization and production-code execution).
     */
    private static final LocalDate TODAY    = LocalDate.of(2024, 6, 15);
    private static final LocalDate PAST     = TODAY.minusDays(1);
    private static final LocalDate FUTURE   = TODAY.plusDays(30);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    /** Fixed clock matching TODAY so PromotionManager's internal date computations agree with the test dates. */
    private static final Clock FIXED_CLOCK  =
        Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    
    @org.junit.jupiter.api.AfterEach
    void clearDaoBulk() {
        // Database cleanup handled by database team's facade
        // com.pricingos.pricing.db.DaoBulk.clearAll();
    }

    @BeforeEach
    void setUp() {
        manager = new PromotionManager(STUB_CATALOG, new PricingAdapter(new SupplyChainDatabaseFacade()), FIXED_CLOCK);
    }

    // ── Create promotion ──────────────────────────────────────────────────────────

    @Test
    void createPromotion_returnsPromoId() {
        String id = createTestPromo("SAVE10", DiscountType.PERCENTAGE_OFF, 10.0, TODAY, FUTURE);
        assertNotNull(id);
        assertTrue(id.startsWith("PROMO-"));
    }

    @Test
    void createPromotion_duplicateCouponCode_throws() {
        createTestPromo("SAVE10", DiscountType.PERCENTAGE_OFF, 10.0, TODAY, FUTURE);
        assertThrows(IllegalArgumentException.class,
            () -> createTestPromo("SAVE10", DiscountType.PERCENTAGE_OFF, 5.0, TODAY, FUTURE));
    }

    @Test
    void createPromotion_unknownSku_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createPromotion("Bad Promo", "BADSKU", DiscountType.FIXED_AMOUNT, 50.0,
                TODAY, FUTURE, List.of("SKU-999"), 0, 0));
    }

    @Test
    void createPromotion_percentageOver100_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createPromotion("Crazy Promo", "CRAZY", DiscountType.PERCENTAGE_OFF, 110.0,
                TODAY, FUTURE, List.of("SKU-001"), 0, 0));
    }

    @Test
    void createPromotion_nullSkuInList_throws() {
        List<String> skusWithNull = new java.util.ArrayList<>();
        skusWithNull.add("SKU-001");
        skusWithNull.add(null);
        assertThrows(IllegalArgumentException.class, () ->
            manager.createPromotion("Bad", "NULLSKU", DiscountType.FIXED_AMOUNT, 10.0,
                TODAY, FUTURE, skusWithNull, 0, 0));
    }

    @Test
    void createPromotion_blankSkuInList_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createPromotion("Bad", "BLANKSKU", DiscountType.FIXED_AMOUNT, 10.0,
                TODAY, FUTURE, List.of("SKU-001", "  "), 0, 0));
    }

    // ── Validate coupon ───────────────────────────────────────────────────────────

    @Test
    void validateAndGetDiscount_validPercentageOff_returnsCorrectAmount() {
        createTestPromo("SAVE10", DiscountType.PERCENTAGE_OFF, 10.0, TODAY, FUTURE);
        double discount = manager.validateAndGetDiscount("SAVE10", "SKU-001", 1000.0);
        assertEquals(100.0, discount, 0.001); // 10% of 1000
    }

    @Test
    void validateAndGetDiscount_fixedAmount_returnsFixedValue() {
        manager.createPromotion("Fixed50", "FIXED50", DiscountType.FIXED_AMOUNT, 50.0,
            TODAY, FUTURE, List.of("SKU-001"), 0, 0);
        double discount = manager.validateAndGetDiscount("FIXED50", "SKU-001", 200.0);
        assertEquals(50.0, discount, 0.001);
    }

    @Test
    void validateAndGetDiscount_fixedAmountExceedsPrice_cappedAtPrice() {
        manager.createPromotion("BigFixed", "BIGFIXED", DiscountType.FIXED_AMOUNT, 500.0,
            TODAY, FUTURE, List.of("SKU-001"), 0, 0);
        double discount = manager.validateAndGetDiscount("BIGFIXED", "SKU-001", 100.0);
        assertEquals(100.0, discount, 0.001); // capped at the cart total
    }

    @Test
    void validateAndGetDiscount_couponCodeCaseInsensitive_works() {
        createTestPromo("SAVE10", DiscountType.PERCENTAGE_OFF, 10.0, TODAY, FUTURE);
        assertDoesNotThrow(() -> manager.validateAndGetDiscount("save10", "SKU-001", 500.0));
    }

    // ── INVALID_PROMO_CODE exception variants ─────────────────────────────────────

    @Test
    void validateAndGetDiscount_notYetStartedPromo_throwsNotYetActive() {
        manager.createPromotion("FuturePromo", "FUTURE10", DiscountType.PERCENTAGE_OFF, 10.0,
            TOMORROW, FUTURE, List.of("SKU-001"), 0, 0);
        InvalidPromoCodeException ex = assertThrows(InvalidPromoCodeException.class,
            () -> manager.validateAndGetDiscount("FUTURE10", "SKU-001", 100.0));
        assertEquals(InvalidPromoCodeException.Reason.NOT_YET_ACTIVE, ex.getReason());
    }

    @Test
    void validateAndGetDiscount_unknownCode_throwsNotFound() {
        InvalidPromoCodeException ex = assertThrows(InvalidPromoCodeException.class,
            () -> manager.validateAndGetDiscount("GHOST", "SKU-001", 100.0));
        assertEquals(InvalidPromoCodeException.Reason.NOT_FOUND, ex.getReason());
    }

    @Test
    void validateAndGetDiscount_expiredPromo_throwsExpired() {
        createTestPromo("OLDCODE", DiscountType.PERCENTAGE_OFF, 5.0, PAST.minusDays(5), PAST);
        InvalidPromoCodeException ex = assertThrows(InvalidPromoCodeException.class,
            () -> manager.validateAndGetDiscount("OLDCODE", "SKU-001", 100.0));
        assertEquals(InvalidPromoCodeException.Reason.EXPIRED, ex.getReason());
    }

    @Test
    void validateAndGetDiscount_wrongSku_throwsSkuNotEligible() {
        createTestPromo("SKUONLY", DiscountType.PERCENTAGE_OFF, 10.0, TODAY, FUTURE);
        InvalidPromoCodeException ex = assertThrows(InvalidPromoCodeException.class,
            () -> manager.validateAndGetDiscount("SKUONLY", "SKU-002", 100.0));
        assertEquals(InvalidPromoCodeException.Reason.SKU_NOT_ELIGIBLE, ex.getReason());
    }

    @Test
    void validateAndGetDiscount_cartBelowMinimum_throwsCartValueTooLow() {
        manager.createPromotion("MinCart", "MINCART", DiscountType.PERCENTAGE_OFF, 10.0,
            TODAY, FUTURE, List.of("SKU-001"), 500.0, 0); // min cart = 500
        InvalidPromoCodeException ex = assertThrows(InvalidPromoCodeException.class,
            () -> manager.validateAndGetDiscount("MINCART", "SKU-001", 100.0));
        assertEquals(InvalidPromoCodeException.Reason.CART_VALUE_TOO_LOW, ex.getReason());
    }

    @Test
    void validateAndGetDiscount_maxUsesReached_throwsMaxUsesReached() {
        manager.createPromotion("LimitedUse", "LIMITED", DiscountType.FIXED_AMOUNT, 20.0,
            TODAY, FUTURE, List.of("SKU-001"), 0, 2); // max 2 uses
        manager.recordRedemption("LIMITED");
        manager.recordRedemption("LIMITED");
        InvalidPromoCodeException ex = assertThrows(InvalidPromoCodeException.class,
            () -> manager.validateAndGetDiscount("LIMITED", "SKU-001", 100.0));
        assertEquals(InvalidPromoCodeException.Reason.MAX_USES_REACHED, ex.getReason());
    }

    // ── Redemption and expiry ─────────────────────────────────────────────────────

    @Test
    void recordRedemption_incrementsCount() {
        createTestPromo("REDEEM", DiscountType.FIXED_AMOUNT, 10.0, TODAY, FUTURE);
        manager.recordRedemption("REDEEM");
        manager.recordRedemption("REDEEM");
        assertEquals(2, manager.getRedemptionCount("REDEEM"));
    }

    @Test
    void expireStalePromotions_expiredPromoNoLongerAppearsActive() {
        createTestPromo("OLDCODE", DiscountType.PERCENTAGE_OFF, 5.0, PAST.minusDays(5), PAST);
        manager.expireStalePromotions();
        assertFalse(manager.getActivePromoCodes().contains("OLDCODE"));
    }

    @Test
    void getActivePromoCodes_returnsOnlyCurrentlyValidCodes() {
        createTestPromo("ACTIVE1", DiscountType.PERCENTAGE_OFF, 10.0, TODAY, FUTURE);
        createTestPromo("OLDCODE", DiscountType.FIXED_AMOUNT, 5.0, PAST.minusDays(5), PAST);
        List<String> active = manager.getActivePromoCodes();
        assertTrue(active.contains("ACTIVE1"));
        assertFalse(active.contains("OLDCODE"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────────

    private String createTestPromo(String code, DiscountType type, double value,
                                   LocalDate start, LocalDate end) {
        return manager.createPromotion("Test Promo", code, type, value,
            start, end, List.of("SKU-001"), 0.0, 0);
    }
}