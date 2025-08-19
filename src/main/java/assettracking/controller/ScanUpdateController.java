package assettracking.controller;

import assettracking.db.DatabaseConnection;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
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

    private DeviceStatusTrackingController parentController;
    private ObservableList<ScanResult> successList = FXCollections.observableArrayList();
    private ObservableList<ScanResult> failedList = FXCollections.observableArrayList();
    private Map<String, String[]> subStatusOptionsMap;

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

    private void setupStatusMappings() {
        // Duplicated from main controller, could be moved to a shared utility class
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