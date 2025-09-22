package assettracking.dao;

import assettracking.data.ReceiptEvent;
import assettracking.db.DatabaseConnection;

import java.sql.*;
import java.util.Optional;

public class ReceiptEventDAO {

    public int addReceiptEvent(ReceiptEvent event) {
        String sql = "INSERT INTO receipt_events (serial_number, package_id, imei, category, make, model_number, description) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, event.getSerialNumber());
            stmt.setInt(2, event.getPackageId());
            stmt.setString(3, event.getImei());
            stmt.setString(4, event.getCategory());
            stmt.setString(5, event.getMake());
            stmt.setString(6, event.getModelNumber());
            stmt.setString(7, event.getDescription());
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    event.setReceiptId(generatedId);
                    return generatedId;
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error adding receipt event: " + e.getMessage());
        }
        return -1;
    }

    public boolean updatePackageId(int receiptId, int newPackageId) {
        String sql = "UPDATE receipt_events SET package_id = ? WHERE receipt_id = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newPackageId);
            stmt.setInt(2, receiptId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Database error updating package ID: " + e.getMessage());
            return false;
        }
    }

    public int addReceiptEvent(Connection conn, ReceiptEvent event) throws SQLException {
        String sql = "INSERT INTO receipt_events (serial_number, package_id, imei, category, make, model_number, description) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, event.getSerialNumber());
            stmt.setInt(2, event.getPackageId());
            stmt.setString(3, event.getImei());
            stmt.setString(4, event.getCategory());
            stmt.setString(5, event.getMake());
            stmt.setString(6, event.getModelNumber());
            stmt.setString(7, event.getDescription());
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    event.setReceiptId(generatedId);
                    return generatedId;
                }
            }
        }
        return -1;
    }

    public Optional<Integer> findMostRecentReceiptId(String serialNumber) {
        String sql = "SELECT receipt_id FROM receipt_events WHERE serial_number = ? ORDER BY receipt_id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("receipt_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding most recent receipt for serial '" + serialNumber + "': " + e.getMessage());
        }
        return Optional.empty();
    }
}