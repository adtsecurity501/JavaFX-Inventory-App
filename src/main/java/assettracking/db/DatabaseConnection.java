package assettracking.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DatabaseConnection {

    private static final CompletableFuture<HikariDataSource> dataSourceFuture = new CompletableFuture<>();

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
                // --- THIS IS THE CORRECTED LINE ---
                config.setJdbcUrl("jdbc:h2:tcp://10.68.47.13/inventorybackup");
                // --- END OF CORRECTION ---
                config.setUsername("sa");
                config.setPassword("");
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(15000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);

                System.out.println("Initializing HikariCP Connection Pool for H2...");
                HikariDataSource ds = new HikariDataSource(config);
                System.out.println("HikariCP Connection Pool for H2 Initialized.");

                dataSourceFuture.complete(ds);

            } catch (Exception e) {
                System.err.println("FATAL: Failed to initialize database connection pool.");
                dataSourceFuture.completeExceptionally(e);
            }
        });
    }

    public static Connection getInventoryConnection() throws SQLException {
        try {
            return dataSourceFuture.get().getConnection();
        } catch (InterruptedException | ExecutionException e) {
            throw new SQLException("Failed to get database connection from the pool.", e);
        }
    }
}