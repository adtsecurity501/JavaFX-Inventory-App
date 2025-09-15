package assettracking.dao;

import assettracking.data.Sku;
import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class SkuDAO {

    public List<Sku> getAllSkus() {
        List<Sku> skus = new ArrayList<>();
        String sql = "SELECT sku_number, model_number, category, manufac, description FROM sku_table ORDER BY sku_number";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                skus.add(mapRowToSku(rs));
            }
        } catch (SQLException e) {
            System.err.println("Database error getting all SKUs: " + e.getMessage());
        }
        return skus;
    }

    public Optional<Sku> findSkuByNumber(String skuNumber) {
        String sql = "SELECT sku_number, model_number, category, manufac, description FROM sku_table WHERE sku_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, skuNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToSku(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error finding SKU by number: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean addSku(Sku sku) {
        String sql = "INSERT INTO sku_table (sku_number, model_number, category, manufac, description) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sku.getSkuNumber());
            stmt.setString(2, sku.getModelNumber());
            stmt.setString(3, sku.getCategory());
            stmt.setString(4, sku.getManufacturer());
            stmt.setString(5, sku.getDescription());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error adding SKU: " + e.getMessage());
            return false;
        }
    }

    public boolean updateSku(Sku sku) {
        String sql = "UPDATE sku_table SET model_number = ?, category = ?, manufac = ?, description = ? WHERE sku_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sku.getModelNumber());
            stmt.setString(2, sku.getCategory());
            stmt.setString(3, sku.getManufacturer());
            stmt.setString(4, sku.getDescription());
            stmt.setString(5, sku.getSkuNumber());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error updating SKU: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteSku(String skuNumber) {
        String sql = "DELETE FROM sku_table WHERE sku_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, skuNumber);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error deleting SKU: " + e.getMessage());
            return false;
        }
    }

    public List<String> findSkusByKeywordOrSkuNumber(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> suggestions = new LinkedHashSet<>(findSkusWithKeywords(query));

        String skuSearchSql = "SELECT sku_number, description FROM sku_table " +
                "WHERE sku_number ILIKE ? " +
                "AND sku_number IS NOT NULL AND sku_number != '' LIMIT 15";

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(skuSearchSql)) {
            stmt.setString(1, "%" + query + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(formatSkuSuggestion(rs.getString("sku_number"), rs.getString("description")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during SKU number search: " + e.getMessage());
        }
        return new ArrayList<>(suggestions);
    }

    public List<String> findSkusWithKeywords(String keywords) {
        List<String> suggestions = new ArrayList<>();
        if (keywords == null || keywords.trim().isEmpty()) {
            return suggestions;
        }
        String[] keywordArray = keywords.trim().split("\\s+");

        String sqlBuilder = "SELECT sku_number, description FROM sku_table " +
                "WHERE sku_number IS NOT NULL AND sku_number != '' " + "AND description ILIKE ? ".repeat(keywordArray.length) +
                "LIMIT 15";

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlBuilder)) {
            for (int i = 0; i < keywordArray.length; i++) {
                stmt.setString(i + 1, "%" + keywordArray[i] + "%");
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(formatSkuSuggestion(rs.getString("sku_number"), rs.getString("description")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during keyword search: " + e.getMessage());
        }
        return suggestions;
    }

    public List<String> findSkusLike(String fragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = "SELECT sku_number, description FROM SKU_Table " +
                "WHERE (sku_number ILIKE ? OR description ILIKE ?) " +
                "LIMIT 15";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String queryFragment = "%" + fragment + "%";
            stmt.setString(1, queryFragment);
            stmt.setString(2, queryFragment);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(formatSkuSuggestion(rs.getString("sku_number"), rs.getString("description")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error finding SKUs: " + e.getMessage());
        }
        return suggestions;
    }

    public List<String> findSkusWithSkuNumberLike(String fragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = "SELECT sku_number, description FROM SKU_Table " +
                "WHERE (sku_number ILIKE ? OR description ILIKE ?) " +
                "AND sku_number IS NOT NULL AND sku_number != '' " +
                "LIMIT 15";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String queryFragment = "%" + fragment + "%";
            stmt.setString(1, queryFragment);
            stmt.setString(2, queryFragment);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(formatSkuSuggestion(rs.getString("sku_number"), rs.getString("description")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error finding printable SKUs: " + e.getMessage());
        }
        return suggestions;
    }

    public List<String> findDistinctValuesLike(String columnName, String fragment) {
        List<String> suggestions = new ArrayList<>();
        if (!columnName.matches("^[a-zA-Z0-9_]+$")) {
            return suggestions;
        }
        String sql = String.format(
                "SELECT DISTINCT %s FROM sku_table WHERE %s IS NOT NULL AND %s != '' AND %s ILIKE ? ORDER BY %s LIMIT 10",
                columnName, columnName, columnName, columnName, columnName
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
            System.err.println("Database error finding distinct values: " + e.getMessage());
        }
        return suggestions;
    }

    private String formatSkuSuggestion(String sku, String description) {
        String desc = (description == null || description.trim().isEmpty()) ? "No Description" : description;
        return sku + " - " + desc;
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