package assettracking.dao.bulk;

import assettracking.data.bulk.BulkDevice;
import assettracking.data.bulk.StagedDevice;
import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class iPadProvisioningDAO {
    public void upsertBulkDevices(List<BulkDevice> devices) throws SQLException {
        // This is the correct "UPSERT" syntax for an H2 database.
        String upsertSql = "MERGE INTO Bulk_Devices (SerialNumber, IMEI, ICCID, Capacity, DeviceName, LastImportDate) " + "KEY(SerialNumber) " + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
            conn.setAutoCommit(false);
            for (BulkDevice device : devices) {
                stmt.setString(1, device.getSerialNumber());
                stmt.setString(2, device.getImei());
                stmt.setString(3, device.getIccid());
                stmt.setString(4, device.getCapacity());
                stmt.setString(5, device.getDeviceName());
                stmt.setString(6, device.getLastImportDate());
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
        }
    }

    public Map<String, BulkDevice> findDevicesBySerials(List<String> serialNumbers) throws SQLException {
        Map<String, BulkDevice> results = new HashMap<>();
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            return results;
        }

        // This creates a query with the correct number of '?' placeholders
        String placeholders = String.join(",", Collections.nCopies(serialNumbers.size(), "?"));
        String sql = String.format("SELECT * FROM Bulk_Devices WHERE SerialNumber IN (%s)", placeholders);

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Bind all the serial numbers to the prepared statement
            for (int i = 0; i < serialNumbers.size(); i++) {
                stmt.setString(i + 1, serialNumbers.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                BulkDevice device = new BulkDevice(rs.getString("SerialNumber"), rs.getString("IMEI"), rs.getString("ICCID"), rs.getString("Capacity"), rs.getString("DeviceName"), rs.getString("LastImportDate"));
                results.put(device.getSerialNumber(), device);
            }
        }
        return results;
    }

    public int getDeviceCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM Bulk_Devices;";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public Optional<BulkDevice> findDeviceBySerial(String serialNumber) throws SQLException {
        String sql = "SELECT * FROM Bulk_Devices WHERE SerialNumber = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new BulkDevice(rs.getString("SerialNumber"), rs.getString("IMEI"), rs.getString("ICCID"), rs.getString("Capacity"), rs.getString("DeviceName"), rs.getString("LastImportDate")));
            }
        }
        return Optional.empty();
    }

    public List<BulkDevice> searchDevices(String query) throws SQLException {
        List<BulkDevice> results = new ArrayList<>();
        String sql = "SELECT * FROM Bulk_Devices WHERE " + "SerialNumber LIKE ? OR " + "IMEI LIKE ? OR " + "ICCID LIKE ? " + "LIMIT 100";
        String queryParam = "%" + query + "%";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, queryParam);
            stmt.setString(2, queryParam);
            stmt.setString(3, queryParam);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(new BulkDevice(rs.getString("SerialNumber"), rs.getString("IMEI"), rs.getString("ICCID"), rs.getString("Capacity"), rs.getString("DeviceName"), rs.getString("LastImportDate")));
            }
        }
        return results;
    }

    public void updateSimCard(String serialNumber, String newIccid) throws SQLException {
        String sql = "UPDATE Bulk_Devices SET ICCID = ? WHERE SerialNumber = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newIccid);
            stmt.setString(2, serialNumber);
            stmt.executeUpdate();
        }
    }

    public void saveAssignments(List<StagedDevice> devices) throws SQLException {
        String sql = "INSERT INTO Device_Assignments (SerialNumber, EmployeeEmail, EmployeeFirstName, " + "EmployeeLastName, SNReferenceNumber, AssignmentDate, DepotOrderNumber, Exported) " + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?, true)"; // Use CURRENT_DATE for PostgreSQL
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (StagedDevice device : devices) {
                stmt.setString(1, device.getSerialNumber());
                stmt.setString(2, device.getEmployeeEmail());
                stmt.setString(3, device.getFirstName());
                stmt.setString(4, device.getLastName());
                stmt.setString(5, device.getSnReferenceNumber());
                stmt.setString(6, device.getDepotOrderNumber()); // Note: Parameter index shifts from 6 to 7 in SQLite to just 6 here. The code provided is correct.
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
        }
    }
}