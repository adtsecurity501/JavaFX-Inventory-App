package assettracking.dao.bulk;

import assettracking.data.bulk.BulkDevice;
import assettracking.data.bulk.StagedDevice;
import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class iPadProvisioningDAO {
    public void upsertBulkDevices(List<BulkDevice> devices) throws SQLException {
        String upsertSql = "INSERT INTO bulk_devices (serial_number, imei, iccid, capacity, device_name, last_import_date) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (serial_number) DO UPDATE SET " +
                "imei = EXCLUDED.imei, " +
                "iccid = EXCLUDED.iccid, " +
                "capacity = EXCLUDED.capacity, " +
                "device_name = EXCLUDED.device_name, " +
                "last_import_date = EXCLUDED.last_import_date";

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
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

    public int getDeviceCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM bulk_devices;";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public Optional<BulkDevice> findDeviceBySerial(String serialNumber) throws SQLException {
        String sql = "SELECT * FROM bulk_devices WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new BulkDevice(
                        rs.getString("serial_number"),
                        rs.getString("imei"),
                        rs.getString("iccid"),
                        rs.getString("capacity"),
                        rs.getString("device_name"),
                        rs.getString("last_import_date")
                ));
            }
        }
        return Optional.empty();
    }

    public List<BulkDevice> searchDevices(String query) throws SQLException {
        List<BulkDevice> results = new ArrayList<>();
        String sql = "SELECT * FROM bulk_devices WHERE " +
                "serial_number ILIKE ? OR " +
                "imei ILIKE ? OR " +
                "iccid ILIKE ? " +
                "LIMIT 100";
        String queryParam = "%" + query + "%";

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, queryParam);
            stmt.setString(2, queryParam);
            stmt.setString(3, queryParam);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(new BulkDevice(
                        rs.getString("serial_number"),
                        rs.getString("imei"),
                        rs.getString("iccid"),
                        rs.getString("capacity"),
                        rs.getString("device_name"),
                        rs.getString("last_import_date")
                ));
            }
        }
        return results;
    }

    public void updateSimCard(String serialNumber, String newIccid) throws SQLException {
        String sql = "UPDATE bulk_devices SET iccid = ? WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newIccid);
            stmt.setString(2, serialNumber);
            stmt.executeUpdate();
        }
    }

    public void saveAssignments(List<StagedDevice> devices) throws SQLException {
        String sql = "INSERT INTO device_assignments (serial_number, employee_email, employee_first_name, " +
                "employee_last_name, sn_reference_number, assignment_date, depot_order_number, exported) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?, true)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (StagedDevice device : devices) {
                stmt.setString(1, device.getSerialNumber());
                stmt.setString(2, device.getEmployeeEmail());
                stmt.setString(3, device.getFirstName());
                stmt.setString(4, device.getLastName());
                stmt.setString(5, device.getSnReferenceNumber());
                stmt.setString(6, device.getDepotOrderNumber());
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
        }
    }
}