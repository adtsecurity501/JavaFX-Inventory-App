package assettracking.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DatabaseConnection {

    private static final CompletableFuture<Void> initializationFuture = new CompletableFuture<>();
    // --- END MODIFICATION ---
    // --- MODIFICATION 1: Make the data source accessible ---
    private static volatile HikariDataSource dataSource;

    static {
        initializePoolInBackground();
    }

    private static void initializePoolInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("Attempting to load H2 JDBC Driver...");
                Class.forName("org.h2.Driver");
                System.out.println("H2 JDBC Driver loaded successfully.");

                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:h2:file:////UTSPRJ2C2333/Server/inventorybackup;AUTO_SERVER=TRUE");
                config.setUsername("sa");
                config.setPassword("");
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(15000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);

                System.out.println("Initializing HikariCP Connection Pool for H2...");
                // --- MODIFICATION 2: Assign to the static field ---
                dataSource = new HikariDataSource(config);
                // --- END MODIFICATION ---
                System.out.println("HikariCP Connection Pool for H2 Initialized.");

                initializationFuture.complete(null);

            } catch (Exception e) {
                System.err.println("FATAL: Failed to initialize database connection pool.");
                initializationFuture.completeExceptionally(e);
            }
        });
    }

    public static Connection getInventoryConnection() throws SQLException {
        try {
            initializationFuture.get(); // Wait for initialization to complete
            return dataSource.getConnection();
        } catch (InterruptedException | ExecutionException e) {
            throw new SQLException("Failed to get database connection from the pool.", e);
        }
    }

    // --- NEW METHOD ---

    /**
     * Gracefully evicts all idle connections from the pool.
     * The next time a connection is requested, the pool will have to create a new, fresh one.
     */
    public static void refreshConnectionPool() {
        if (dataSource != null) {
            System.out.println("Refreshing database connection pool...");
            dataSource.getHikariPoolMXBean().softEvictConnections();
            System.out.println("Connection pool refreshed.");
        }
    }

    /**
     * Closes the connection pool. This should be called on application shutdown.
     */
    public static void closeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            System.out.println("Closing database connection pool...");
            dataSource.close();
            System.out.println("Connection pool closed.");
        }
    }
}