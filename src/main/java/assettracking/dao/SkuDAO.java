package assettracking.dao;

import assettracking.data.Sku;
import assettracking.db.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SkuDAO {

    public List<Sku> getAllSkus() {
        List<Sku> skus = new ArrayList<>();
        String sql = "SELECT sku_number, model_number, category, manufac, description FROM SKU_Table ORDER BY sku_number";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                skus.add(mapRowToSku(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return skus;
    }

    public Optional<Sku> findSkuByNumber(String skuNumber) {
        String sql = "SELECT sku_number, model_number, category, manufac, description FROM SKU_Table WHERE sku_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, skuNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToSku(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public boolean addSku(Sku sku) {
        String sql = "INSERT INTO SKU_Table (sku_number, model_number, category, manufac, description) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sku.getSkuNumber());
            stmt.setString(2, sku.getModelNumber());
            stmt.setString(3, sku.getCategory());
            stmt.setString(4, sku.getManufacturer());
            stmt.setString(5, sku.getDescription());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateSku(Sku sku) {
        String sql = "UPDATE SKU_Table SET model_number = ?, category = ?, manufac = ?, description = ? WHERE sku_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sku.getModelNumber());
            stmt.setString(2, sku.getCategory());
            stmt.setString(3, sku.getManufacturer());
            stmt.setString(4, sku.getDescription());
            stmt.setString(5, sku.getSkuNumber());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteSku(String skuNumber) {
        String sql = "DELETE FROM SKU_Table WHERE sku_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, skuNumber);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Finds SKUs where the SKU number or description matches the given fragment.
     * Returns a list of formatted strings "SKU - Description" for display in suggestions.
     * @param fragment The text typed by the user.
     * @return A list of matching SKU suggestions.
     */
    public List<String> findSkusLike(String fragment) {
        List<String> suggestions = new ArrayList<>();
        // This query is now corrected to find all items where SKU or description matches.
        String sql = "SELECT sku_number, description FROM SKU_Table " +
                "WHERE (sku_number LIKE ? OR description LIKE ?) " +
                "LIMIT 15";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String queryFragment = "%" + fragment + "%";
            stmt.setString(1, queryFragment);
            stmt.setString(2, queryFragment);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String sku = rs.getString("sku_number");
                    String description = rs.getString("description");

                    // Skip any rows that have no description.
                    if (description == null || description.trim().isEmpty()) {
                        continue;
                    }

                    // If a SKU exists, format it with a separator.
                    if (sku != null && !sku.trim().isEmpty()) {
                        suggestions.add(sku + " - " + description);
                    } else {
                        // If no SKU exists (e.g., a pure kit description), just add the description.
                        suggestions.add(description);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return suggestions;
    }

    /**
     * Finds SKUs that have a non-empty sku_number, specifically for label printing.
     * Returns a list of formatted strings "SKU - Description" for display in suggestions.
     * @param fragment The text typed by the user.
     * @return A list of matching SKU suggestions.
     */
    public List<String> findSkusWithSkuNumberLike(String fragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = "SELECT sku_number, description FROM SKU_Table " +
                "WHERE (sku_number LIKE ? OR description LIKE ?) " +
                "AND sku_number IS NOT NULL AND sku_number != '' " + // Ensures only printable SKUs are returned
                "LIMIT 15";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String queryFragment = "%" + fragment + "%";
            stmt.setString(1, queryFragment);
            stmt.setString(2, queryFragment);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String sku = rs.getString("sku_number");
                    String description = rs.getString("description");
                    if (description == null || description.trim().isEmpty()) {
                        description = "No Description";
                    }
                    suggestions.add(sku + " - " + description);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return suggestions;
    }

    private Sku mapRowToSku(ResultSet rs) throws SQLException {
        Sku sku = new Sku();
        sku.setSkuNumber(rs.getString("sku_number"));
        sku.setModelNumber(rs.getString("model_number"));
        sku.setCategory(rs.getString("category"));
        sku.setManufacturer(rs.getString("manufac"));
        sku.setDescription(rs.getString("description"));
        return sku;
    }
}