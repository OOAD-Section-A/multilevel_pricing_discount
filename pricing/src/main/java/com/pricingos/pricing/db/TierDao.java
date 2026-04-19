package com.pricingos.pricing.db;

import com.pricingos.common.CustomerTier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TierDao {

    public static CustomerTier getEvaluatedTier(String customerId) {
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT tier FROM customer_tier_cache WHERE customerId = ?")) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return CustomerTier.valueOf(rs.getString("tier"));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return null;
    }

    public static void saveEvaluatedTier(String customerId, CustomerTier tier) {
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO customer_tier_cache (customerId, tier) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE tier = ?, evaluatedAt = CURRENT_TIMESTAMP")) {
            ps.setString(1, customerId);
            ps.setString(2, tier.name());
            ps.setString(3, tier.name());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public static CustomerTier getOverrideTier(String customerId) {
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT overrideTier FROM customer_tier_overrides WHERE customerId = ?")) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return CustomerTier.valueOf(rs.getString("overrideTier"));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return null;
    }

    public static void saveOverrideTier(String customerId, CustomerTier tier) {
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO customer_tier_overrides (customerId, overrideTier) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE overrideTier = ?, overrideSetAt = CURRENT_TIMESTAMP")) {
            ps.setString(1, customerId);
            ps.setString(2, tier.name());
            ps.setString(3, tier.name());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public static boolean hasOverride(String customerId) {
        return getOverrideTier(customerId) != null;
    }
}
