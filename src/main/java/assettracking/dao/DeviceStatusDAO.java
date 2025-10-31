package assettracking.dao;

import assettracking.controller.DeviceStatusTrackingController;
import assettracking.data.DeviceStatusView;
import assettracking.db.DatabaseConnection;
import assettracking.manager.DeviceStatusManager;
import assettracking.manager.StageManager;
import assettracking.ui.DeviceStatusActions;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DeviceStatusDAO {

    private final DeviceStatusManager manager;
    private final ObservableList<DeviceStatusView> deviceStatusList;

    public DeviceStatusDAO(DeviceStatusManager manager, ObservableList<DeviceStatusView> deviceStatusList) {
        this.manager = manager;
        this.deviceStatusList = deviceStatusList;
    }

    public int fetchPageCount() {
        DeviceStatusActions.QueryAndParams queryAndParams = buildFilteredQuery(true);
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql())) {
            for (int i = 0; i < queryAndParams.params().size(); i++) {
                stmt.setObject(i + 1, queryAndParams.params().get(i));
            }
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            Platform.runLater(() -> StageManager.showAlert(null, Alert.AlertType.ERROR, "Database Error", "Failed to count records for pagination: " + e.getMessage()));
        }
        return 0;
    }

    public BulkMoveResult bulkMoveBySerialList(String sourceBoxId, String destinationBoxId, Set<String> serialsToMove) throws SQLException {
        List<String> foundSerials = new ArrayList<>();
        // <<< THIS IS THE CORRECTED LINE
        List<String> notFoundSerials = new ArrayList<>(serialsToMove);

        String selectPlaceholders = String.join(",", Collections.nCopies(serialsToMove.size(), "?"));

        // Step 1: Find which of the provided serials actually exist in the source box. This is a critical validation step.
        String findSql = String.format("""
        SELECT re.serial_number FROM Device_Status ds
        JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id
        WHERE ds.box_id = ? AND re.serial_number IN (%s)
    """, selectPlaceholders);

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement findStmt = conn.prepareStatement(findSql)) {
            findStmt.setString(1, sourceBoxId);
            int i = 2;
            for (String serial : serialsToMove) {
                findStmt.setString(i++, serial);
            }
            ResultSet rs = findStmt.executeQuery();
            while (rs.next()) {
                String foundSerial = rs.getString("serial_number");
                foundSerials.add(foundSerial);
                notFoundSerials.remove(foundSerial);
            }
        }

        if (foundSerials.isEmpty()) {
            return new BulkMoveResult(Collections.emptyList(), new ArrayList<>(serialsToMove));
        }

        // Step 2: Update only the serials that were verified to be in the source box.
        String updatePlaceholders = String.join(",", Collections.nCopies(foundSerials.size(), "?"));
        String updateSql = String.format("""
        UPDATE Device_Status SET box_id = ?
        WHERE receipt_id IN (
            SELECT MAX(re.receipt_id)
            FROM Receipt_Events re
            WHERE re.serial_number IN (%s)
            GROUP BY re.serial_number
        )
    """, updatePlaceholders);

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            conn.setAutoCommit(false); // Start transaction
            updateStmt.setString(1, destinationBoxId.trim().toUpperCase());
            int i = 2;
            for (String serial : foundSerials) {
                updateStmt.setString(i++, serial);
            }
            updateStmt.executeUpdate();
            conn.commit(); // Commit transaction
        }

        return new BulkMoveResult(foundSerials, notFoundSerials);
    }

    public BulkUpdateResult bulkUpdateStatusBySerial(Set<String> serials, String newStatus, String newSubStatus, String note) throws SQLException {
        List<String> updatedSerials = new ArrayList<>();
        List<String> notFoundSerials = new ArrayList<>(serials); // Start with all serials, we'll remove the successful ones

        // This query updates the status of the MOST RECENT receipt event for a given serial number.
        String sql = """
                    UPDATE Device_Status
                    SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP, change_log = ?
                    WHERE receipt_id IN (
                        SELECT MAX(re.receipt_id)
                        FROM Receipt_Events re
                        WHERE re.serial_number = ?
                    )
                """;

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInventoryConnection();
            conn.setAutoCommit(false); // Start transaction

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (String serial : serials) {
                    stmt.setString(1, newStatus);
                    stmt.setString(2, newSubStatus);
                    stmt.setString(3, note);
                    stmt.setString(4, serial);
                    stmt.addBatch();
                }

                int[] results = stmt.executeBatch();

                // Now, validate the results
                List<String> serialList = new ArrayList<>(serials);
                for (int i = 0; i < results.length; i++) {
                    if (results[i] > 0) { // If rows were affected, it was a success
                        String successfulSerial = serialList.get(i);
                        updatedSerials.add(successfulSerial);
                        notFoundSerials.remove(successfulSerial);
                    }
                }
            }
            conn.commit(); // Commit all successful updates at once
        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // Rollback on any error
            throw e; // Re-throw the exception to be handled by the controller
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        return new BulkUpdateResult(updatedSerials, notFoundSerials);
    }

    public void updateTableForPage(int pageIndex) {
        deviceStatusList.clear();
        DeviceStatusActions.QueryAndParams queryAndParams = buildFilteredQuery(false);
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql())) {
            int paramIndex = 1;
            for (Object param : queryAndParams.params()) {
                stmt.setObject(paramIndex++, param);
            }
            stmt.setInt(paramIndex++, manager.getRowsPerPage());
            stmt.setInt(paramIndex, pageIndex * manager.getRowsPerPage());

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                deviceStatusList.add(new DeviceStatusView(rs.getInt("receipt_id"), rs.getString("serial_number"), rs.getString("category"), rs.getString("make"), rs.getString("description"), rs.getString("status"), rs.getString("sub_status"), rs.getTimestamp("last_update") != null ? rs.getTimestamp("last_update").toString().substring(0, 19) : "", rs.getString("receive_date"), rs.getString("change_log"), rs.getBoolean("is_flagged")));
            }
        } catch (SQLException e) {
            Platform.runLater(() -> StageManager.showAlert(null, Alert.AlertType.ERROR, "Database Error", "Failed to load page data: " + e.getMessage()));
        }
    }

    public void updateDeviceStatus(ObservableList<DeviceStatusView> selectedDevices, String newStatus, String newSubStatus, String note, String boxId) {
        if (selectedDevices == null || selectedDevices.isEmpty()) {
            StageManager.showAlert(null, Alert.AlertType.WARNING, "No Selection", "Please select one or more devices to update.");
            return;
        }

        String updateStatusSql = "UPDATE Device_Status SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP, change_log = ?, box_id = ? WHERE receipt_id = ?";
        String deleteFlagSql = "DELETE FROM Flag_Devices WHERE serial_number = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInventoryConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement updateStmt = conn.prepareStatement(updateStatusSql); PreparedStatement deleteStmt = conn.prepareStatement(deleteFlagSql)) {

                for (DeviceStatusView device : selectedDevices) {
                    updateStmt.setString(1, newStatus);
                    updateStmt.setString(2, newSubStatus);
                    updateStmt.setString(3, note.isEmpty() ? null : note);

                    // If this is a deletion, force box_id to null. Otherwise, use the provided boxId.
                    if ("Deleted (Mistake)".equals(newSubStatus)) {
                        updateStmt.setNull(4, java.sql.Types.VARCHAR);
                    } else {
                        updateStmt.setString(4, (boxId != null && !boxId.isEmpty()) ? boxId : null);
                    }

                    updateStmt.setInt(5, device.getReceiptId());
                    updateStmt.addBatch();

                    if ("Flag!".equals(device.getStatus())) {
                        deleteStmt.setString(1, device.getSerialNumber());
                        deleteStmt.addBatch();
                    }
                }
                updateStmt.executeBatch();
                deleteStmt.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            StageManager.showAlert(null, Alert.AlertType.ERROR, "Update Failed", "Failed to update device statuses in the database: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    StageManager.showAlert(null, Alert.AlertType.ERROR, "Rollback Failed", "Failed to rollback database changes: " + ex.getMessage());
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    StageManager.showAlert(null, Alert.AlertType.ERROR, "Connection Error", "Failed to close database connection: " + e.getMessage());
                }
            }
        }
    }

    public int permanentlyDeleteDevicesBySerial(Set<String> serials) throws SQLException {
        if (serials == null || serials.isEmpty()) {
            return 0;
        }

        // Create a string of placeholders like "?,?,?"
        String placeholders = String.join(",", Collections.nCopies(serials.size(), "?"));

        String[] deleteQueries = {String.format("DELETE FROM Device_Status WHERE receipt_id IN (SELECT receipt_id FROM Receipt_Events WHERE serial_number IN (%s))", placeholders), String.format("DELETE FROM Disposition_Info WHERE receipt_id IN (SELECT receipt_id FROM Receipt_Events WHERE serial_number IN (%s))", placeholders), String.format("DELETE FROM Flag_Devices WHERE serial_number IN (%s)", placeholders), String.format("DELETE FROM Receipt_Events WHERE serial_number IN (%s)", placeholders), String.format("DELETE FROM Physical_Assets WHERE serial_number IN (%s)", placeholders), String.format("DELETE FROM Device_Autofill_Data WHERE serial_number IN (%s)", placeholders)};

        int totalRowsAffected = 0;
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInventoryConnection();
            conn.setAutoCommit(false); // Start transaction

            for (String sql : deleteQueries) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    int i = 1;
                    for (String serial : serials) {
                        stmt.setString(i++, serial);
                    }
                    totalRowsAffected += stmt.executeUpdate();
                }
            }

            conn.commit(); // Commit all changes
            return totalRowsAffected;

        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // Rollback on any error
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    private DeviceStatusActions.QueryAndParams buildFilteredQuery(boolean forCount) {
        DeviceStatusTrackingController controller = manager.getController();

        // --- QUERY LOGIC HAS BEEN CORRECTED HERE ---
        String baseQuery = " FROM " + "    Receipt_Events re " + "INNER JOIN ( " + "    SELECT serial_number, MAX(receipt_id) AS max_receipt_id " + "    FROM Receipt_Events " + "    GROUP BY serial_number " + ") latest ON re.serial_number = latest.serial_number AND re.receipt_id = latest.max_receipt_id " +
                // --- NEW: JOIN the Physical_Assets table to get the most current data ---
                "LEFT JOIN Physical_Assets pa ON re.serial_number = pa.serial_number " + "LEFT JOIN Packages p ON re.package_id = p.package_id " + "LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id";

        String selectClause = forCount ? "SELECT COUNT(DISTINCT re.serial_number)"
                // --- UPDATED: Select category, make, and description from Physical_Assets (aliased as 'pa') ---
                : "SELECT p.receive_date, re.receipt_id, re.serial_number, pa.category, pa.make, pa.description, " + "ds.status, ds.sub_status, ds.last_update, ds.change_log, " + "EXISTS(SELECT 1 FROM Flag_Devices fd WHERE fd.serial_number = re.serial_number) AS is_flagged";
        // --- END OF CORRECTIONS ---

        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");

        String serialNum = controller.serialSearchField.getText().trim();
        if (!serialNum.isEmpty()) {
            whereClause.append(" AND re.serial_number LIKE ?");
            params.add("%" + serialNum + "%");
        }
        String status = controller.statusFilterCombo.getValue();
        if (status != null && !"All Statuses".equals(status)) {
            whereClause.append(" AND ds.status = ?");
            params.add(status);
        }

        // --- NEW LOGIC TO ADD SUB-STATUS TO THE QUERY ---
        String subStatus = controller.subStatusFilterCombo.getValue();
        if (subStatus != null && !"All Sub-Statuses".equals(subStatus)) {
            whereClause.append(" AND ds.sub_status = ?");
            params.add(subStatus);
        }
        // --- END OF NEW LOGIC ---

        String category = controller.categoryFilterCombo.getValue();
        if (category != null && !"All Categories".equals(category)) {
            whereClause.append(" AND pa.category = ?");
            params.add(category);
        }
        LocalDate fromDate = controller.fromDateFilter.getValue();
        if (fromDate != null) {
            whereClause.append(" AND ds.last_update >= ?");
            params.add(java.sql.Date.valueOf(fromDate));
        }
        LocalDate toDate = controller.toDateFilter.getValue();
        if (toDate != null) {
            whereClause.append(" AND ds.last_update < ?");
            params.add(java.sql.Date.valueOf(toDate.plusDays(1)));
        }

        String fullQuery = selectClause + baseQuery + whereClause;

        if (!forCount) {
            String groupBy = controller.groupByCombo.getValue();
            if ("Status".equals(groupBy)) {
                fullQuery += " ORDER BY ds.status, ds.last_update DESC";
            } else if ("Category".equals(groupBy)) {
                // --- UPDATED: Group by the current category in Physical_Assets ---
                fullQuery += " ORDER BY pa.category, ds.last_update DESC";
            } else {
                fullQuery += " ORDER BY ds.last_update DESC";
            }
            fullQuery += " LIMIT ? OFFSET ?";
        }
        return new DeviceStatusActions.QueryAndParams(fullQuery, params);
    }

    public record BulkMoveResult(List<String> movedSerials, List<String> notFoundOrFailedSerials) {
    }

    public record BulkUpdateResult(List<String> updated, List<String> notFound) {
    }
}