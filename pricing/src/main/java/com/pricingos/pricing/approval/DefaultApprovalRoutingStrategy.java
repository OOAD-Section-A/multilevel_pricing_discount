package com.pricingos.pricing.approval;

import com.pricingos.common.IApproverRoleService;
import java.util.Objects;

/**
 * Default implementation of {@link ApprovalRoutingStrategy}.
 *
 * <p>Routes approval requests based on the request type and discount amount:
 * <ul>
 *   <li>MANUAL_DISCOUNT up to the threshold → floor manager (from T3N50R hierarchy).</li>
 *   <li>MANUAL_DISCOUNT above threshold → regional manager (escalation path).</li>
 *   <li>CONTRACT_BYPASS → B2B Sales Rep's direct manager.</li>
 *   <li>POLICY_EXCEPTION → Pricing Admin's manager.</li>
 *   <li>Any request > seniorApprovalThreshold → dual approval required.</li>
 * </ul>
 *
 * <p>Delegates actual hierarchy lookup to {@link IApproverRoleService} (Team T3N50R)
 * so routing logic stays decoupled from auth implementation — SOLID DIP.
 */
public class DefaultApprovalRoutingStrategy implements ApprovalRoutingStrategy {

    /**
     * Discount amounts above this threshold (absolute value) require dual approval.
     * Configurable at construction time; default is ₹5,000.
     */
    private final double seniorApprovalThreshold;

    /**
     * ID of the Pricing Admin's manager — used for POLICY_EXCEPTION routing.
     * In production this would be resolved from the T3N50R hierarchy service.
     */
    private final String pricingAdminManagerId;

    /** Integration with T3N50R for actual manager lookup. */
    private final IApproverRoleService approverRoleService;

    public DefaultApprovalRoutingStrategy(IApproverRoleService approverRoleService,
                                          String pricingAdminManagerId,
                                          double seniorApprovalThreshold) {
        this.approverRoleService      = Objects.requireNonNull(approverRoleService, "approverRoleService cannot be null");
        this.pricingAdminManagerId    = Objects.requireNonNull(pricingAdminManagerId, "pricingAdminManagerId cannot be null");
        this.seniorApprovalThreshold  = seniorApprovalThreshold;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the primary approver by asking T3N50R for the escalation manager
     * of the requester. Falls back to the pricing admin manager for POLICY_EXCEPTION.
     */
    @Override
    public String resolveApproverId(ApprovalRequest request) {
        return switch (request.getRequestType()) {
            case MANUAL_DISCOUNT, CONTRACT_BYPASS ->
                // Ask T3N50R: who is the direct manager of the person who submitted this?
                approverRoleService.getEscalationManagerId(request.getRequestedBy());

            case POLICY_EXCEPTION ->
                // Policy exceptions always go to the Pricing Admin's manager.
                pricingAdminManagerId;
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p>Any request above the senior approval threshold requires dual sign-off.
     */
    @Override
    public boolean requiresDualApproval(ApprovalRequest request) {
        return request.getRequestedDiscountAmt() > seniorApprovalThreshold;
    }
}