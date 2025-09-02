package assettracking.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final HikariDataSource dataSource;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("SQLite JDBC Driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            System.err.println("FATAL: SQLite JDBC Driver not found. The application cannot run.");
            throw new RuntimeException("Failed to load SQLite driver", e);
        }

        String dbUrl = getBestAvailableDbUrl();
        System.out.println("HikariCP Pool Initializing with DB URL: " + dbUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);

        config.addDataSourceProperty("cache_size", "20000");
        config.addDataSourceProperty("page_size", "4096");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("journal_mode", "WAL");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);
        System.out.println("HikariCP Connection Pool Initialized.");
    }

    public static Connection getInventoryConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private static String getBestAvailableDbUrl() {
        String networkPath = getNetworkDbPath();
        String localPath = getLocalDbPath();

        // This try-with-resources block tests the network connection.
        // The 'ignored' variable silences the "variable is never used" warning.
        try (Connection ignored = DriverManager.getConnection("jdbc:sqlite:" + networkPath)) {
            System.out.println("Network database is accessible. Using network path.");
            return "jdbc:sqlite:" + networkPath;
        } catch (SQLException e) {
            System.err.println("Network database not accessible, falling back to local. Reason: " + e.getMessage());
            File localDbFile = new File(localPath);
            if (!localDbFile.exists()) {
                System.out.println("Local database not found. Initializing new database at: " + localPath);
                initializeNewDatabase(localDbFile);
            }
            return "jdbc:sqlite:" + localPath;
        }
    }

    // ... (rest of the file is identical and correct) ...

    private static String getNetworkDbPath() {
        String dbFileName = "inventorybackup.db";
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "\\\\UTSPRJ2C2333\\Server\\" + dbFileName;
        } else {
            return "/Volumes/Server/" + dbFileName;
        }
    }

    private static String getLocalDbPath() {
        String dbFileName = "inventorybackup.db";
        String appDir = System.getenv("APPDATA") + File.separator + "AssetTracking";
        return appDir + File.separator + dbFileName;
    }

    private static void initializeNewDatabase(File dbFile) {
        boolean dirsCreated = dbFile.getParentFile().mkdirs();
        if (!dirsCreated && !dbFile.getParentFile().exists()) {
            System.err.println("FATAL: Could not create application directory: " + dbFile.getParentFile());
            return;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {

            System.out.println("Creating full database schema...");
            createFullSchema(stmt);
            System.out.println("Successfully created and initialized new local database.");

        } catch (SQLException e) {
            System.err.println("FATAL: Could not create or initialize the local fallback database. Error: " + e.getMessage());
        }
    }

    private static void createFullSchema(Statement stmt) throws SQLException {
        stmt.execute("PRAGMA foreign_keys = ON;");

        // --- TABLES ---
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS AppSettings (setting_key TEXT PRIMARY KEY NOT NULL, setting_value TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Bulk_Devices (SerialNumber TEXT PRIMARY KEY NOT NULL, IMEI TEXT UNIQUE, ICCID TEXT, Capacity TEXT, DeviceName TEXT, LastImportDate TEXT NOT NULL);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Device_Assignments (AssignmentID INTEGER PRIMARY KEY AUTOINCREMENT, SerialNumber TEXT NOT NULL, EmployeeEmail TEXT, EmployeeFirstName TEXT, EmployeeLastName TEXT, SNReferenceNumber TEXT, AssignmentDate TEXT NOT NULL, DepotOrderNumber TEXT, Exported BOOLEAN NOT NULL DEFAULT 0, FOREIGN KEY (SerialNumber) REFERENCES Bulk_Devices (SerialNumber));");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Device_Autofill_Data (serial_number TEXT PRIMARY KEY, imei TEXT, category TEXT, make TEXT, description TEXT, part_number TEXT, capacity TEXT, everon_serial BOOLEAN);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Device_Status (status_id INTEGER PRIMARY KEY, receipt_id INTEGER NOT NULL REFERENCES Receipt_Events (receipt_id), sheet_id INTEGER REFERENCES Sheets, status TEXT, sub_status TEXT, receive_date DATE, last_update DATETIME, change_log TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Disposition_Info (disposition_id INTEGER PRIMARY KEY, receipt_id INTEGER NOT NULL REFERENCES Receipt_Events (receipt_id), is_everon BOOLEAN DEFAULT (0), is_end_of_life BOOLEAN DEFAULT (0), is_under_capacity BOOLEAN DEFAULT (0), is_phone BOOLEAN DEFAULT (0), other_disqualification TEXT DEFAULT NULL, final_auto_disp TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS EOLDevice (model_name TEXT, part_numbers TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Flag_Devices (serial_number TEXT PRIMARY KEY, status TEXT, sub_status TEXT, flag_reason TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Mel_Rules (model_number TEXT, description TEXT, action TEXT, special_notes TEXT, manufac TEXT, redeploy_threshold TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Packages (package_id INTEGER PRIMARY KEY, tracking_number VARCHAR (50) NOT NULL UNIQUE, first_name VARCHAR (50), last_name VARCHAR (50), city VARCHAR (100), state VARCHAR (2), zip_code VARCHAR (10), receive_date DATE NOT NULL);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Physical_Assets (asset_id INTEGER PRIMARY KEY AUTOINCREMENT, serial_number TEXT NOT NULL UNIQUE, imei TEXT UNIQUE, category VARCHAR (250), make TEXT, description TEXT, part_number TEXT, capacity TEXT, everon_serial BOOLEAN NOT NULL DEFAULT 0);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Receipt_Events (receipt_id INTEGER PRIMARY KEY AUTOINCREMENT, serial_number TEXT NOT NULL REFERENCES Physical_Assets (serial_number), package_id INTEGER NOT NULL REFERENCES Packages (package_id), IMEI NUMERIC, category VARCHAR (50), make VARCHAR (50), model_number VARCHAR (50), description TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Return_Labels (tracking_number NUMERIC, contact_name TEXT, email_address TEXT, city TEXT, state TEXT, zip_code NUMERIC, log_date DATE);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS Sheets (sheet_id INTEGER PRIMARY KEY, sheet_name VARCHAR (100) NOT NULL UNIQUE);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS SKU_Table (sku_number TEXT, model_number TEXT, category TEXT, manufac TEXT, description TEXT);");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ZipCodeData (zip_code TEXT PRIMARY KEY, zip_type TEXT, primary_city TEXT, state_code TEXT, country_code TEXT);");

        // --- INDEXES ---
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_disposition_receipt ON Disposition_Info (receipt_id);");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_package_tracking ON Packages(tracking_number);");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_receipt_event_package ON Receipt_Events (package_id);");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_receipt_event_serial ON Receipt_Events (serial_number);");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_status_receipt ON Device_Status (receipt_id);");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_zipcodedata_state_city ON ZipCodeData (state_code, primary_city);");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_device_status_status ON Device_Status (status);");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_device_status_last_update ON Device_Status (last_update);");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_device_status_status_update ON Device_Status (status, last_update);");

        // --- TRIGGERS ---
        stmt.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS Flag_Device_New
            AFTER INSERT ON Disposition_Info FOR EACH ROW
            BEGIN
                UPDATE Device_Status
                   SET status = (SELECT fd.status FROM Flag_Devices fd JOIN Receipt_Events re ON re.serial_number = fd.serial_number WHERE re.receipt_id = NEW.receipt_id),
                       sub_status = (SELECT fd.sub_status FROM Flag_Devices fd JOIN Receipt_Events re ON re.serial_number = fd.serial_number WHERE re.receipt_id = NEW.receipt_id),
                       last_update = datetime('now')
                 WHERE receipt_id = NEW.receipt_id AND
                       EXISTS (SELECT 1 FROM Receipt_Events re JOIN Flag_Devices fd ON fd.serial_number = re.serial_number WHERE re.receipt_id = NEW.receipt_id);
            END;
        """);
        stmt.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS update_status_time_new
            AFTER UPDATE ON Device_Status FOR EACH ROW
            WHEN NEW.status IS NOT OLD.status OR NEW.sub_status IS NOT OLD.sub_status
            BEGIN
                UPDATE Device_Status
                   SET last_update = datetime('now')
                 WHERE receipt_id = NEW.receipt_id;
            END;
        """);
    }
}