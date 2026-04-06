package com.pricingos.pricing.promotion;

/**
 * Domain exception thrown by the Promotion & Campaign Manager when a coupon code
 * is invalid, expired, or not applicable to the current cart.
 *
 * <p>Handles the INVALID_PROMO_CODE exception from the Exception Table (MINOR category).
 * The UI layer catches this and allows up to 3 re-attempts before disabling the promo
 * input field for the session.
 */
public class InvalidPromoCodeException extends RuntimeException {

    /** Machine-readable reason code for the UI to act on. */
    public enum Reason {
        /** The coupon code does not exist in the system. */
        NOT_FOUND,
        /** The promotion's start_date is in the future — not yet active. */
        NOT_YET_ACTIVE,
        /** The promotion's end_date is in the past. */
        EXPIRED,
        /** The coupon exists but does not apply to the requested SKU. */
        SKU_NOT_ELIGIBLE,
        /** The cart subtotal is below the min_cart_value threshold. */
        CART_VALUE_TOO_LOW,
        /** The coupon has reached its max_uses redemption limit. */
        MAX_USES_REACHED
    }

    private final String couponCode;
    private final Reason reason;

    public InvalidPromoCodeException(String couponCode, Reason reason) {
        super(buildMessage(couponCode, reason));
        this.couponCode = couponCode;
        this.reason = reason;
    }

    public String getCouponCode() { return couponCode; }
    public Reason getReason()     { return reason; }

    private static String buildMessage(String code, Reason reason) {
        return String.format(
            "Promo code [%s] is %s.",
            code,
            switch (reason) {
                case NOT_FOUND      -> "invalid or does not exist";
                case NOT_YET_ACTIVE -> "not yet active: promotion starts in the future";
                case EXPIRED        -> "expired";
                case SKU_NOT_ELIGIBLE -> "not applicable to the current cart items";
                case CART_VALUE_TOO_LOW -> "not applicable: cart value is below the minimum threshold";
                case MAX_USES_REACHED -> "no longer available: maximum redemption limit reached";
            }
        );
    }
}