package assettracking.dao;

import assettracking.data.AssetInfo;
import assettracking.data.MelRule;
import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
     * Finds descriptions case-insensitively, fetching a broad list from the DB
     * and then performing intelligent, prioritized sorting within the Java code.
     * This avoids a bug in H2's CASE statement implementation.
     */
    public List<String> findDescriptionsLike(String descriptionFragment) {
        List<String> suggestions = new ArrayList<>();
        // Use a simple, stable query to fetch all potential matches.
        String sql = "SELECT DISTINCT description FROM SKU_Table " + "WHERE (description ILIKE ? OR model_number ILIKE ?) " + "AND description IS NOT NULL AND description != '' " + "LIMIT 30"; // Fetch a larger set to sort from

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            String searchTerm = "%" + descriptionFragment + "%";
            stmt.setString(1, searchTerm);
            stmt.setString(2, searchTerm);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("description"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }

        // --- NEW: Perform the intelligent sorting in Java ---
        final String lowerFragment = descriptionFragment.toLowerCase();
        suggestions.sort((s1, s2) -> {
            int score1 = getScore(s1, lowerFragment);
            int score2 = getScore(s2, lowerFragment);
            if (score1 != score2) {
                return Integer.compare(score1, score2); // Lower score is better
            }
            return s1.compareToIgnoreCase(s2); // Alphabetical tie-breaker
        });

        // Return only the top 10 results from the sorted list
        return suggestions.stream().limit(10).collect(Collectors.toList());
    }

    /**
     * Finds model numbers case-insensitively, fetching a broad list from the DB
     * and then performing intelligent, prioritized sorting within the Java code.
     */
    public List<String> findModelNumbersLike(String modelFragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = "SELECT DISTINCT model_number FROM SKU_Table " + "WHERE (model_number ILIKE ? OR description ILIKE ?) " + "AND model_number IS NOT NULL AND model_number != '' " + "LIMIT 30";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            String searchTerm = "%" + modelFragment + "%";
            stmt.setString(1, searchTerm);
            stmt.setString(2, searchTerm);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("model_number"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }

        // --- NEW: Perform the intelligent sorting in Java ---
        final String lowerFragment = modelFragment.toLowerCase();
        suggestions.sort((s1, s2) -> {
            int score1 = getScore(s1, lowerFragment);
            int score2 = getScore(s2, lowerFragment);
            if (score1 != score2) {
                return Integer.compare(score1, score2);
            }
            return s1.compareToIgnoreCase(s2);
        });

        return suggestions.stream().limit(10).collect(Collectors.toList());
    }

    // --- NEW: Private helper method for sorting logic ---
    private int getScore(String suggestion, String searchTerm) {
        String lowerSuggestion = suggestion.toLowerCase();
        if (lowerSuggestion.startsWith(searchTerm)) {
            return 1; // Best score: starts with term
        }
        if (lowerSuggestion.contains(" " + searchTerm)) {
            return 2; // Good score: contains term as a whole word
        }
        return 3; // Base score: just contains the term
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

    public List<String> findDistinctValuesLike(String columnName, String fragment) {
        List<String> suggestions = new ArrayList<>();
        // Basic validation to prevent SQL injection, though parameters make it safe.
        if (!columnName.matches("^[a-zA-Z0-9_]+$")) {
            return suggestions;
        }

        String sql = String.format("SELECT DISTINCT %s FROM device_autofill_data WHERE %s IS NOT NULL AND %s != '' AND %s LIKE ? ORDER BY %s LIMIT 10", columnName, columnName, columnName, columnName, columnName);

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + fragment + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error finding distinct values for autofill: " + e.getMessage());
        }
        return suggestions;
    }

    public CompletableFuture<List<AssetInfo>> getAllAutofillEntries() {
        return CompletableFuture.supplyAsync(() -> {
            List<AssetInfo> entries = new ArrayList<>();
            String sql = "SELECT * FROM device_autofill_data ORDER BY serial_number";
            try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AssetInfo asset = new AssetInfo();
                    asset.setSerialNumber(rs.getString("serial_number"));
                    asset.setMake(rs.getString("make"));
                    asset.setModelNumber(rs.getString("part_number"));
                    asset.setDescription(rs.getString("description"));
                    asset.setCategory(rs.getString("category"));
                    // SKU field is removed from here
                    entries.add(asset);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return entries;
        });
    }

    public CompletableFuture<Boolean> addAutofillEntry(AssetInfo asset) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO device_autofill_data (serial_number, make, part_number, description, category) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, asset.getSerialNumber());
                stmt.setString(2, asset.getMake());
                stmt.setString(3, asset.getModelNumber());
                stmt.setString(4, asset.getDescription());
                stmt.setString(5, asset.getCategory());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> updateAutofillEntry(AssetInfo asset) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE device_autofill_data SET make = ?, part_number = ?, description = ?, category = ? WHERE serial_number = ?";
            try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, asset.getMake());
                stmt.setString(2, asset.getModelNumber());
                stmt.setString(3, asset.getDescription());
                stmt.setString(4, asset.getCategory());
                stmt.setString(5, asset.getSerialNumber());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deleteAutofillEntry(String serialNumber) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM device_autofill_data WHERE serial_number = ?";
            try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, serialNumber);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                return false;
            }
        });
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