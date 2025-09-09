package assettracking.dao;

import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

public class FlaggedDeviceDAO {

    public boolean flagDevice(String serialNumber, String reason) {
        String sql = "INSERT INTO flag_devices (serial_number, status, sub_status, flag_reason) " +
                "VALUES (?, 'Flag!', 'Requires Review', ?) " +
                "ON CONFLICT (serial_number) DO UPDATE SET " +
                "status = EXCLUDED.status, " +
                "sub_status = EXCLUDED.sub_status, " +
                "flag_reason = EXCLUDED.flag_reason";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            stmt.setString(2, reason);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Database error flagging device: " + e.getMessage());
            return false;
        }
    }

    public List<AbstractMap.SimpleEntry<String, String>> getAllFlags() {
        List<AbstractMap.SimpleEntry<String, String>> flags = new ArrayList<>();
        String sql = "SELECT serial_number, flag_reason FROM flag_devices ORDER BY serial_number";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                flags.add(new AbstractMap.SimpleEntry<>(rs.getString("serial_number"), rs.getString("flag_reason")));
            }
        } catch (SQLException e) {
            System.err.println("Database error getting all flags: " + e.getMessage());
        }
        return flags;
    }

    public boolean unflagDevice(String serialNumber) {
        String sql = "DELETE FROM flag_devices WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error unflagging device: " + e.getMessage());
            return false;
        }
    }
}