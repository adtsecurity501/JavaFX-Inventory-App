package assettracking.controller;

import assettracking.dao.AssetDAO;
import assettracking.dao.SkuDAO;
import assettracking.data.AssetInfo;
import assettracking.db.DatabaseConnection;
import assettracking.label.service.ZplPrinterService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScanUpdateController {

    // --- FXML Injected Fields ---
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> subStatusCombo;
    @FXML private TextField changeLogField;
    @FXML private TextField scanSerialField;
    @FXML private Label feedbackLabel;
    @FXML private TableView<ScanResult> successTable;
    @FXML private TableColumn<ScanResult, String> successSerialCol;
    @FXML private TableColumn<ScanResult, String> successStatusCol;
    @FXML private TableColumn<ScanResult, String> successTimestampCol;
    @FXML private TableView<ScanResult> failedTable;
    @FXML private TableColumn<ScanResult, String> failedSerialCol;
    @FXML private TableColumn<ScanResult, String> failedReasonCol;
    @FXML private TableColumn<ScanResult, String> failedTimestampCol;

    // --- Services and State ---
    private DeviceStatusTrackingController parentController;
    private final ObservableList<ScanResult> successList = FXCollections.observableArrayList();
    private final ObservableList<ScanResult> failedList = FXCollections.observableArrayList();
    private Map<String, String[]> subStatusOptionsMap;

    // --- DAO and Services for Integrated Printing ---
    private final SkuDAO skuDAO = new SkuDAO();
    private final AssetDAO assetDAO = new AssetDAO();
    private final ZplPrinterService printerService = new ZplPrinterService();


    public void setParentController(DeviceStatusTrackingController parentController) {
        this.parentController = parentController;
    }

    @FXML
    public void initialize() {
        setupStatusMappings();
        setupTableColumns();

        statusCombo.getItems().addAll(subStatusOptionsMap.keySet().stream().sorted().toArray(String[]::new));
        statusCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            subStatusCombo.getItems().clear();
            if (newVal != null && subStatusOptionsMap.containsKey(newVal)) {
                subStatusCombo.getItems().addAll(subStatusOptionsMap.get(newVal));
                subStatusCombo.getSelectionModel().selectFirst();
            }
        });
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

    @FXML
    private void onSerialScanned() {
        String serial = scanSerialField.getText().trim();
        if (serial.isEmpty()) return;

        String newStatus = statusCombo.getValue();
        String newSubStatus = subStatusCombo.getValue();
        String changeNote = changeLogField.getText().trim();

        feedbackLabel.setText("Processing " + serial + "...");
        feedbackLabel.setTextFill(Color.BLUE);

        Task<String> updateTask = new Task<>() {
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
                            stmt.setString(3, changeNote.isEmpty() ? null : changeNote);
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

        updateTask.setOnSucceeded(event -> {
            String result = updateTask.getValue();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            if (result.startsWith("Success")) {
                feedbackLabel.setText("✔ " + result);
                feedbackLabel.setTextFill(Color.GREEN);
                successList.add(0, new ScanResult(serial, newStatus + " / " + newSubStatus, timestamp));
                if (parentController != null) parentController.refreshData();

                // --- MODIFIED WORKFLOW INTEGRATION ---
                if ("Processed".equals(newStatus) && "Ready for Deployment".equals(newSubStatus)) {
                    Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Device is Ready for Deployment. Print SKU and Serial labels?", ButtonType.YES, ButtonType.NO);
                    confirmation.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            printDeploymentLabels(serial);
                        }
                    });
                }
            } else {
                feedbackLabel.setText("✖ " + result);
                feedbackLabel.setTextFill(Color.RED);
                failedList.add(0, new ScanResult(serial, result, timestamp));
            }
            scanSerialField.clear();
            scanSerialField.requestFocus();
        });

        updateTask.setOnFailed(event -> {
            Throwable ex = updateTask.getException();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            feedbackLabel.setText("✖ Error: " + ex.getMessage());
            feedbackLabel.setTextFill(Color.RED);
            failedList.add(0, new ScanResult(serial, "DB Error", timestamp));
            ex.printStackTrace();
            scanSerialField.clear();
            scanSerialField.requestFocus();
        });

        new Thread(updateTask).start();
    }

    private void printDeploymentLabels(String serialNumber) {
        String sku = assetDAO.findAssetBySerialNumber(serialNumber)
                .map(AssetInfo::getModelNumber) // Assuming model_number is your SKU
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

        // Intelligently find the SKU/label printer (e.g., a Zebra GX series)
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
            feedbackLabel.setText("✖ Failed to print one or both labels for " + serialNumber + ".");
        }
    }

    private Optional<String> findPrinter(String nameHint) {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .filter(name -> name.toLowerCase().contains(nameHint.toLowerCase()))
                .findFirst();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void setupStatusMappings() {
        // This could be moved to a shared utility class in the future
        subStatusOptionsMap = Stream.of(new Object[][]{
                {"Disposal/EOL", new String[]{"Can-Am, Pending Pickup", "Ingram, Pending Pickup", "Can-Am, Picked Up", "Ingram, Pick Up"}},
                {"Everon", new String[]{"Pending Shipment", "Shipped"}},
                {"Phone", new String[]{"Pending Shipment", "Shipped"}},
                {"WIP", new String[]{"In Evaluation", "Troubleshooting", "Awaiting Parts", "Awaiting Dell Tech", "Shipped to Dell", "Refurbishment", "Send to Inventory"}},
                {"Processed", new String[]{"Kept in Depot(Parts)", "Kept in Depot(Functioning)", "Ready for Deployment"}}
        }).collect(Collectors.toMap(data -> (String) data[0], data -> (String[]) data[1]));
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