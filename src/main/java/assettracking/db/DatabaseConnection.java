package assettracking.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DatabaseConnection {

    // Use a CompletableFuture to handle the asynchronous initialization.
    // This will hold the connection pool once it's ready.
    private static final CompletableFuture<HikariDataSource> dataSourceFuture = new CompletableFuture<>();

    static {
        // Start the initialization on a background thread immediately when the class is loaded.
        initializePoolInBackground();
    }

    private static void initializePoolInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("Attempting to load H2 JDBC Driver...");
                Class.forName("org.h2.Driver");
                System.out.println("H2 JDBC Driver loaded successfully.");

                HikariConfig config = new HikariConfig();
                // Use the direct IP address for maximum reliability
                config.setJdbcUrl("jdbc:h2:tcp://172.21.16.1/inventorybackup");
                config.setUsername("sa");
                config.setPassword("");
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(15000); // 15 seconds is a reasonable timeout
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);

                System.out.println("Initializing HikariCP Connection Pool for H2...");
                HikariDataSource ds = new HikariDataSource(config);
                System.out.println("HikariCP Connection Pool for H2 Initialized.");

                // When initialization is successful, complete the Future.
                dataSourceFuture.complete(ds);

            } catch (Exception e) {
                System.err.println("FATAL: Failed to initialize database connection pool.");
                // If it fails, complete the Future with an exception.
                dataSourceFuture.completeExceptionally(e);
            }
        });
    }

    /**
     * Gets a connection to the H2 database from the connection pool.
     * This method will wait if the pool is not yet initialized, but it will
     * not block the UI thread if called from a background task.
     * @return A database connection.
     * @throws SQLException if a connection cannot be established or the wait is interrupted.
     */
    public static Connection getInventoryConnection() throws SQLException {
        try {
            // .get() will wait for the background task to finish if it hasn't already.
            return dataSourceFuture.get().getConnection();
        } catch (InterruptedException | ExecutionException e) {
            // Wrap the original exception for better error reporting
            throw new SQLException("Failed to get database connection from the pool.", e);
        }
    }
}