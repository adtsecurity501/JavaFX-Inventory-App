package assettracking.dao;

import assettracking.controller.DeviceStatusTrackingController;
import assettracking.data.DeviceStatusView;
import assettracking.db.DatabaseConnection;
import assettracking.manager.DeviceStatusManager;
import assettracking.ui.DeviceStatusActions;
import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DeviceStatusDAO {

    private final DeviceStatusManager manager;
    private final ObservableList<DeviceStatusView> deviceStatusList;

    public DeviceStatusDAO(DeviceStatusManager manager, ObservableList<DeviceStatusView> deviceStatusList) {
        this.manager = manager;
        this.deviceStatusList = deviceStatusList;
    }

    public int fetchPageCount() {
        DeviceStatusActions.QueryAndParams queryAndParams = buildFilteredQuery(true);
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql())) {
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

    public void updateTableForPage(int pageIndex) {
        deviceStatusList.clear();
        DeviceStatusActions.QueryAndParams queryAndParams = buildFilteredQuery(false);
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql())) {
            int paramIndex = 1;
            for (Object param : queryAndParams.params()) {
                stmt.setObject(paramIndex++, param);
            }
            stmt.setInt(paramIndex++, manager.getRowsPerPage());
            stmt.setInt(paramIndex, pageIndex * manager.getRowsPerPage());

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                deviceStatusList.add(new DeviceStatusView(
                        rs.getInt("receipt_id"), rs.getString("serial_number"), rs.getString("category"),
                        rs.getString("make"), rs.getString("description"), rs.getString("status"),
                        rs.getString("sub_status"), rs.getTimestamp("last_update") != null ? rs.getTimestamp("last_update").toString().substring(0, 19) : "",
                        rs.getString("receive_date"),
                        rs.getString("change_log"), rs.getBoolean("is_flagged")
                ));
            }
        } catch (SQLException e) {
            Platform.runLater(() -> StageManager.showAlert(null, Alert.AlertType.ERROR, "Database Error", "Failed to load page data: " + e.getMessage()));
        }
    }

    public void updateDeviceStatus(ObservableList<DeviceStatusView> selectedDevices, String newStatus, String newSubStatus, String note) {
        if (selectedDevices == null || selectedDevices.isEmpty()) {
            StageManager.showAlert(null, Alert.AlertType.WARNING, "No Selection", "Please select one or more devices to update.");
            return;
        }

        String updateStatusSql = "UPDATE Device_Status SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP, change_log = ? WHERE receipt_id = ?";
        String deleteFlagSql = "DELETE FROM Flag_Devices WHERE serial_number = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInventoryConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement updateStmt = conn.prepareStatement(updateStatusSql);
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteFlagSql)) {
                for (DeviceStatusView device : selectedDevices) {
                    updateStmt.setString(1, newStatus);
                    updateStmt.setString(2, newSubStatus);
                    updateStmt.setString(3, note);
                    updateStmt.setInt(4, device.getReceiptId());
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
                try { conn.rollback(); } catch (SQLException ex) {
                    StageManager.showAlert(null, Alert.AlertType.ERROR, "Rollback Failed", "Failed to rollback database changes: " + ex.getMessage());
                }
            }
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) {
                    StageManager.showAlert(null, Alert.AlertType.ERROR, "Connection Error", "Failed to close database connection: " + e.getMessage());
                }
            }
        }
    }

    private DeviceStatusActions.QueryAndParams buildFilteredQuery(boolean forCount) {
        DeviceStatusTrackingController controller = manager.getController();

        // --- QUERY LOGIC HAS BEEN CORRECTED HERE ---
        String baseQuery =
                " FROM " +
                        "    Receipt_Events re " +
                        "INNER JOIN ( " +
                        "    SELECT serial_number, MAX(receipt_id) AS max_receipt_id " +
                        "    FROM Receipt_Events " +
                        "    GROUP BY serial_number " +
                        ") latest ON re.serial_number = latest.serial_number AND re.receipt_id = latest.max_receipt_id " +
                        // --- NEW: JOIN the Physical_Assets table to get the most current data ---
                        "LEFT JOIN Physical_Assets pa ON re.serial_number = pa.serial_number " +
                        "LEFT JOIN Packages p ON re.package_id = p.package_id " +
                        "LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id";

        String selectClause = forCount
                ? "SELECT COUNT(DISTINCT re.serial_number)"
                // --- UPDATED: Select category, make, and description from Physical_Assets (aliased as 'pa') ---
                : "SELECT p.receive_date, re.receipt_id, re.serial_number, pa.category, pa.make, pa.description, " +
                "ds.status, ds.sub_status, ds.last_update, ds.change_log, " +
                "EXISTS(SELECT 1 FROM Flag_Devices fd WHERE fd.serial_number = re.serial_number) AS is_flagged";
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
}