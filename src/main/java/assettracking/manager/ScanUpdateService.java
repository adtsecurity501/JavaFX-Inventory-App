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

    public UpdateResult updateBySerial(String serial, String newStatus, String newSubStatus, String note, String boxId) throws SQLException {
        try (Connection conn = DatabaseConnection.getInventoryConnection()) {

            // This is the missing part: It declares and finds the receiptId
            String queryReceiptId = "SELECT receipt_id FROM Receipt_Events WHERE serial_number = ? ORDER BY receipt_id DESC LIMIT 1";
            int receiptId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(queryReceiptId)) {
                stmt.setString(1, serial);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    receiptId = rs.getInt("receipt_id");
                }
            }

            // Now, the 'if' statement will work correctly
            if (receiptId != -1) {
                String updateQuery = "UPDATE Device_Status SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP, change_log = ?, box_id = ? WHERE receipt_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setString(1, newStatus);
                    stmt.setString(2, newSubStatus);
                    stmt.setString(3, note.isEmpty() ? null : note);
                    stmt.setString(4, boxId.isEmpty() ? null : boxId);
                    stmt.setInt(5, receiptId);
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
                    WHERE ds.box_id = ?
                """;
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(findSql)) {
            stmt.setString(1, location);
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
                "UPDATE Device_Status SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP, change_log = ?, box_id = NULL WHERE receipt_id IN (%s)",
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

    public enum UpdateResult {SUCCESS, NOT_FOUND}
}