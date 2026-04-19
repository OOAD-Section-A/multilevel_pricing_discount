package com.pricingos.pricing.db;

import java.sql.*;

public class DbExec {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DatabaseConnectionPool.getInstance().getRootConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("USE OOAD");
            try {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS price_list (\n" +
                    "    price_id        VARCHAR(50)    NOT NULL,\n" +
                    "    sku_id          VARCHAR(50)    NOT NULL,\n" +
                    "    region_code     VARCHAR(20)    NOT NULL,\n" +
                    "    channel         VARCHAR(30)    NOT NULL,\n" +
                    "    price_type      ENUM('RETAIL','DISTRIBUTOR') NOT NULL,\n" +
                    "    base_price      DECIMAL(12,2)  NOT NULL,\n" +
                    "    price_floor     DECIMAL(12,2)  NOT NULL,\n" +
                    "    currency_code   CHAR(3)        NOT NULL  DEFAULT 'INR',\n" +
                    "    effective_from  DATETIME       NOT NULL,\n" +
                    "    effective_to    DATETIME       NOT NULL,\n" +
                    "    status          ENUM('ACTIVE','INACTIVE','SUPERSEDED') NOT NULL DEFAULT 'ACTIVE',\n" +
                    "\n" +
                    "    PRIMARY KEY (price_id),\n" +
                    "    UNIQUE KEY uq_price_list_sku_region_channel_type\n" +
                    "        (sku_id, region_code, channel, price_type, effective_from),\n" +
                    "    CONSTRAINT chk_price_floor CHECK (price_floor <= base_price),\n" +
                    "    CONSTRAINT chk_base_price_positive CHECK (base_price >= 0),\n" +
                    "    CONSTRAINT chk_price_date_range CHECK (effective_to > effective_from)\n" +
                    ")"
                );
                System.out.println("SUCCESSFULLY CREATED price_list");
            } catch (SQLException e) {
                System.out.println("SQL ERROR CODE: " + e.getErrorCode());
                System.out.println("SQL STATE: " + e.getSQLState());
                System.out.println("MESSAGE: " + e.getMessage());
            }
        }
    }
}
