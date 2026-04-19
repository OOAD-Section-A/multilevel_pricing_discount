package com.pricingos.pricing.db;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.nio.charset.StandardCharsets;

public class DatabaseSetup {
    public static void main(String[] args) {
        try {
            System.out.println("Reading schema.sql...");
            List<String> lines = Files.readAllLines(Paths.get("../schema.sql"), StandardCharsets.UTF_8);
            
            System.out.println("Connecting to Database as root...");
            try (Connection conn = DatabaseConnectionPool.getInstance().getRootConnection();
                 Statement stmt = conn.createStatement()) {
                
                stmt.execute("CREATE DATABASE IF NOT EXISTS OOAD;");
                stmt.execute("USE OOAD;");
                
                StringBuilder sb = new StringBuilder();
                int count = 0;
                for (String line : lines) {
                    String t = line.trim();
                    if (t.startsWith("--") || t.isEmpty()) continue;
                    
                    sb.append(line).append("\n");
                    if (t.endsWith(";")) {
                        String sql = sb.toString().trim();
                        sb.setLength(0);
                        if (sql.toUpperCase().contains("CREATE DATABASE") || sql.toUpperCase().startsWith("USE ")) continue;
                        
                        try {
                            stmt.execute(sql);
                            count++;
                        } catch (Exception e) {
                            System.err.println("SQL ERROR:");
                            System.err.println(e.getMessage());
                        }
                    }
                }
                System.out.println("Successfully executed " + count + " statements.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
