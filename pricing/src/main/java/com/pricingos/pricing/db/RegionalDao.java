package com.pricingos.pricing.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RegionalDao {

    public static double getMultiplier(String regionCode, double defaultValue) {
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT multiplier FROM regional_pricing_multipliers WHERE region_code = ?")) {
            ps.setString(1, regionCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("multiplier");
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return defaultValue;
    }

    public static void saveMultiplier(String regionCode, double multiplier) {
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO regional_pricing_multipliers (region_code, multiplier) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE multiplier = ?")) {
            ps.setString(1, regionCode);
            ps.setDouble(2, multiplier);
            ps.setDouble(3, multiplier);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
}
