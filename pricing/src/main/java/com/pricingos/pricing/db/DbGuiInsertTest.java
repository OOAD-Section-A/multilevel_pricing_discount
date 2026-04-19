package com.pricingos.pricing.db;

import java.sql.*;
import java.time.LocalDateTime;

public class DbGuiInsertTest {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DatabaseConnectionPool.getInstance().getRootConnection()) {
            conn.createStatement().execute("USE OOAD");
            
            String insertSql = "INSERT INTO price_list " +
                "(price_id, sku_id, region_code, channel, price_type, base_price, price_floor, currency_code, effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, "PRICE-123456789");
                ps.setString(2, "SKU-NEW-001");
                ps.setString(3, "SOUTH");
                ps.setString(4, "RETAIL");
                ps.setString(5, "RETAIL");
                ps.setBigDecimal(6, new java.math.BigDecimal("150.00"));
                ps.setBigDecimal(7, new java.math.BigDecimal("120.00"));
                ps.setString(8, "INR");
                ps.setObject(9, LocalDateTime.now());
                ps.setObject(10, LocalDateTime.now().plusMonths(1));
                ps.setString(11, "ACTIVE");
                
                ps.executeUpdate();
                System.out.println("GUI INSERT SUCCESSFUL!");
            } catch (SQLException e) {
                System.out.println("GUI INSERT EXCEPTION: " + e.getMessage());
            }
        }
    }
}
