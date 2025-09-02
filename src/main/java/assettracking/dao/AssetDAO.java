package assettracking.dao;

import assettracking.db.DatabaseConnection;
import assettracking.data.AssetInfo;
import assettracking.data.MelRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AssetDAO {

    // (This findAssetBySerialNumber method is the correct, intelligent version from our last conversation)
    public Optional<AssetInfo> findAssetBySerialNumber(String serialNumber) {
        AssetInfo asset = null;

        String sqlAutofill = "SELECT serial_number, make, part_number, description, category, imei, everon_serial, capacity FROM Device_Autofill_Data WHERE serial_number = ?";        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlAutofill)) {
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
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlPhysicalAssets)) {
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
     * CORRECTED: The filter `AND sku_number IS NOT NULL AND sku_number != ''` has been REMOVED.
     * This ensures ALL descriptions from the SKU_Table are available for autofill during intake.
     */
    public List<String> findDescriptionsLike(String descriptionFragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = "SELECT DISTINCT description FROM SKU_Table WHERE description LIKE ? ORDER BY description LIMIT 10";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + descriptionFragment + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("description"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return suggestions;
    }

    /**
     * CORRECTED: The filter `AND sku_number IS NOT NULL AND sku_number != ''` has been REMOVED.
     * This ensures ALL model numbers from the SKU_Table are available for autofill during intake.
     */
    public List<String> findModelNumbersLike(String modelFragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = "SELECT DISTINCT model_number FROM SKU_Table WHERE (model_number LIKE ? OR description LIKE ?) ORDER BY model_number LIMIT 10";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String searchTerm = "%" + modelFragment + "%";
            stmt.setString(1, searchTerm);
            stmt.setString(2, searchTerm);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("model_number"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return suggestions;
    }

    // --- (The rest of the file is unchanged) ---

    public Optional<AssetInfo> findSkuByModelAndCondition(String modelNumber, boolean isRefurbished) {
        String conditionSearchTerm = isRefurbished ? "%RFB%" : "%NEW%";
        String antiConditionSearchTerm = isRefurbished ? "%NEW%" : "%RFB%";
        String sql = "SELECT sku_number, description FROM SKU_Table " +
                "WHERE model_number = ? " +
                "AND description LIKE ? " +
                "AND description NOT LIKE ? " +
                "LIMIT 1";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<String> findDescriptionBySkuNumber(String skuNumber) {
        String sql = "SELECT description FROM SKU_Table WHERE sku_number = ? AND (model_number IS NULL OR model_number = '')";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, skuNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("description"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public void addAsset(AssetInfo asset) {
        String sql = "INSERT INTO Physical_Assets (serial_number, imei, category, make, description, part_number, capacity, everon_serial) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<MelRule> findMelRule(String modelNumber, String description) {
        String sql = "SELECT model_number, action, special_notes FROM Mel_Rules WHERE model_number = ? OR description = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modelNumber);
            stmt.setString(2, description);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new MelRule(
                            rs.getString("model_number"),
                            rs.getString("action"),
                            rs.getString("special_notes")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Set<String> getAllDistinctCategories() {
        Set<String> categories = new TreeSet<>();
        String sql = "SELECT DISTINCT category FROM Physical_Assets WHERE category IS NOT NULL AND category != '' " +
                "UNION " +
                "SELECT DISTINCT category FROM SKU_Table WHERE category IS NOT NULL AND category != '' " +
                "ORDER BY category";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return categories;
    }
}