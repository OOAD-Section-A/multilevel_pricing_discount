package com.pricingos.pricing.exception;

import com.scm.core.Severity;
import com.scm.factory.SCMExceptionFactory;
import com.scm.handler.SCMExceptionHandler;
import com.scm.subsystems.MultiLevelPricingSubsystem;

import java.util.function.Consumer;

public final class PricingExceptionReporter {

    private static final String SUBSYSTEM_NAME = "Multi-level Pricing";

    private PricingExceptionReporter() {
    }

    public static void invalidPromoCode(String promoCode) {
        emit(exceptions -> exceptions.onInvalidPromoCode(promoCode));
    }

    public static void priceFloorConfigError(String configKey) {
        emit(exceptions -> exceptions.onPriceFloorConfigError(configKey));
    }

    public static void externalDataTimeout(String sourceName, int timeoutMs) {
        emit(exceptions -> exceptions.onExternalDataTimeout(sourceName, timeoutMs));
    }

    public static void basePriceNotFound(String productId) {
        emit(exceptions -> exceptions.onBasePriceNotFound(productId));
    }

    public static void approvalEscalationTimeout(String priceChangeId, long elapsedMs) {
        emit(exceptions -> exceptions.onApprovalEscalationTimeout(priceChangeId, elapsedMs));
    }

    public static void contractExpiredAlert(String contractId, String expiryDate) {
        emit(exceptions -> exceptions.onContractExpiredAlert(contractId, expiryDate));
    }

    public static void duplicateContractConflict(String contractId1, String contractId2) {
        emit(exceptions -> exceptions.onDuplicateContractConflict(contractId1, contractId2));
    }

    public static void policyStackingConflict(String productId, String policies) {
        emit(exceptions -> exceptions.onPolicyStackingConflict(productId, policies));
    }

    public static void negativeMarginCalculation(String productId, double margin) {
        emit(exceptions -> exceptions.onNegativeMarginCalculation(productId, margin));
    }

    public static void unregistered(String exceptionName, String message) {
        emit(() -> SCMExceptionHandler.INSTANCE.handle(
                SCMExceptionFactory.create(0, exceptionName, message, SUBSYSTEM_NAME, Severity.MINOR)));
    }

    private static void emit(Consumer<MultiLevelPricingSubsystem> action) {
        if (isDisabled()) {
            return;
        }
        try {
            action.accept(MultiLevelPricingSubsystem.INSTANCE);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static void emit(Runnable action) {
        if (isDisabled()) {
            return;
        }
        try {
            action.run();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static boolean isDisabled() {
        return Boolean.getBoolean("scm.event.viewer.disabled");
    }
}
