package assettracking.controller;

import assettracking.dao.AssetDAO;
import assettracking.dao.SkuDAO;
import assettracking.data.AssetInfo;
import assettracking.db.DatabaseConnection;
import assettracking.label.service.ZplPrinterService;
import assettracking.manager.StageManager;
import assettracking.manager.StatusManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ScanUpdateController {

    // --- (All FXML Fields are the same) ---
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> subStatusCombo;
    @FXML private TextField changeLogField;
    @FXML private TextField scanSerialField;
    @FXML private Label feedbackLabel;
    @FXML private Label disposalLocationLabel;
    @FXML private TextField disposalLocationField;
    @FXML private TableView<ScanResult> successTable;
    @FXML private TableColumn<ScanResult, String> successSerialCol;
    @FXML private TableColumn<ScanResult, String> successStatusCol;
    @FXML private TableColumn<ScanResult, String> successTimestampCol;
    @FXML private TableView<ScanResult> failedTable;
    @FXML private TableColumn<ScanResult, String> failedSerialCol;
    @FXML private TableColumn<ScanResult, String> failedReasonCol;
    @FXML private TableColumn<ScanResult, String> failedTimestampCol;
    @FXML private TextField scanLocationField;
    @FXML private HBox boxIdHBox;
    @FXML private Button clearBoxIdButton;

    private DeviceStatusTrackingController parentController;
    private final ObservableList<ScanResult> successList = FXCollections.observableArrayList();
    private final ObservableList<ScanResult> failedList = FXCollections.observableArrayList();
    private final SkuDAO skuDAO = new SkuDAO();
    private final AssetDAO assetDAO = new AssetDAO();
    private final ZplPrinterService printerService = new ZplPrinterService();

    public void setParentController(DeviceStatusTrackingController parentController) {
        this.parentController = parentController;
    }

    @FXML
    public void initialize() {
        setupTableColumns();

        statusCombo.getItems().addAll(StatusManager.getStatuses());
        statusCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            subStatusCombo.getItems().clear();
            if (newVal != null) {
                subStatusCombo.getItems().addAll(StatusManager.getSubStatuses(newVal));
                if (!subStatusCombo.getItems().isEmpty()) {
                    subStatusCombo.getSelectionModel().selectFirst();
                }
            }
            updateUiForStatusChange();
        });

        subStatusCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateUiForStatusChange());
        disposalLocationField.textProperty().addListener((obs, oldVal, newVal) -> updateUiForStatusChange());

        statusCombo.getSelectionModel().selectFirst();
    }

    private void setupTableColumns() {
        successSerialCol.setCellValueFactory(new PropertyValueFactory<>("serial"));
        successStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        successTimestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        successTable.setItems(successList);

        failedSerialCol.setCellValueFactory(new PropertyValueFactory<>("serial"));
        failedReasonCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        failedTimestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        failedTable.setItems(failedList);
    }

    /**
     * MODIFIED: This method no longer aggressively manages focus. It only controls
     * visibility and whether the serial field is enabled.
     */
    private void updateUiForStatusChange() {
        String status = statusCombo.getValue();
        String subStatus = subStatusCombo.getValue();

        boolean isDisposal = "Disposed".equals(status);
        boolean needsBoxId = isDisposal && !"Ready for Wipe".equals(subStatus);

        disposalLocationLabel.setVisible(isDisposal);
        boxIdHBox.setVisible(isDisposal);
        disposalLocationLabel.setManaged(isDisposal);
        boxIdHBox.setManaged(isDisposal);

        scanSerialField.setDisable(needsBoxId && disposalLocationField.getText().trim().isEmpty());
    }

    /**
     * NEW METHOD: This is called when the user presses Enter in the Box ID field.
     * It simply moves the focus to the next logical field.
     */
    @FXML
    private void onBoxIdScanned() {
        scanSerialField.requestFocus();
    }

    // --- (The rest of the file is unchanged) ---

    @FXML
    private void handleClearBoxId() {
        disposalLocationField.clear();
        changeLogField.clear();
        disposalLocationField.requestFocus();
    }

    @FXML
    private void onSerialScanned() {
        String serial = scanSerialField.getText().trim();
        if (serial.isEmpty()) return;

        String newStatus = statusCombo.getValue();
        String newSubStatus = subStatusCombo.getValue();
        String boxId = disposalLocationField.getText().trim();

        if ("Disposed".equals(newStatus) && !"Ready for Wipe".equals(newSubStatus) && boxId.isEmpty()) {
            showAlert("Box ID Required", "A Box ID must be entered for this disposed status.");
            disposalLocationField.requestFocus();
            return;
        }

        String baseNote = changeLogField.getText().trim();
        String finalNote = "Disposed".equals(newStatus) && !boxId.isEmpty()
                ? ("Box ID: " + boxId + ". " + baseNote).trim()
                : baseNote;

        feedbackLabel.setText("Processing " + serial + "...");
        feedbackLabel.setTextFill(Color.BLUE);

        Task<String> updateTask = createTaskForSerialUpdate(serial, newStatus, newSubStatus, finalNote);

        updateTask.setOnSucceeded(event -> {
            String result = updateTask.getValue();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            if (result.startsWith("Success")) {
                feedbackLabel.setText("✔ " + result);
                feedbackLabel.setTextFill(Color.GREEN);
                successList.add(0, new ScanResult(serial, newStatus + " / " + newSubStatus, timestamp));
                if (parentController != null) parentController.refreshData();

                if ("Processed".equals(newStatus) && "Ready for Deployment".equals(newSubStatus)) {
                    Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Device is Ready for Deployment. Print SKU and Serial labels?", ButtonType.YES, ButtonType.NO);
                    confirmation.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            printDeploymentLabels(serial);
                        }
                    });
                }
            } else {
                feedbackLabel.setText("❌ " + result);
                feedbackLabel.setTextFill(Color.RED);
                failedList.add(0, new ScanResult(serial, result, timestamp));
            }
            scanSerialField.clear();
            scanSerialField.requestFocus();
        });

        updateTask.setOnFailed(event -> {
            Throwable ex = updateTask.getException();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            feedbackLabel.setText("❌ Error: " + ex.getMessage());
            feedbackLabel.setTextFill(Color.RED);
            failedList.add(0, new ScanResult(serial, "DB Error", timestamp));
            StageManager.showAlert(scanSerialField.getScene().getWindow(), Alert.AlertType.ERROR, "Database Error", "An unexpected database error occurred: " + ex.getMessage());
        });

        new Thread(updateTask).start();
    }

    private Task<String> createTaskForSerialUpdate(String serial, String newStatus, String newSubStatus, String finalNote) {
        return new Task<>() {
            @Override
            protected String call() throws Exception {
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
                            stmt.setString(3, finalNote.isEmpty() ? null : finalNote);
                            stmt.setInt(4, receiptId);
                            int rows = stmt.executeUpdate();
                            return rows > 0 ? "Success: " + serial : "Warning: " + serial + " found but not updated.";
                        }
                    } else {
                        return "Not Found: " + serial;
                    }
                }
            }
        };
    }

    @FXML
    private void onUpdateByLocation() {
        String location = scanLocationField.getText().trim();
        if (location.isEmpty()) return;

        String newStatus = statusCombo.getValue();
        String newSubStatus = subStatusCombo.getValue();
        String finalNote = changeLogField.getText().trim();

        Task<List<Integer>> findDevicesTask = new Task<>() {
            @Override
            protected List<Integer> call() throws Exception {
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
        };

        findDevicesTask.setOnSucceeded(e -> {
            List<Integer> receiptIds = findDevicesTask.getValue();
            if (receiptIds.isEmpty()) {
                showAlert("No Devices Found", "No active devices were found with the Box ID: '" + location + "'");
                return;
            }

            String header = "Update all devices in Box ID '" + location + "'?";
            String content = String.format(
                    "You are about to update %d device(s) to the following status:%n%nStatus: %s%nSub-Status: %s%n%nThis action cannot be undone. Are you sure you want to proceed?",
                    receiptIds.size(), newStatus, newSubStatus
            );

            boolean confirmed = StageManager.showConfirmationDialog(
                    scanLocationField.getScene().getWindow(),
                    "Confirm Bulk Update",
                    header,
                    content
            );

            if (confirmed) {
                performBulkUpdate(receiptIds, newStatus, newSubStatus, finalNote, location);
            }
        });

        findDevicesTask.setOnFailed(e -> StageManager.showAlert(scanLocationField.getScene().getWindow(), Alert.AlertType.ERROR, "Database Error", "Failed to query devices by location: " + e.getSource().getException().getMessage()));

        new Thread(findDevicesTask).start();
    }

    private void performBulkUpdate(List<Integer> receiptIds, String newStatus, String newSubStatus, String finalNote, String location) {
        Task<Integer> bulkUpdateTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                String placeholders = String.join(",", Collections.nCopies(receiptIds.size(), "?"));
                String updateSql = String.format(
                        "UPDATE Device_Status SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP, change_log = ? WHERE receipt_id IN (%s)",
                        placeholders
                );

                try (Connection conn = DatabaseConnection.getInventoryConnection();
                     PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, newStatus);
                    stmt.setString(2, newSubStatus);
                    stmt.setString(3, finalNote.isEmpty() ? null : finalNote);
                    int i = 4;
                    for (Integer id : receiptIds) {
                        stmt.setInt(i++, id);
                    }
                    return stmt.executeUpdate();
                }
            }
        };

        bulkUpdateTask.setOnSucceeded(e -> {
            int updatedCount = bulkUpdateTask.getValue();
            feedbackLabel.setText(String.format("✔ Successfully updated %d devices in location '%s'.", updatedCount, location));
            feedbackLabel.setTextFill(Color.GREEN);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            successList.add(0, new ScanResult("Box ID: " + location, String.format("Updated %d devices", updatedCount), timestamp));
            if (parentController != null) parentController.refreshData();
            scanLocationField.clear();
            scanLocationField.requestFocus();
        });

        bulkUpdateTask.setOnFailed(e -> {
            feedbackLabel.setText("❌ Bulk update failed: " + e.getSource().getException().getMessage());
            feedbackLabel.setTextFill(Color.RED);
        });

        new Thread(bulkUpdateTask).start();
    }

    private void printDeploymentLabels(String serialNumber) {
        String sku = assetDAO.findAssetBySerialNumber(serialNumber)
                .map(AssetInfo::getModelNumber)
                .orElse(null);

        if (sku == null) {
            showAlert("Data Not Found", "Could not find an associated SKU for serial: " + serialNumber);
            return;
        }

        String description = skuDAO.findSkuByNumber(sku)
                .map(s -> s.getDescription())
                .orElse("Description not found");

        String adtZpl = ZplPrinterService.getAdtLabelZpl(sku, description);
        String serialZpl = ZplPrinterService.getSerialLabelZpl(sku, serialNumber);

        Optional<String> printerName = findPrinter("GX");
        if (printerName.isEmpty()) {
            showAlert("Printer Not Found", "Could not find a default SKU printer (containing 'GX'). Please configure printers.");
            return;
        }

        boolean s1 = printerService.sendZplToPrinter(printerName.get(), adtZpl);
        boolean s2 = printerService.sendZplToPrinter(printerName.get(), serialZpl);

        if (s1 && s2) {
            feedbackLabel.setText("✔ Deployment labels for " + serialNumber + " sent to " + printerName.get());
        } else {
            feedbackLabel.setText("❌ Failed to print one or both labels for " + serialNumber + ".");
        }
    }

    private Optional<String> findPrinter(String nameHint) {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .filter(name -> name.toLowerCase().contains(nameHint.toLowerCase()))
                .findFirst();
    }

    private void showAlert(String title, String content) {
        StageManager.showAlert(scanSerialField.getScene().getWindow(), Alert.AlertType.WARNING, title, content);
    }

    public static class ScanResult {
        private final SimpleStringProperty serial;
        private final SimpleStringProperty status;
        private final SimpleStringProperty timestamp;

        public ScanResult(String serial, String status, String timestamp) {
            this.serial = new SimpleStringProperty(serial);
            this.status = new SimpleStringProperty(status);
            this.timestamp = new SimpleStringProperty(timestamp);
        }
        public String getSerial() { return serial.get(); }
        public String getStatus() { return status.get(); }
        public String getTimestamp() { return timestamp.get(); }
    }
}