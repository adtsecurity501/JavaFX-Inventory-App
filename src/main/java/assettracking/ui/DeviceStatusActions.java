package assettracking.ui;

import assettracking.controller.DeviceHistoryController;
import assettracking.controller.DeviceStatusTrackingController;
import assettracking.controller.ScanUpdateController;
import assettracking.data.DeviceStatusView;
import assettracking.db.DatabaseConnection;
import assettracking.manager.StageManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DeviceStatusActions {

    private final DeviceStatusTrackingController controller;

    public DeviceStatusActions(DeviceStatusTrackingController controller) {
        this.controller = controller;
    }

    /**
     * Opens the history window for the currently selected device in the table.
     * Shows a custom alert if no device is selected.
     */
    public void openDeviceHistoryWindow() {
        DeviceStatusView selectedDevice = controller.statusTable.getSelectionModel().getSelectedItem();
        if (selectedDevice != null) {
            openDeviceHistoryWindow(selectedDevice.getSerialNumber());
        } else {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "No Selection", "Please select a device to view its history.");
        }
    }

    /**
     * Creates and displays a custom window showing the historical events for a given serial number.
     *
     * @param serialNumber The serial number to look up.
     */
    public void openDeviceHistoryWindow(String serialNumber) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/DeviceHistory.fxml"));
            Parent root = loader.load();
            DeviceHistoryController historyController = loader.getController();
            historyController.initData(serialNumber);

            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Device History for " + serialNumber, root);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Could not open the device history window.");
        }
    }

    /**
     * Creates and displays the custom window for scan-based status updates.
     */
    public void openScanUpdateWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ScanUpdate.fxml"));
            Parent root = loader.load();
            ScanUpdateController scanController = loader.getController();
            scanController.setParentController(controller);

            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Scan-Based Status Update", root);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Could not open the Scan Update window.");
        }
    }

    /**
     * Initiates the file import process for flagged devices.
     */
    public void importFlags() {
        FlaggedDeviceImporter importer = new FlaggedDeviceImporter();
        importer.importFromFile((Stage) getOwnerWindow(), controller::refreshData);
    }

    /**
     * Exports the current device inventory to a CSV file.
     */
    public void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Asset Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(getOwnerWindow());

        if (file != null) {
            String header = "Tracking Number,First Name,Last Name,City,State,Zip,Receive Date,Category,Description,IMEI,Serial Number,Status Change Date,Status,Sub Status";
            String query = "SELECT p.tracking_number, p.first_name, p.last_name, p.city, p.state, p.zip_code, p.receive_date, " +
                    "re.category, re.description, re.imei, re.serial_number, ds.last_update AS status_change_date, " +
                    "ds.status, ds.sub_status " +
                    "FROM Packages p JOIN Receipt_Events re ON p.package_id = re.package_id LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id " +
                    "ORDER BY p.tracking_number, re.category";

            try (Connection conn = DatabaseConnection.getInventoryConnection();
                 PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery();
                 PrintWriter writer = new PrintWriter(file)) {

                writer.println(header);
                while (rs.next()) {
                    List<String> row = new ArrayList<>();
                    row.add(escapeCSV(rs.getString("tracking_number")));
                    row.add(escapeCSV(rs.getString("first_name")));
                    row.add(escapeCSV(rs.getString("last_name")));
                    row.add(escapeCSV(rs.getString("city")));
                    row.add(escapeCSV(rs.getString("state")));
                    row.add(escapeCSV(rs.getString("zip_code")));
                    row.add(escapeCSV(rs.getString("receive_date")));
                    row.add(escapeCSV(rs.getString("category")));
                    row.add(escapeCSV(rs.getString("description")));
                    row.add(escapeCSV(rs.getString("imei")));
                    row.add(escapeCSV(rs.getString("serial_number")));
                    row.add(escapeCSV(rs.getString("status_change_date")));
                    row.add(escapeCSV(rs.getString("status")));
                    row.add(escapeCSV(rs.getString("sub_status")));
                    writer.println(String.join(",", row));
                }
                StageManager.showAlert(getOwnerWindow(), Alert.AlertType.INFORMATION, "Success", "Export successful: " + file.getAbsolutePath());
            } catch (SQLException e) {
                e.printStackTrace();
                StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Database Error", "Failed to query data for export: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Export Error", "Failed to write to file: " + e.getMessage());
            }
        }
    }

    /**
     * Helper method to escape characters in a string for CSV format.
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Helper method to get the parent window for new stages and dialogs.
     */
    private Window getOwnerWindow() {
        return controller.statusTable.getScene().getWindow();
    }

    // This record is used by the DAO but defined here for historical reasons.
    public record QueryAndParams(String sql, List<Object> params) {
    }
}