package assettracking.dao;

import assettracking.data.FlaggedDeviceData;
import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FlaggedDeviceDAO {

    private static final String NO_REMOVE_TAG = "[NOREMOVE]";

    public boolean flagDevice(String serialNumber, String reason) {
        String sql = "MERGE INTO Flag_Devices (serial_number, status, sub_status, flag_reason) " +
                "KEY(serial_number) " +
                "VALUES (?, 'Flag!', 'Requires Review', ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serialNumber);
            stmt.setString(2, reason);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Database error during flag operation: " + e.getMessage());
            return false;
        }
    }

    public List<FlaggedDeviceData> getAllFlags() {
        List<FlaggedDeviceData> flags = new ArrayList<>();
        String sql = "SELECT serial_number, flag_reason FROM Flag_Devices ORDER BY serial_number";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String serial = rs.getString("serial_number");
                String reason = rs.getString("flag_reason");
                boolean preventsRemoval = reason != null && reason.startsWith(NO_REMOVE_TAG);
                flags.add(new FlaggedDeviceData(serial, reason, preventsRemoval));
            }
        } catch (SQLException e) {
            System.err.println("Database error getting all flags: " + e.getMessage());
        }
        return flags;
    }

    public Optional<FlaggedDeviceData> getFlagBySerial(String serialNumber) {
        String sql = "SELECT flag_reason FROM Flag_Devices WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String reason = rs.getString("flag_reason");
                    boolean preventsRemoval = reason != null && reason.startsWith(NO_REMOVE_TAG);
                    return Optional.of(new FlaggedDeviceData(serialNumber, reason, preventsRemoval));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error getting flag by serial: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean unflagDevice(String serialNumber) {
        String sql = "DELETE FROM Flag_Devices WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error unflagging device: " + e.getMessage());
            return false;
        }
    }

    public boolean isAutoRemovalPrevented(String serialNumber) {
        String sql = "SELECT flag_reason FROM Flag_Devices WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String reason = rs.getString("flag_reason");
                    return reason != null && reason.startsWith(NO_REMOVE_TAG);
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error checking for auto-removal prevention: " + e.getMessage());
        }
        return false;
    }
}