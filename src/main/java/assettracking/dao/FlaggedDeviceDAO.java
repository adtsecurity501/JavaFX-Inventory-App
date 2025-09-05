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

    /**
     * Inserts or updates a flag for a specific device.
     * If the serial number already exists, it updates the reason.
     * If not, it inserts a new record.
     *
     * @param serialNumber The device's serial number.
     * @param reason The reason for the flag.
     * @return true if the operation was successful, false otherwise.
     */
    public boolean flagDevice(String serialNumber, String reason) {
        // MERGE is the H2 equivalent of "UPSERT" (UPDATE or INSERT)
        // It's the same robust command used by the importer.
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
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retrieves all flagged devices from the database.
     * @return A list of pairs, where each pair contains a serial number and its flag reason.
     */
    public List<AbstractMap.SimpleEntry<String, String>> getAllFlags() {
        List<AbstractMap.SimpleEntry<String, String>> flags = new ArrayList<>();
        String sql = "SELECT serial_number, flag_reason FROM Flag_Devices ORDER BY serial_number";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                flags.add(new AbstractMap.SimpleEntry<>(rs.getString("serial_number"), rs.getString("flag_reason")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return flags;
    }

    /**
     * Removes a flag for a specific device.
     * @param serialNumber The serial number of the device to un-flag.
     * @return true if the operation was successful, false otherwise.
     */
    public boolean unflagDevice(String serialNumber) {
        String sql = "DELETE FROM Flag_Devices WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}