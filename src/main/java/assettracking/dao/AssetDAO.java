package assettracking.dao;

import assettracking.db.DatabaseConnection;
import assettracking.data.AssetInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AssetDAO {

    public Optional<AssetInfo> findAssetBySerialNumber(String serialNumber) {
        String sql = "SELECT serial_number, make, part_number, description, category, imei, everon_serial, capacity FROM Physical_Assets WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        } catch (SQLException e) {
            System.err.println("Error finding asset by serial number '" + serialNumber + "': " + e.getMessage());
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public void addAsset(AssetInfo asset) {
        String sql = "INSERT INTO Physical_Assets (serial_number, imei, category, make, description, part_number, capacity, everon_serial) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, asset.getSerialNumber());

            String imei = asset.getImei();
            if (imei != null && imei.isEmpty()) {
                stmt.setNull(2, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(2, imei);
            }

            stmt.setString(3, asset.getCategory());
            stmt.setString(4, asset.getMake());
            stmt.setString(5, asset.getDescription());
            stmt.setString(6, asset.getModelNumber());
            stmt.setString(7, asset.getCapacity());
            stmt.setBoolean(8, asset.isEveronSerial());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding physical asset: " + e.getMessage());
            e.printStackTrace();
        }
    }

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

    public List<String> findModelNumbersLike(String modelFragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = "SELECT DISTINCT model_number FROM SKU_Table WHERE model_number LIKE ? ORDER BY model_number LIMIT 10";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + modelFragment + "%");
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

    // New method to find action from Mel_Rules
    public Optional<String> findActionFromMelRules(String modelNumber, String description) {
        String sql = "SELECT action FROM Mel_Rules WHERE model_number = ? OR description = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modelNumber);
            stmt.setString(2, description);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("action"));
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