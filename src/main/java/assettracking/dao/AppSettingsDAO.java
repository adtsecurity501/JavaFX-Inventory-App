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
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Saves or updates a setting in the database.
     * @param key The name of the setting (e.g., "device_goal").
     * @param value The value to save.
     */
    public void saveSetting(String key, String value) {
        // This is the standard "UPSERT" syntax for PostgreSQL
        String sql = "INSERT INTO AppSettings (setting_key, setting_value) VALUES (?, ?) " +
                "ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}