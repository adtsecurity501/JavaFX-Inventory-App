package assettracking.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class DatabaseConnection {

    // Define your database locations in order of preference
    // We'll construct the full JDBC URLs from these base paths/UNCs
    private static final List<String> DATABASE_PATHS_OR_UNCS = Arrays.asList(
        "////utsprj2c2333/Server/inventorybackup.db"
    );

    private static volatile boolean driverLoaded = false;
    private static final Object driverLoadLock = new Object();

    static {
        // Load the driver once when the class is loaded.
        try {
            loadDriver();
        } catch (SQLException e) {
            System.err.println("FATAL: Failed to load SQLite driver during static initialization: " + e.getMessage());
            // Depending on your app, you might re-throw as a RuntimeException to halt startup
            // throw new RuntimeException("Failed to initialize DatabaseConnection", e);
        }
    }

    private static void loadDriver() throws SQLException {
        if (!driverLoaded) {
            synchronized (driverLoadLock) {
                if (!driverLoaded) {
                    try {
                        Class.forName("org.sqlite.JDBC");
                        driverLoaded = true;
                        // It's okay to leave this one, as it only runs once.
                        System.out.println("SQLite JDBC Driver loaded successfully.");
                    } catch (ClassNotFoundException e) {
                        System.err.println("SQLite JDBC Driver not found in classpath/modulepath.");
                        throw new SQLException("SQLite JDBC Driver not found: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    public static Connection getInventoryConnection() throws SQLException {
        SQLException lastException = null;

        for (String pathOrUnc : DATABASE_PATHS_OR_UNCS) {
            String jdbcUrl = "jdbc:sqlite:" + pathOrUnc;
            System.out.println("Attempting to connect to: " + jdbcUrl);

            if (!pathOrUnc.startsWith("//") && !pathOrUnc.startsWith("\\\\")) {
                File dbFile = new File(pathOrUnc);
                if (!dbFile.exists()) {
                    lastException = new SQLException("Local database file not found: " + pathOrUnc);
                    continue;
                }
            }

            try {
                Connection connection = DriverManager.getConnection(jdbcUrl);
                System.out.println("Successfully connected to: " + jdbcUrl);
                return connection;
            } catch (SQLException e) {
                System.err.println("Failed to connect to " + jdbcUrl + ": " + e.getMessage());
                lastException = e;
            }
        }

        if (lastException != null) {
            System.err.println("Failed to connect to any of the configured database locations.");
            throw lastException;
        } else {
            throw new SQLException("No database locations configured or all failed without specific exceptions.");
        }
    }

    // Optional: Main method for quick testing
    public static void main(String[] args) {
        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            if (conn != null) {
                System.out.println("Test connection successful!");
                // You could verify further, e.g.:
                // System.out.println("Connected via: " + conn.getMetaData().getURL());
            }
        } catch (SQLException e) {
            System.err.println("Test connection failed with SQLException after trying all locations: " + e.getMessage());
            // e.printStackTrace(); // Uncomment for full stack trace during development
        }
    }
}