package assettracking.ui;

import assettracking.controller.DeviceHistoryController;
import assettracking.controller.DeviceStatusTrackingController;
import assettracking.controller.ScanUpdateController;
import assettracking.data.DeviceStatusView; // Corrected import
import assettracking.db.DatabaseConnection;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
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

    public record QueryAndParams(String sql, List<Object> params) {}

    public DeviceStatusActions(DeviceStatusTrackingController controller) {
        this.controller = controller;
    }

    public void openDeviceHistoryWindow() {
        DeviceStatusView selectedDevice = controller.statusTable.getSelectionModel().getSelectedItem();
        if (selectedDevice != null) {
            openDeviceHistoryWindow(selectedDevice.getSerialNumber());
        } else {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a device to view its history.");
        }
    }

    public void openDeviceHistoryWindow(String serialNumber) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/DeviceHistory.fxml"));
            Parent root = loader.load();
            DeviceHistoryController historyController = loader.getController();
            historyController.initData(serialNumber);
            Stage stage = new Stage();
            stage.setTitle("Device History for " + serialNumber);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(getOwnerWindow());
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open the device history window.");
        }
    }

    public void openScanUpdateWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ScanUpdate.fxml"));
            Parent root = loader.load();
            ScanUpdateController scanController = loader.getController();
            scanController.setParentController(controller);
            Stage stage = new Stage();
            stage.setTitle("Scan-Based Status Update");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(getOwnerWindow());
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open the Scan Update window.");
        }
    }

    public void importFlags() {
        FlaggedDeviceImporter importer = new FlaggedDeviceImporter();
        importer.importFromFile((Stage) getOwnerWindow(), controller::refreshData);
    }

    public void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Comprehensive CSV Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(getOwnerWindow());

        if (file != null) {
            String header = "Tracking Number,First Name,Last Name,City,State,Zip,Receive Date,Category,Condition,Quantity,Description,IMEI,Serial Number,Status Change Date,Status,Sub Status";
            String query = "SELECT p.tracking_number, p.first_name, p.last_name, p.city, p.state, p.zip_code, p.receive_date, " +
                    "re.category, NULL AS condition, NULL AS quantity, re.description, re.imei, re.serial_number, ds.last_update AS status_change_date, " +
                    "ds.status, ds.sub_status " +
                    "FROM Packages p JOIN Receipt_Events re ON p.package_id = re.package_id LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id " +
                    "UNION ALL " +
                    "SELECT p.tracking_number, p.first_name, p.last_name, p.city, p.state, p.zip_code, p.receive_date, " +
                    "pe.category, pe.condition, pe.quantity, pe.description, NULL AS imei, NULL AS serial_number, NULL AS status_change_date, " +
                    "NULL AS status, NULL AS sub_status " +
                    "FROM Packages p JOIN Peripherals pe ON p.package_id = pe.package_id " +
                    "ORDER BY tracking_number, category";

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
                    row.add(escapeCSV(rs.getString("condition")));
                    row.add(escapeCSV(rs.getString("quantity")));
                    row.add(escapeCSV(rs.getString("description")));
                    row.add(escapeCSV(rs.getString("imei")));
                    row.add(escapeCSV(rs.getString("serial_number")));
                    row.add(escapeCSV(rs.getString("status_change_date")));
                    row.add(escapeCSV(rs.getString("status")));
                    row.add(escapeCSV(rs.getString("sub_status")));
                    writer.println(String.join(",", row));
                }
                showAlert(Alert.AlertType.INFORMATION, "Success", "Export successful: " + file.getAbsolutePath());
            } catch (SQLException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to query data for export: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Export Error", "Failed to write to file: " + e.getMessage());
            }
        }
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public static void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private Window getOwnerWindow() {
        return controller.statusTable.getScene().getWindow();
    }
}