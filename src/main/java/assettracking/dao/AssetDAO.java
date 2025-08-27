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

    public Optional<AssetInfo> findAssetBySerialNumber(String serialNumber) {
        // Query 1: Check the primary inventory table first. This contains the most accurate,
        // up-to-date information for devices currently in your inventory.
        String sqlPhysicalAssets = "SELECT serial_number, make, part_number, description, category, imei, everon_serial, capacity FROM Physical_Assets WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlPhysicalAssets)) {
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
                    // If found here, we have the best data, so we return it immediately.
                    return Optional.of(asset);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding asset in Physical_Assets for serial '" + serialNumber + "': " + e.getMessage());
            // Log the error but allow the code to fall through to the next query.
        }

        // Query 2: If not found in the primary table, check the historical autofill data as a fallback.
        // This is useful for devices that are being returned or re-processed.
        String sqlAutofill = "SELECT serial_number, make, model_number, description, category, IMEI FROM Device_Autofill_Data WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlAutofill)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AssetInfo asset = new AssetInfo();
                    asset.setSerialNumber(rs.getString("serial_number"));
                    asset.setMake(rs.getString("make"));
                    asset.setModelNumber(rs.getString("model_number"));
                    asset.setDescription(rs.getString("description"));
                    asset.setCategory(rs.getString("category"));
                    asset.setImei(rs.getString("IMEI"));
                    // Set sensible defaults for fields that don't exist in the historical table.
                    asset.setEveronSerial(false);
                    asset.setCapacity(null);
                    return Optional.of(asset);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding asset in Device_Autofill_Data for serial '" + serialNumber + "': " + e.getMessage());
        }

        // If the serial number was not found in either table, return empty.
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

    /**
     * MODIFIED: This method now searches both the model_number and description fields
     * to provide a more flexible and user-friendly search for the model number field.
     */
    public List<String> findModelNumbersLike(String modelFragment) {
        List<String> suggestions = new ArrayList<>();
        // The query now checks if the fragment is in the model number OR the description
        String sql = "SELECT DISTINCT model_number FROM SKU_Table WHERE model_number LIKE ? OR description LIKE ? ORDER BY model_number LIMIT 10";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String searchTerm = "%" + modelFragment + "%";
            stmt.setString(1, searchTerm);
            stmt.setString(2, searchTerm); // Set the same term for the description search
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