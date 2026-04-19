package com.pricingos.pricing.contract;

import com.pricingos.common.ContractStatus;
import com.pricingos.pricing.db.DatabaseConnectionPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContractDao {

    public static void save(Contract contract) {
        String insertContract = "INSERT INTO contracts (contract_id, customer_id, start_date, end_date, status) VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE end_date = ?, status = ?";
        String insertSku = "INSERT INTO contract_sku_prices (contract_id, sku_id, price) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE price = ?";
        
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(insertContract)) {
                ps.setString(1, contract.getContractId());
                ps.setString(2, contract.getCustomerId());
                ps.setDate(3, java.sql.Date.valueOf(contract.getStartDate()));
                ps.setDate(4, java.sql.Date.valueOf(contract.getEndDate()));
                ps.setString(5, contract.getStatus().name());
                
                ps.setDate(6, java.sql.Date.valueOf(contract.getEndDate()));
                ps.setString(7, contract.getStatus().name());
                ps.executeUpdate();
            }
            try (PreparedStatement deletePs = c.prepareStatement("DELETE FROM contract_sku_prices WHERE contract_id = ?")) {
                deletePs.setString(1, contract.getContractId());
                deletePs.executeUpdate();
            }
            // Need a way to fetch the skuPrices from contract. Contract has getPrice(skuId) but no getSkuPrices() method!
            // We can use reflection to access `skuPrices` map.
            java.lang.reflect.Field skuPricesField = Contract.class.getDeclaredField("skuPrices");
            skuPricesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Double> skuPrices = (Map<String, Double>) skuPricesField.get(contract);
            
            try (PreparedStatement psSku = c.prepareStatement(insertSku)) {
                for (Map.Entry<String, Double> entry : skuPrices.entrySet()) {
                    psSku.setString(1, contract.getContractId());
                    psSku.setString(2, entry.getKey());
                    psSku.setDouble(3, entry.getValue());
                    psSku.setDouble(4, entry.getValue());
                    psSku.executeUpdate();
                }
            }
            c.commit();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static Contract get(String id) {
        String sql = "SELECT * FROM contracts WHERE contract_id = ?";
        String skuSql = "SELECT sku_id, price FROM contract_sku_prices WHERE contract_id = ?";
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             PreparedStatement psSku = c.prepareStatement(skuSql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    psSku.setString(1, id);
                    Map<String, Double> prices = new HashMap<>();
                    try (ResultSet rsSku = psSku.executeQuery()) {
                        while (rsSku.next()) prices.put(rsSku.getString("sku_id"), rsSku.getDouble("price"));
                    }
                    return mapRow(rs, prices);
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    public static List<Contract> findAll() {
        List<Contract> contracts = new ArrayList<>();
        String sql = "SELECT * FROM contracts";
        String skuSql = "SELECT sku_id, price FROM contract_sku_prices WHERE contract_id = ?";
        try (Connection c = DatabaseConnectionPool.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             PreparedStatement psSku = c.prepareStatement(skuSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String contractId = rs.getString("contract_id");
                psSku.setString(1, contractId);
                Map<String, Double> prices = new HashMap<>();
                try (ResultSet rsSku = psSku.executeQuery()) {
                    while (rsSku.next()) prices.put(rsSku.getString("sku_id"), rsSku.getDouble("price"));
                }
                contracts.add(mapRow(rs, prices));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return contracts;
    }

    private static Contract mapRow(ResultSet rs, Map<String, Double> prices) throws Exception {
        Contract c = Contract.builder(rs.getString("contract_id"), rs.getString("customer_id"))
            .startDate(rs.getDate("start_date").toLocalDate())
            .endDate(rs.getDate("end_date").toLocalDate())
            .skuPrices(prices)
            .build();
        java.lang.reflect.Field statusField = Contract.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(c, ContractStatus.valueOf(rs.getString("status")));
        return c;
    }
}
