package assettracking.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DatabaseConnection {

    private static final CompletableFuture<Void> initializationFuture = new CompletableFuture<>();
    private static volatile HikariDataSource dataSource;

    static {
        initializePoolInBackground();
    }

    private static void initializePoolInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("Attempting to load PostgreSQL JDBC Driver...");
                Class.forName("org.postgresql.Driver");
                System.out.println("PostgreSQL JDBC Driver loaded successfully.");

                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:postgresql://10.68.47.138:5432/inventory");
                config.setUsername("postgres");
                config.setPassword("admin");
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(15000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);

                System.out.println("Initializing HikariCP Connection Pool for PostgreSQL...");
                dataSource = new HikariDataSource(config);
                System.out.println("HikariCP Connection Pool for PostgreSQL Initialized.");

                initializationFuture.complete(null);

            } catch (Exception e) {
                System.err.println("FATAL: Failed to initialize PostgreSQL database connection pool.");
                e.printStackTrace();
                initializationFuture.completeExceptionally(e);
            }
        });
    }

    public static Connection getInventoryConnection() throws SQLException {
        try {
            initializationFuture.get();
            return dataSource.getConnection();
        } catch (InterruptedException | ExecutionException e) {
            throw new SQLException("Failed to get PostgreSQL database connection from the pool.", e);
        }
    }

    public static void refreshConnectionPool() {
        if (dataSource != null) {
            System.out.println("Refreshing PostgreSQL database connection pool...");
            dataSource.getHikariPoolMXBean().softEvictConnections();
            System.out.println("Connection pool refreshed.");
        }
    }

    public static void closeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            System.out.println("Closing PostgreSQL database connection pool...");
            dataSource.close();
            System.out.println("Connection pool closed.");
        }
    }
}