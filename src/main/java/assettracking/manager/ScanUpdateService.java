package assettracking.manager;

import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A service class to handle the business logic of updating device statuses.
 * It is decoupled from the UI and only deals with database operations.
 */
public class ScanUpdateService {

    public enum UpdateResult { SUCCESS, NOT_FOUND }

    public UpdateResult updateBySerial(String serial, String newStatus, String newSubStatus, String note) throws SQLException {
        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            String queryReceiptId = "SELECT receipt_id FROM Receipt_Events WHERE serial_number = ? ORDER BY receipt_id DESC LIMIT 1";
            int receiptId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(queryReceiptId)) {
                stmt.setString(1, serial);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) receiptId = rs.getInt("receipt_id");
            }

            if (receiptId != -1) {
                String updateQuery = "UPDATE Device_Status SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP, change_log = ? WHERE receipt_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setString(1, newStatus);
                    stmt.setString(2, newSubStatus);
                    stmt.setString(3, note.isEmpty() ? null : note);
                    stmt.setInt(4, receiptId);
                    stmt.executeUpdate();
                    return UpdateResult.SUCCESS;
                }
            } else {
                return UpdateResult.NOT_FOUND;
            }
        }
    }

    public List<Integer> findDeviceReceiptsByLocation(String location) throws SQLException {
        List<Integer> receiptIds = new ArrayList<>();
        String findSql = """
            SELECT ds.receipt_id FROM Device_Status ds
            JOIN (
                SELECT serial_number, MAX(receipt_id) as max_receipt_id
                FROM Receipt_Events
                GROUP BY serial_number
            ) latest_re ON ds.receipt_id = latest_re.max_receipt_id
            WHERE ds.change_log LIKE ?
        """;
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(findSql)) {
            stmt.setString(1, "Box ID: " + location + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                receiptIds.add(rs.getInt("receipt_id"));
            }
        }
        return receiptIds;
    }

    public int updateByReceiptIds(List<Integer> receiptIds, String newStatus, String newSubStatus, String note) throws SQLException {
        if (receiptIds == null || receiptIds.isEmpty()) return 0;

        String placeholders = String.join(",", Collections.nCopies(receiptIds.size(), "?"));
        String updateSql = String.format(
                "UPDATE Device_Status SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP, change_log = ? WHERE receipt_id IN (%s)",
                placeholders
        );

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setString(1, newStatus);
            stmt.setString(2, newSubStatus);
            stmt.setString(3, note.isEmpty() ? null : note);
            int i = 4;
            for (Integer id : receiptIds) {
                stmt.setInt(i++, id);
            }
            return stmt.executeUpdate();
        }
    }
}