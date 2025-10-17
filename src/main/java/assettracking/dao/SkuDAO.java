package assettracking.dao;

import assettracking.data.AssetInfo;
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
        String sql = "SELECT sku_number, model_number, category, manufac, description FROM SKU_Table ORDER BY sku_number";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                skus.add(mapRowToSku(rs));
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return skus;
    }

    /**
     * Finds SKUs where the SKU number matches OR the description contains all specified keywords.
     * This allows for flexible searching by either SKU or description.
     *
     * @param query The user's search input.
     * @return A list of formatted, unique SKU suggestions.
     */
    public List<String> findSkusByKeywordOrSkuNumber(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // This is your excellent implementation line!
        Set<String> suggestions = new LinkedHashSet<>(findSkusWithKeywords(query));

        // Now, we add any additional results where the SKU number itself matches.
        // The LinkedHashSet will automatically ignore duplicates.
        String skuSearchSql = "SELECT sku_number, description FROM SKU_Table " + "WHERE sku_number LIKE ? " + "AND sku_number IS NOT NULL AND sku_number != '' LIMIT 15";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(skuSearchSql)) {

            stmt.setString(1, "%" + query.toUpperCase() + "%");
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
            System.err.println("Database error during SKU number search: " + e.getMessage());
        }

        return new ArrayList<>(suggestions);
    }


    public List<String> findSkusWithKeywords(String keywords) {
        List<String> suggestions = new ArrayList<>();
        if (keywords == null || keywords.trim().isEmpty()) {
            return suggestions;
        }

        // Split the user's input into individual words
        String[] keywordArray = keywords.trim().split("\\s+");

        // Build the base of the query

        // Add a LIKE clause for each keyword
        String sqlBuilder = "SELECT sku_number, description FROM SKU_Table " + "WHERE sku_number IS NOT NULL AND sku_number != '' " + "AND UPPER(description) LIKE ? ".repeat(keywordArray.length) + "LIMIT 15";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sqlBuilder)) {

            // Bind each keyword to the prepared statement
            for (int i = 0; i < keywordArray.length; i++) {
                stmt.setString(i + 1, "%" + keywordArray[i].toUpperCase() + "%");
            }

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
            System.err.println("Database error during keyword search: " + e.getMessage());
        }
        return suggestions;
    }

    public Optional<Sku> findSkuByNumber(String skuNumber) {
        String sql = "SELECT sku_number, model_number, category, manufac, description FROM SKU_Table WHERE sku_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, skuNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToSku(rs));
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean addSku(Sku sku) {
        String sql = "INSERT INTO SKU_Table (sku_number, model_number, category, manufac, description) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sku.getSkuNumber());
            stmt.setString(2, sku.getModelNumber());
            stmt.setString(3, sku.getCategory());
            stmt.setString(4, sku.getManufacturer());
            stmt.setString(5, sku.getDescription());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            return false;
        }
    }

    public boolean updateSku(Sku sku) {
        String sql = "UPDATE SKU_Table SET model_number = ?, category = ?, manufac = ?, description = ? WHERE sku_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sku.getModelNumber());
            stmt.setString(2, sku.getCategory());
            stmt.setString(3, sku.getManufacturer());
            stmt.setString(4, sku.getDescription());
            stmt.setString(5, sku.getSkuNumber());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            return false;
        }
    }

    public List<String> findModelNumbersLike(String modelFragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = "SELECT DISTINCT model_number FROM SKU_Table " + "WHERE model_number ILIKE ? AND model_number IS NOT NULL AND model_number != '' " + "ORDER BY model_number LIMIT 10";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + modelFragment + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("model_number"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error finding model numbers: " + e.getMessage());
        }
        return suggestions;
    }

    public List<String> findDescriptionsLike(String descriptionFragment) {
        List<String> suggestions = new ArrayList<>();
        String sql = "SELECT DISTINCT description FROM SKU_Table " + "WHERE description ILIKE ? AND description IS NOT NULL AND description != '' " + "ORDER BY description LIMIT 10";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + descriptionFragment + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("description"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error finding descriptions: " + e.getMessage());
        }
        return suggestions;
    }

    public Optional<AssetInfo> findSkuDetails(String value, String lookupType) {
        String column = "model_number".equalsIgnoreCase(lookupType) ? "model_number" : "description";
        String sql = "SELECT category, model_number, description, manufac AS make FROM SKU_Table WHERE " + column + " = ?";

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
            System.err.println("Database error finding SKU details: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean deleteSku(String skuNumber) {
        String sql = "DELETE FROM SKU_Table WHERE sku_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, skuNumber);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Finds SKUs where the SKU number or description matches the given fragment.
     * Returns a list of formatted strings "SKU - Description" for display in suggestions.
     *
     * @param fragment The text typed by the user.
     * @return A list of matching SKU suggestions.
     */
    public List<String> findSkusLike(String fragment) {
        List<String> suggestions = new ArrayList<>();
        // This query is now corrected to find all items where SKU or description matches.
        String sql = "SELECT sku_number, description FROM SKU_Table " + "WHERE (sku_number LIKE ? OR description LIKE ?) " + "LIMIT 15";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
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
            System.err.println("Database error: " + e.getMessage());
        }
        return suggestions;
    }

    /**
     * Finds SKUs that have a non-empty sku_number, specifically for label printing.
     * Returns a list of formatted strings "SKU - Description" for display in suggestions.
     *
     * @param fragment The text typed by the user.
     * @return A list of matching SKU suggestions that are printable.
     */
    public List<String> findSkusWithSkuNumberLike(String fragment) {
        List<String> suggestions = new ArrayList<>();
        // This query is stricter: it ensures that the sku_number is not null or empty.
        String sql = "SELECT sku_number, description FROM SKU_Table " + "WHERE (sku_number LIKE ? OR description LIKE ?) " + "AND sku_number IS NOT NULL AND sku_number != '' " + // <-- THE CRITICAL FIX
                "LIMIT 15";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
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
            System.err.println("Database error finding printable SKUs: " + e.getMessage());
        }
        return suggestions;
    }

    public List<String> findDistinctValuesLike(String columnName, String fragment) {
        List<String> suggestions = new ArrayList<>();
        // Basic validation to prevent SQL injection, though parameters make it safe.
        if (!columnName.matches("^[a-zA-Z0-9_]+$")) {
            return suggestions;
        }

        String sql = String.format("SELECT DISTINCT %s FROM SKU_Table WHERE %s IS NOT NULL AND %s != '' AND %s LIKE ? ORDER BY %s LIMIT 10", columnName, columnName, columnName, columnName, columnName);

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + fragment + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
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