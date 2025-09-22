package assettracking.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DatabaseConnection {

    private static final CompletableFuture<Void> initializationFuture = new CompletableFuture<>();
    private static final Properties properties = new Properties();
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);
    private static volatile HikariDataSource dataSource;

    static {
        // Load properties file once
        try (InputStream input = DatabaseConnection.class.getResourceAsStream("/config.properties")) {
            if (input == null) {
                logger.error("FATAL: Unable to find config.properties in resources.");
                initializationFuture.completeExceptionally(new IOException("config.properties not found"));
            } else {
                properties.load(input);
                initializePoolInBackground();
            }
        } catch (IOException e) {
            logger.error("FATAL: Error reading config.properties.");
            initializationFuture.completeExceptionally(e);
        }
    }

    private static void initializePoolInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                Class.forName("org.h2.Driver");

                HikariConfig config = new HikariConfig();
                // Use properties from the file
                config.setJdbcUrl(properties.getProperty("db.url"));
                config.setUsername(properties.getProperty("db.user"));
                config.setPassword(properties.getProperty("db.password"));

                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(15000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);

                dataSource = new HikariDataSource(config);
                System.out.println("HikariCP Connection Pool Initialized.");
                initializationFuture.complete(null);
            } catch (Exception e) {
                logger.error("FATAL: Failed to initialize database connection pool.");
                initializationFuture.completeExceptionally(e);
            }
        });
    }

    // The rest of the class remains the same
    public static Connection getInventoryConnection() throws SQLException {
        try {
            initializationFuture.get();
            return dataSource.getConnection();
        } catch (InterruptedException | ExecutionException e) {
            throw new SQLException("Failed to get database connection from the pool.", e);
        }
    }

    public static void refreshConnectionPool() {
        if (dataSource != null) {
            dataSource.getHikariPoolMXBean().softEvictConnections();
        }
    }

    public static void closeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}