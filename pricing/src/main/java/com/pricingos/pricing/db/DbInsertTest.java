package com.pricingos.pricing.db;

import java.sql.*;
import java.util.UUID;
import java.time.LocalDateTime;

public class DbInsertTest {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DatabaseConnectionPool.getInstance().getRootConnection()) {
            conn.createStatement().execute("USE OOAD");
            
            String insertSql = "INSERT INTO price_list " +
                "(price_id, sku_id, region_code, channel, price_type, base_price, price_floor, currency_code, effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, "SKU-NEW-001");
                ps.setString(3, "SOUTH");
                ps.setString(4, "RETAIL");
                ps.setString(5, "RETAIL");
                ps.setBigDecimal(6, new java.math.BigDecimal("150.00"));
                ps.setBigDecimal(7, new java.math.BigDecimal("120.00"));
                ps.setString(8, "INR");
                ps.setObject(9, LocalDateTime.now());
                ps.setObject(10, null); // Testing null effective_to
                ps.setString(11, "ACTIVE");
                
                ps.executeUpdate();
                System.out.println("INSERT SUCCESSFUL!");
            } catch (SQLException e) {
                System.out.println("TEST NULL EFFECTIVE_TO EXCEPTION: " + e.getMessage());
            }

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, "SKU-NEW-001");
                ps.setString(3, "SOUTH");
                ps.setString(4, "RETAIL");
                ps.setString(5, "RETAIL");
                ps.setBigDecimal(6, new java.math.BigDecimal("150.00"));
                ps.setBigDecimal(7, new java.math.BigDecimal("120.00"));
                ps.setString(8, "INR");
                ps.setObject(9, LocalDateTime.now());
                // Test realistic max valid date for effective_to
                ps.setObject(10, LocalDateTime.of(2099, 12, 31, 23, 59, 59)); 
                ps.setString(11, "ACTIVE");
                
                ps.executeUpdate();
                System.out.println("INSERT WITH DATE SUCCESSFUL!");
            } catch (SQLException e) {
                System.out.println("TEST WITH DATE EXCEPTION: " + e.getMessage());
            }
        }
    }
}
