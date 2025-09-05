package assettracking.dao;

import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
}