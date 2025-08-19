package assettracking.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

public class DatabaseConnection {

    // --- PREFERRED DATABASES (The ones the app should try to connect to first) ---
    private static final String CORRECT_DB_FILE_NAME = "inventorybackup.db";
    private static final String APP_DIR_PATH = System.getProperty("user.home") + File.separator + ".AssetTracking";
    private static final String CORRECT_LOCAL_DB_URL = "jdbc:sqlite:" + APP_DIR_PATH + File.separator + CORRECT_DB_FILE_NAME;
    private static final String NETWORK_DB_URL = "jdbc:sqlite:////utsprj2c2333/Server/inventorybackup.db";

    // --- FALLBACK DATABASE (Only used if the preferred databases cannot be reached) ---
    private static final String FALLBACK_DB_FILE_NAME = "inventory.db";
    private static final String FALLBACK_LOCAL_DB_PATH = APP_DIR_PATH + File.separator + FALLBACK_DB_FILE_NAME;
    private static final String FALLBACK_LOCAL_DB_URL = "jdbc:sqlite:" + FALLBACK_LOCAL_DB_PATH;

    // List of databases to try connecting to, in order of priority.
    private static final List<String> DATABASE_URLS_TO_TRY = Arrays.asList(
            CORRECT_LOCAL_DB_URL,
            NETWORK_DB_URL
    );

    private static volatile boolean driverLoaded = false;
    private static final Object driverLoadLock = new Object();

    static {
        try {
            loadDriver();
        } catch (SQLException e) {
            System.err.println("FATAL: Failed to load SQLite driver during static initialization: " + e.getMessage());
        }
    }

    // --- MODIFIED LOGIC ---
    public static Connection getInventoryConnection() throws SQLException {
        loadDriver(); // Ensure driver is loaded

        // 1. Try to connect to the preferred databases first.
        for (String url : DATABASE_URLS_TO_TRY) {
            try {
                System.out.println("Attempting to connect to preferred database: " + url);
                Connection connection = DriverManager.getConnection(url);
                System.out.println("Successfully connected to: " + url);
                return connection; // Success! Return the connection.
            } catch (SQLException e) {
                System.err.println("Failed to connect to " + url + ": " + e.getMessage());
                // Ignore and try the next one.
            }
        }

        // 2. If all preferred connections fail, fall back to creating a new local database.
        System.err.println("Could not connect to any preferred databases. Falling back to local database creation.");
        initializeAndConnectToFallback();

        // 3. Try connecting to the newly created fallback database one last time.
        try {
            System.out.println("Attempting to connect to newly created fallback database: " + FALLBACK_LOCAL_DB_URL);
            return DriverManager.getConnection(FALLBACK_LOCAL_DB_URL);
        } catch (SQLException finalException) {
            System.err.println("FATAL: Could not even connect to the fallback database.");
            throw finalException;
        }
    }

    private static void initializeAndConnectToFallback() {
        File dbFile = new File(FALLBACK_LOCAL_DB_PATH);
        if (!dbFile.exists()) {
            System.out.println("Fallback database file not found. Creating new database at: " + FALLBACK_LOCAL_DB_PATH);
            try {
                dbFile.getParentFile().mkdirs();
                try (Connection conn = DriverManager.getConnection(FALLBACK_LOCAL_DB_URL);
                     Statement stmt = conn.createStatement()) {
                    createTables(stmt); // Use the complete schema creation method
                    System.out.println("Fallback database and tables created successfully.");
                }
            } catch (SQLException e) {
                System.err.println("FATAL: Could not create or initialize the fallback database.");
                e.printStackTrace();
            }
        }
    }

    private static void loadDriver() throws SQLException {
        if (!driverLoaded) {
            synchronized (driverLoadLock) {
                if (!driverLoaded) {
                    try {
                        Class.forName("org.sqlite.JDBC");
                        driverLoaded = true;
                        System.out.println("SQLite JDBC Driver loaded successfully.");
                    } catch (ClassNotFoundException e) {
                        System.err.println("SQLite JDBC Driver not found.");
                        throw new SQLException("SQLite JDBC Driver not found", e);
                    }
                }
            }
        }
    }

    private static void createTables(Statement stmt) throws SQLException {
        // This is the full, correct schema for creating a new database from scratch.
        stmt.execute("PRAGMA foreign_keys = OFF;");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Device_Status (status_id INTEGER PRIMARY KEY, receipt_id INTEGER NOT NULL REFERENCES Receipt_Events (receipt_id), sheet_id INTEGER REFERENCES Sheets, status TEXT, sub_status TEXT, receive_date DATE, last_update DATETIME, change_log TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Disposition_Info (disposition_id INTEGER PRIMARY KEY, receipt_id INTEGER NOT NULL REFERENCES Receipt_Events (receipt_id), is_everon BOOLEAN DEFAULT (0), is_end_of_life BOOLEAN DEFAULT (0), is_under_capacity BOOLEAN DEFAULT (0), is_phone BOOLEAN DEFAULT (0), other_disqualification TEXT DEFAULT NULL, final_auto_disp TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS EOLDevice (model_name TEXT, part_numbers TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Flag_Devices (serial_number TEXT PRIMARY KEY, status TEXT, sub_status TEXT, flag_reason TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Mel_Rules (model_number TEXT, description TEXT, action TEXT, special_notes TEXT, manufac TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Packages (package_id INTEGER PRIMARY KEY, tracking_number VARCHAR (50) NOT NULL UNIQUE, first_name VARCHAR (50), last_name VARCHAR (50), city VARCHAR (100), state VARCHAR (2), zip_code VARCHAR (10), receive_date DATE NOT NULL);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Physical_Assets (asset_id INTEGER PRIMARY KEY AUTOINCREMENT, serial_number TEXT NOT NULL UNIQUE, imei TEXT UNIQUE, category VARCHAR (250), make TEXT, description TEXT, part_number TEXT, capacity TEXT, everon_serial BOOLEAN NOT NULL DEFAULT 0);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Receipt_Events (receipt_id INTEGER PRIMARY KEY AUTOINCREMENT, serial_number TEXT NOT NULL REFERENCES Physical_Assets (serial_number), package_id INTEGER NOT NULL REFERENCES Packages (package_id), IMEI NUMERIC, category VARCHAR (50), make VARCHAR (50), model_number VARCHAR (50), description TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Return_Labels (tracking_number NUMERIC, contact_name TEXT, email_address TEXT, city TEXT, state TEXT, zip_code NUMERIC, log_date DATE);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Sheets (sheet_id INTEGER PRIMARY KEY, sheet_name VARCHAR (100) NOT NULL UNIQUE);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS SKU_Table (sku_number TEXT, model_number TEXT, category TEXT, manufac TEXT, description TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ZipCodeData (zip_code TEXT PRIMARY KEY, zip_type TEXT, primary_city TEXT, state_code TEXT, country_code TEXT);");
        System.out.println("All tables created (if they did not exist).");
    }
}