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

    // Static block to load properties and then initialize the pool
    static {
        try (InputStream input = DatabaseConnection.class.getResourceAsStream("/config.properties")) {
            if (input == null) {
                String errorMsg = "FATAL: Unable to find config.properties in resources.";
                logger.error(errorMsg);
                initializationFuture.completeExceptionally(new IOException(errorMsg));
            } else {
                properties.load(input);
                logger.info("config.properties loaded successfully.");
                initializePoolInBackground();
            }
        } catch (IOException e) {
            logger.error("FATAL: Error reading config.properties.", e);
            initializationFuture.completeExceptionally(e);
        }
    }

    /**
     * Initializes the HikariCP connection pool on a background thread
     * using the settings from the loaded properties file.
     */
    private static void initializePoolInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Attempting to load PostgreSQL JDBC Driver...");
                Class.forName("org.postgresql.Driver");
                logger.info("PostgreSQL JDBC Driver loaded successfully.");

                HikariConfig config = new HikariConfig();

                // --- Connection Settings read from config.properties ---
                config.setJdbcUrl(properties.getProperty("db.url"));
                config.setUsername(properties.getProperty("db.user"));
                config.setPassword(properties.getProperty("db.password"));

                // --- Connection Pool Performance Settings ---
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(15000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);

                logger.info("Initializing HikariCP Connection Pool for PostgreSQL...");
                dataSource = new HikariDataSource(config);
                logger.info("HikariCP Connection Pool for PostgreSQL Initialized.");

                initializationFuture.complete(null);

            } catch (Exception e) {
                logger.error("FATAL: Failed to initialize PostgreSQL database connection pool.", e);
                initializationFuture.completeExceptionally(e);
            }
        });
    }

    /**
     * Gets a connection from the pool. This method will wait if the pool is not yet initialized.
     *
     * @return A database connection.
     * @throws SQLException if a connection cannot be obtained.
     */
    public static Connection getInventoryConnection() throws SQLException {
        try {
            initializationFuture.get(); // Wait for the pool to be ready
            return dataSource.getConnection();
        } catch (InterruptedException | ExecutionException e) {
            throw new SQLException("Failed to get PostgreSQL database connection from the pool.", e);
        }
    }

    /**
     * Gently removes idle connections from the pool.
     */
    public static void refreshConnectionPool() {
        if (dataSource != null) {
            logger.info("Refreshing PostgreSQL database connection pool...");
            dataSource.getHikariPoolMXBean().softEvictConnections();
            logger.info("Connection pool refreshed.");
        }
    }

    /**
     * Closes the entire connection pool. This should be called when the application shuts down.
     */
    public static void closeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing PostgreSQL database connection pool...");
            dataSource.close();
            logger.info("Connection pool closed.");
        }
    }
}