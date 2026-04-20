package com.pricingos.pricing.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnectionPool {
    private static DatabaseConnectionPool instance;
    private final String url;
    private final String username;
    private final String password;

    private DatabaseConnectionPool() {
        Properties props = new Properties();
        try (InputStream input = DatabaseConnectionPool.class.getClassLoader().getResourceAsStream("database.properties")) {
            if (input == null) {
                throw new RuntimeException("database.properties not found in classpath");
            }
            props.load(input);
            this.url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/OOAD");
            this.username = props.getProperty("db.username", "root");
            this.password = props.getProperty("db.password", "1977");
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load database.properties", ex);
        }
    }

    public static synchronized DatabaseConnectionPool getInstance() {
        if (instance == null) {
            instance = new DatabaseConnectionPool();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
    
    public Connection getRootConnection() throws SQLException {
        // Connect to mysql directly without assuming OOAD exists
        String rootUrl = url.replace("/OOAD", "/");
        return DriverManager.getConnection(rootUrl, username, password);
    }
}
