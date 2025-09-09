package assettracking.dao;

import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AppSettingsDAO {

    public Optional<String> getSetting(String key) {
        String sql = "SELECT setting_value FROM appsettings WHERE setting_key = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("setting_value"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error getting setting '" + key + "': " + e.getMessage());
        }
        return Optional.empty();
    }

    public void saveSetting(String key, String value) {
        String sql = "INSERT INTO appsettings (setting_key, setting_value) VALUES (?, ?) " +
                "ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Database error while saving setting '" + key + "': " + e.getMessage());
        }
    }
}