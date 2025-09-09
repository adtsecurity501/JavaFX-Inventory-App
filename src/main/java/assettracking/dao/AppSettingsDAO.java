package assettracking.dao;

import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AppSettingsDAO {

    /**
     * Retrieves a setting value from the database.
     *
     * @param key The name of the setting to retrieve (e.g., "device_goal").
     * @return An Optional containing the value if found, otherwise empty.
     */
    public Optional<String> getSetting(String key) {
        String sql = "SELECT setting_value FROM AppSettings WHERE setting_key = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("setting_value"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Saves or updates a setting in the database using H2's MERGE command.
     *
     * @param key   The name of the setting (e.g., "device_goal").
     * @param value The value to save.
     */
    public void saveSetting(String key, String value) {
        // This is the correct "UPSERT" syntax for an H2 database.
        String sql = "MERGE INTO AppSettings (setting_key, setting_value) KEY(setting_key) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Replaced printStackTrace with a more descriptive log
            System.err.println("Database error while saving setting '" + key + "': " + e.getMessage());
        }
    }
}