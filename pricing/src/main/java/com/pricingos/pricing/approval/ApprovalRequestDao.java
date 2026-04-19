package com.pricingos.pricing.approval;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.pricing.db.DatabaseConnectionPool;
import com.pricingos.common.ApprovalStatus;

public class ApprovalRequestDao {

    public static ApprovalRequest get(String id, Clock testClock) {
        String sql = "SELECT * FROM approval_requests WHERE approval_id = ?";
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs, testClock);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return null;
    }

    public static List<ApprovalRequest> findAll(Clock testClock) {
        List<ApprovalRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM approval_requests";
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs, testClock));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public static void save(ApprovalRequest r) {
        String sql = "INSERT INTO approval_requests (approval_id, request_type, order_id, requested_discount_amt, " +
            "status, submission_time, escalation_time, approval_timestamp, routed_to_approver_id, approving_manager_id, " +
            "rejection_reason, audit_log_flag) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE request_type=?, order_id=?, requested_discount_amt=?, status=?, submission_time=?, escalation_time=?, approval_timestamp=?, routed_to_approver_id=?, " +
            "approving_manager_id=?, rejection_reason=?, audit_log_flag=?";
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getApprovalId());
            ps.setString(2, r.getRequestType().name());
            ps.setString(3, r.getOrderId());
            ps.setDouble(4, r.getRequestedDiscountAmt());
            ps.setString(5, r.getStatus().name());
            ps.setObject(6, r.getSubmissionTime());
            ps.setObject(7, r.getEscalationTime());
            ps.setObject(8, r.getApprovalTimestamp());
            ps.setString(9, r.getRoutedToApproverId());
            ps.setString(10, r.getApprovingManagerId());
            ps.setString(11, r.getRejectionReason());
            ps.setBoolean(12, r.isAuditLogFlag());

            ps.setString(13, r.getRequestType().name());
            ps.setString(14, r.getOrderId());
            ps.setDouble(15, r.getRequestedDiscountAmt());
            ps.setString(16, r.getStatus().name());
            ps.setObject(17, r.getSubmissionTime());
            ps.setObject(18, r.getEscalationTime());
            ps.setObject(19, r.getApprovalTimestamp());
            ps.setString(20, r.getRoutedToApproverId());
            ps.setString(21, r.getApprovingManagerId());
            ps.setString(22, r.getRejectionReason());
            ps.setBoolean(23, r.isAuditLogFlag());
            
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private static ApprovalRequest map(ResultSet rs, Clock clock) throws SQLException {
        ApprovalRequest r = new ApprovalRequest(
            rs.getString("approval_id"),
            ApprovalRequestType.valueOf(rs.getString("request_type")),
            "UNKNOWN",
            rs.getString("order_id"),
            rs.getDouble("requested_discount_amt"),
            "From DB",
            clock == null ? Clock.systemDefaultZone() : clock
        );
        try {
            java.lang.reflect.Field sf = ApprovalRequest.class.getDeclaredField("status");
            sf.setAccessible(true);
            sf.set(r, ApprovalStatus.valueOf(rs.getString("status")));

            java.lang.reflect.Field sm = ApprovalRequest.class.getDeclaredField("submissionTime");
            sm.setAccessible(true);
            java.sql.Timestamp sts = rs.getTimestamp("submission_time");
            if (sts != null) sm.set(r, sts.toLocalDateTime());

            java.lang.reflect.Field et = ApprovalRequest.class.getDeclaredField("escalationTime");
            et.setAccessible(true);
            java.sql.Timestamp ets = rs.getTimestamp("escalation_time");
            et.set(r, ets == null ? null : ets.toLocalDateTime());

            java.lang.reflect.Field at = ApprovalRequest.class.getDeclaredField("approvalTimestamp");
            at.setAccessible(true);
            java.sql.Timestamp ats = rs.getTimestamp("approval_timestamp");
            at.set(r, ats == null ? null : ats.toLocalDateTime());

            java.lang.reflect.Field rt = ApprovalRequest.class.getDeclaredField("routedToApproverId");
            rt.setAccessible(true);
            rt.set(r, rs.getString("routed_to_approver_id"));

            java.lang.reflect.Field am = ApprovalRequest.class.getDeclaredField("approvingManagerId");
            am.setAccessible(true);
            am.set(r, rs.getString("approving_manager_id"));

            java.lang.reflect.Field rr = ApprovalRequest.class.getDeclaredField("rejectionReason");
            rr.setAccessible(true);
            rr.set(r, rs.getString("rejection_reason"));

            java.lang.reflect.Field al = ApprovalRequest.class.getDeclaredField("auditLogFlag");
            al.setAccessible(true);
            al.set(r, rs.getBoolean("audit_log_flag"));

        } catch (Exception e) {
            throw new RuntimeException("Failed to map ApprovalRequest via reflection", e);
        }
        return r;
    }
}
