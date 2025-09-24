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
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);
    private static volatile HikariDataSource dataSource;

    // The static initializer now only triggers the background task.
    static {
        initializePoolInBackground();
    }

    private static void initializePoolInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: Load properties from the config file in the background.
                Properties properties = new Properties();
                try (InputStream input = DatabaseConnection.class.getResourceAsStream("/config.properties")) {
                    if (input == null) {
                        throw new IOException("FATAL: Unable to find config.properties in resources.");
                    }
                    properties.load(input);
                }

                // Step 2: Initialize the connection pool. This is the slowest part.
                Class.forName("org.h2.Driver");
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(properties.getProperty("db.url"));
                config.setUsername(properties.getProperty("db.user"));
                config.setPassword(properties.getProperty("db.password"));

                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(15000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);

                dataSource = new HikariDataSource(config);
                logger.info("HikariCP Connection Pool Initialized successfully.");

                // Step 3: Signal that initialization is complete.
                initializationFuture.complete(null);

            } catch (Exception e) {
                logger.error("FATAL: Failed to initialize database connection pool.", e);
                // Step 4: Signal that initialization failed.
                initializationFuture.completeExceptionally(e);
            }
        });
    }

    public static Connection getInventoryConnection() throws SQLException {
        try {
            // This will wait for the background initialization to finish if it hasn't already.
            initializationFuture.get();
            return dataSource.getConnection();
        } catch (InterruptedException | ExecutionException e) {
            // If initialization failed, this will throw an exception.
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