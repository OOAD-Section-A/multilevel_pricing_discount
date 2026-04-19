package com.pricingos.pricing.db;

import java.sql.*;

public class DbQuery {
    public static void main(String[] args) throws Exception {
        System.out.println("Connecting to Database...");
        try (Connection conn = DatabaseConnectionPool.getInstance().getRootConnection();
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SHOW DATABASES");
            System.out.println("--- DATABASES ---");
            while (rs.next()) {
                System.out.println(rs.getString(1));
            }

            stmt.execute("USE OOAD");
            
            System.out.println("Seeding Approval Requests...");
            stmt.executeUpdate("INSERT INTO approval_requests (approval_id, request_type, order_id, requested_discount_amt, status, submission_time, routed_to_approver_id, audit_log_flag) " +
                "VALUES ('APP-001', 'MANUAL_DISCOUNT', 'ORD-999', 25.0, 'PENDING', NOW(), 'MANAGER-1', FALSE) " +
                "ON DUPLICATE KEY UPDATE status=status");
            
            System.out.println("Seeding Profitability Analytics...");
            stmt.executeUpdate("INSERT INTO profitability_analytics (approval_id, request_type, discount_amount, final_status, recorded_at) " + 
                "VALUES ('APP-001', 'MANUAL_DISCOUNT', 50.0, 'APPROVED', NOW())");
                
            System.out.println("Database Seeding Completed.");
        }
    }
}
