package assettracking.dao;

import assettracking.data.AssetInfo;
import assettracking.data.MelRule;
import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AssetDAO {

    // (This findAssetBySerialNumber method is the correct, intelligent version from our last conversation)
    public Optional<AssetInfo> findAssetBySerialNumber(String serialNumber) {
        AssetInfo asset = null;

        String sqlAutofill = "SELECT serial_number, make, part_number, description, category, imei, everon_serial, capacity FROM Device_Autofill_Data WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sqlAutofill)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    asset = new AssetInfo();
                    asset.setSerialNumber(rs.getString("serial_number"));
                    asset.setMake(rs.getString("make"));
                    asset.setModelNumber(rs.getString("part_number"));
                    asset.setDescription(rs.getString("description"));
                    asset.setCategory(rs.getString("category"));
                    asset.setImei(rs.getString("imei"));
                    asset.setEveronSerial(rs.getBoolean("everon_serial"));
                    asset.setCapacity(rs.getString("capacity"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding asset in Device_Autofill_Data for serial '" + serialNumber + "': " + e.getMessage());
        }

        String sqlPhysicalAssets = "SELECT serial_number, make, part_number, description, category, imei, everon_serial, capacity FROM Physical_Assets WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sqlPhysicalAssets)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (asset == null) {
                        asset = new AssetInfo();
                        asset.setSerialNumber(rs.getString("serial_number"));
                    }
                    String physicalMake = rs.getString("make");
                    if (physicalMake != null && !physicalMake.isEmpty()) asset.setMake(physicalMake);
                    String physicalModel = rs.getString("part_number");
                    if (physicalModel != null && !physicalModel.isEmpty()) asset.setModelNumber(physicalModel);
                    String physicalDesc = rs.getString("description");
                    if (physicalDesc != null && !physicalDesc.isEmpty()) asset.setDescription(physicalDesc);
                    String physicalCat = rs.getString("category");
                    if (physicalCat != null && !physicalCat.isEmpty()) asset.setCategory(physicalCat);
                    String physicalImei = rs.getString("imei");
                    if (physicalImei != null && !physicalImei.isEmpty()) asset.setImei(physicalImei);
                    asset.setEveronSerial(rs.getBoolean("everon_serial"));
                    asset.setCapacity(rs.getString("capacity"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding asset in Physical_Assets for serial '" + serialNumber + "': " + e.getMessage());
        }

        return Optional.ofNullable(asset);
    }

    /**
     * Finds descriptions case-insensitively, ranking results that start with the fragment higher.
     */
    public List<String> findDescriptionsLike(String descriptionFragment) {
        List<String> suggestions = new ArrayList<>();
        // This query now uses LOWER() on all columns and parameters for a case-insensitive search.
        String sql = "SELECT DISTINCT description, " + "CASE " + "    WHEN LOWER(description) LIKE ? THEN 1 " + // Priority 1: Starts with the term
                "    WHEN LOWER(description) LIKE ? THEN 2 " + // Priority 2: Contains the term as a whole word
                "    ELSE 3 " +                                // Priority 3: Just contains the term
                "END AS priority " + "FROM SKU_Table " + "WHERE (LOWER(description) LIKE ? OR LOWER(model_number) LIKE ?) " + "AND description IS NOT NULL AND description != '' " + "ORDER BY priority, description " + "LIMIT 10";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Convert search terms to lowercase here
            String lowerFragment = descriptionFragment.toLowerCase();
            String startsWithTerm = lowerFragment + "%";
            String wholeWordTerm = "% " + lowerFragment + "%";
            String searchTerm = "%" + lowerFragment + "%";

            // Set the parameters in the correct order for the query
            stmt.setString(1, startsWithTerm);      // For priority 1
            stmt.setString(2, wholeWordTerm);      // For priority 2
            stmt.setString(3, searchTerm);         // For WHERE clause (description)
            stmt.setString(4, searchTerm);         // For WHERE clause (model_number)

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("description"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return suggestions;
    }

    /**
     * Finds model numbers case-insensitively, ranking results that start with the fragment higher.
     */
    public List<String> findModelNumbersLike(String modelFragment) {
        List<String> suggestions = new ArrayList<>();
        // This query also uses LOWER() for a case-insensitive search.
        String sql = "SELECT DISTINCT model_number, " + "CASE " + "    WHEN LOWER(model_number) LIKE ? THEN 1 " + // Priority 1: Starts with the term
                "    WHEN LOWER(model_number) LIKE ? THEN 2 " + // Priority 2: Contains the term as a whole word
                "    ELSE 3 " +                                // Priority 3: Just contains the term
                "END AS priority " + "FROM SKU_Table " + "WHERE (LOWER(model_number) LIKE ? OR LOWER(description) LIKE ?) " + "AND model_number IS NOT NULL AND model_number != '' " + "ORDER BY priority, model_number " + "LIMIT 10";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Convert search terms to lowercase here
            String lowerFragment = modelFragment.toLowerCase();
            String startsWithTerm = lowerFragment + "%";
            String wholeWordTerm = "% " + lowerFragment + "%";
            String searchTerm = "%" + lowerFragment + "%";

            // Set the parameters in the correct order for the query
            stmt.setString(1, startsWithTerm);      // For priority 1
            stmt.setString(2, wholeWordTerm);      // For priority 2
            stmt.setString(3, searchTerm);         // For WHERE clause (model_number)
            stmt.setString(4, searchTerm);         // For WHERE clause (description)

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("model_number"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return suggestions;
    }

    // New method that operates within an existing transaction
    public Optional<AssetInfo> findAssetBySerialNumber(Connection conn, String serialNumber) throws SQLException {
        // This query is simplified but functionally the same as your existing one.
        // It's designed to run on an existing connection.
        String sql = "SELECT * FROM physical_assets WHERE serial_number = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AssetInfo asset = new AssetInfo();
                    asset.setSerialNumber(rs.getString("serial_number"));
                    asset.setMake(rs.getString("make"));
                    asset.setModelNumber(rs.getString("part_number"));
                    asset.setDescription(rs.getString("description"));
                    asset.setCategory(rs.getString("category"));
                    asset.setImei(rs.getString("imei"));
                    asset.setEveronSerial(rs.getBoolean("everon_serial"));
                    asset.setCapacity(rs.getString("capacity"));
                    return Optional.of(asset);
                }
            }
        }
        return Optional.empty();
    }

    // New method that operates within an existing transaction
    public void addAsset(Connection conn, AssetInfo asset) throws SQLException {
        String sql = "INSERT INTO Physical_Assets (serial_number, imei, category, make, description, part_number) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, asset.getSerialNumber());
            stmt.setString(2, asset.getImei());
            stmt.setString(3, asset.getCategory());
            stmt.setString(4, asset.getMake());
            stmt.setString(5, asset.getDescription());
            stmt.setString(6, asset.getModelNumber());
            stmt.executeUpdate();
        }
    }

    public Optional<AssetInfo> findSkuByModelAndCondition(String modelNumber, boolean isRefurbished) {
        String conditionSearchTerm = isRefurbished ? "%RFB%" : "%NEW%";
        String antiConditionSearchTerm = isRefurbished ? "%NEW%" : "%RFB%";
        String sql = "SELECT sku_number, description FROM SKU_Table " + "WHERE model_number = ? " + "AND description LIKE ? " + "AND description NOT LIKE ? " + "LIMIT 1";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modelNumber);
            stmt.setString(2, conditionSearchTerm);
            stmt.setString(3, antiConditionSearchTerm);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AssetInfo assetInfo = new AssetInfo();
                    assetInfo.setModelNumber(modelNumber);
                    assetInfo.setSkuNumber(rs.getString("sku_number"));
                    assetInfo.setDescription(rs.getString("description"));
                    return Optional.of(assetInfo);
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean updateAsset(AssetInfo asset) {
        // This MERGE command is an "UPSERT" for H2. It updates a record if it exists,
        // or inserts it if it does not. This prevents the silent-fail scenario.
        String sql = "MERGE INTO Physical_Assets (serial_number, category, make, part_number, description, imei) " + "KEY(serial_number) " + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, asset.getSerialNumber());
            stmt.setString(2, asset.getCategory());
            stmt.setString(3, asset.getMake());
            stmt.setString(4, asset.getModelNumber());
            stmt.setString(5, asset.getDescription());
            stmt.setString(6, asset.getImei());

            // An upsert will return 1 for an insert or an update.
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error during asset upsert: " + e.getMessage());
            return false;
        }
    }

    public Optional<String> findDescriptionBySkuNumber(String skuNumber) {
        String sql = "SELECT description FROM SKU_Table WHERE sku_number = ? AND (model_number IS NULL OR model_number = '')";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, skuNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("description"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public void addAsset(AssetInfo asset) {
        String sql = "INSERT INTO Physical_Assets (serial_number, imei, category, make, description, part_number, capacity, everon_serial) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, asset.getSerialNumber());
            stmt.setString(2, asset.getImei());
            stmt.setString(3, asset.getCategory());
            stmt.setString(4, asset.getMake());
            stmt.setString(5, asset.getDescription());
            stmt.setString(6, asset.getModelNumber());
            stmt.setString(7, asset.getCapacity());
            stmt.setBoolean(8, asset.isEveronSerial());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding physical asset: " + e.getMessage());
        }
    }

    public Optional<AssetInfo> findSkuDetails(String value, String lookupType) {
        String sql;
        if ("description".equalsIgnoreCase(lookupType)) {
            sql = "SELECT category, model_number, description, manufac AS make FROM SKU_Table WHERE description = ?";
        } else if ("model_number".equalsIgnoreCase(lookupType)) {
            sql = "SELECT category, model_number, description, manufac AS make FROM SKU_Table WHERE model_number = ?";
        } else {
            return Optional.empty();
        }

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AssetInfo asset = new AssetInfo();
                    asset.setCategory(rs.getString("category"));
                    asset.setModelNumber(rs.getString("model_number"));
                    asset.setDescription(rs.getString("description"));
                    asset.setMake(rs.getString("make"));
                    return Optional.of(asset);
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<MelRule> findMelRule(String modelNumber, String description) {
        String sql = "SELECT model_number, action, special_notes FROM Mel_Rules WHERE model_number = ? OR description = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modelNumber);
            stmt.setString(2, description);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new MelRule(rs.getString("model_number"), rs.getString("action"), rs.getString("special_notes")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Set<String> getAllDistinctCategories() {
        Set<String> categories = new TreeSet<>();
        String sql = "SELECT DISTINCT category FROM Physical_Assets WHERE category IS NOT NULL AND category != '' " + "UNION " + "SELECT DISTINCT category FROM SKU_Table WHERE category IS NOT NULL AND category != '' " + "ORDER BY category";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return categories;
    }
}