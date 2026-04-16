package com.pricingos.pricing.promotion;

public class InvalidPromoCodeException extends RuntimeException {

    public static final int EXCEPTION_ID = 7;
    public static final String EXCEPTION_NAME = "INVALID_PROMO_CODE";

    public enum Reason {

        NOT_FOUND,

        NOT_YET_ACTIVE,

        EXPIRED,

        SKU_NOT_ELIGIBLE,

        CART_VALUE_TOO_LOW,

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
            "%s (%d): Promo code [%s] is %s.",
            EXCEPTION_NAME,
            EXCEPTION_ID,
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
