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

    public Optional<AssetInfo> findAssetBySerialNumber(String serialNumber) {
        AssetInfo asset = null;
        String sqlAutofill = "SELECT * FROM device_autofill_data WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlAutofill)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    asset = mapRowToAssetInfo(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding asset in device_autofill_data for serial '" + serialNumber + "': " + e.getMessage());
        }

        String sqlPhysicalAssets = "SELECT * FROM physical_assets WHERE serial_number = ?";
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
            System.err.println("Error finding asset in physical_assets for serial '" + serialNumber + "': " + e.getMessage());
        }
        return Optional.ofNullable(asset);
    }

    public Optional<AssetInfo> findAssetBySerialNumber(Connection conn, String serialNumber) throws SQLException {
        String sql = "SELECT * FROM physical_assets WHERE serial_number = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToAssetInfo(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<String> findDescriptionsLike(String descriptionFragment) {
        return findSuggestions("description", descriptionFragment);
    }

    public List<String> findModelNumbersLike(String modelFragment) {
        return findSuggestions("model_number", modelFragment);
    }

    private List<String> findSuggestions(String column, String fragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = String.format(
                "SELECT DISTINCT %s FROM sku_table WHERE %s ILIKE ? AND %s IS NOT NULL AND %s != '' ORDER BY %s LIMIT 10",
                column, column, column, column, column
        );
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + fragment + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error finding suggestions for " + column + ": " + e.getMessage());
        }
        return suggestions;
    }

    public boolean updateAsset(AssetInfo asset) {
        String sql = "UPDATE physical_assets SET category = ?, make = ?, part_number = ?, description = ?, imei = ? WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, asset.getCategory());
            stmt.setString(2, asset.getMake());
            stmt.setString(3, asset.getModelNumber());
            stmt.setString(4, asset.getDescription());
            stmt.setString(5, asset.getImei());
            stmt.setString(6, asset.getSerialNumber());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error updating asset: " + e.getMessage());
            return false;
        }
    }

    public void addAsset(Connection conn, AssetInfo asset) throws SQLException {
        String sql = "INSERT INTO physical_assets (serial_number, imei, category, make, description, part_number, capacity, everon_serial) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, asset.getSerialNumber());
            stmt.setString(2, asset.getImei());
            stmt.setString(3, asset.getCategory());
            stmt.setString(4, asset.getMake());
            stmt.setString(5, asset.getDescription());
            stmt.setString(6, asset.getModelNumber());
            stmt.setString(7, asset.getCapacity());
            stmt.setBoolean(8, asset.isEveronSerial());
            stmt.executeUpdate();
        }
    }

    // RESTORED this public method
    public void addAsset(AssetInfo asset) {
        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            addAsset(conn, asset);
        } catch (SQLException e) {
            System.err.println("Error adding physical asset with auto-connection: " + e.getMessage());
        }
    }

    public Optional<AssetInfo> findSkuDetails(String value, String lookupType) {
        String sql = "SELECT category, model_number, description, manufac AS make FROM sku_table WHERE " + lookupType + " = ?";
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
            System.err.println("Database error finding SKU details: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<MelRule> findMelRule(String modelNumber, String description) {
        String sql = "SELECT model_number, action, special_notes FROM mel_rules WHERE model_number = ? OR description = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modelNumber);
            stmt.setString(2, description);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new MelRule(rs.getString("model_number"), rs.getString("action"), rs.getString("special_notes")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error finding MEL rule: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Set<String> getAllDistinctCategories() {
        Set<String> categories = new TreeSet<>();
        String sql = "SELECT category FROM physical_assets WHERE category IS NOT NULL AND category != '' " +
                "UNION " +
                "SELECT category FROM sku_table WHERE category IS NOT NULL AND category != '' " +
                "ORDER BY category";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
        } catch (SQLException e) {
            System.err.println("Database error getting distinct categories: " + e.getMessage());
        }
        return categories;
    }

    public Optional<String> findDescriptionBySkuNumber(String skuNumber) {
        String sql = "SELECT description FROM sku_table WHERE sku_number = ? AND (model_number IS NULL OR model_number = '')";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, skuNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("description"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error finding description by SKU: " + e.getMessage());
        }
        return Optional.empty();
    }

    private AssetInfo mapRowToAssetInfo(ResultSet rs) throws SQLException {
        AssetInfo asset = new AssetInfo();
        asset.setSerialNumber(rs.getString("serial_number"));
        asset.setMake(rs.getString("make"));
        asset.setModelNumber(rs.getString("part_number"));
        asset.setDescription(rs.getString("description"));
        asset.setCategory(rs.getString("category"));
        asset.setImei(rs.getString("imei"));
        asset.setEveronSerial(rs.getBoolean("everon_serial"));
        asset.setCapacity(rs.getString("capacity"));
        return asset;
    }
}