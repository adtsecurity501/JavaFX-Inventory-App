package assettracking.controller;

import assettracking.dao.AssetDAO;
import assettracking.dao.PackageDAO;
import assettracking.data.AssetEntry;
import assettracking.data.AssetInfo;
import assettracking.data.Package;
import assettracking.label.service.ZplPrinterService;
import assettracking.manager.ScanResultManager;
import assettracking.manager.ScanUpdateService;
import assettracking.manager.StageManager;
import assettracking.manager.StatusManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ScanUpdateController {

    // --- FXML Fields ---
    @FXML private ComboBox<String> statusCombo, subStatusCombo;
    @FXML private TextField changeLogField, scanSerialField, disposalLocationField, scanLocationField;
    @FXML private Label feedbackLabel, disposalLocationLabel;
    @FXML private TableView<ScanResult> successTable, failedTable;
    @FXML private TableColumn<ScanResult, String> successSerialCol, successStatusCol, successTimestampCol;
    @FXML private TableColumn<ScanResult, String> failedSerialCol, failedReasonCol, failedTimestampCol;
    @FXML private HBox boxIdHBox;
    @FXML private CheckBox printLabelsToggle;

    // --- Services and Managers ---
    private final ScanUpdateService updateService = new ScanUpdateService();
    private final ScanResultManager resultManager = new ScanResultManager();
    private final AssetDAO assetDAO = new AssetDAO();
    private final ZplPrinterService printerService = new ZplPrinterService();
    private DeviceStatusTrackingController parentController;

    public void setParentController(DeviceStatusTrackingController parentController) {
        this.parentController = parentController;
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        setupStatusComboBoxes();
        updateUiForStatusChange();
    }

    private void setupTableColumns() {
        successSerialCol.setCellValueFactory(new PropertyValueFactory<>("serial"));
        successStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        successTimestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        successTable.setItems(resultManager.getSuccessList());

        failedSerialCol.setCellValueFactory(new PropertyValueFactory<>("serial"));
        failedReasonCol.setCellValueFactory(new PropertyValueFactory<>("status")); // 'status' property holds the reason
        failedTimestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        failedTable.setItems(resultManager.getFailedList());
    }

    private void setupStatusComboBoxes() {
        statusCombo.getItems().addAll(StatusManager.getStatuses());
        statusCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            subStatusCombo.getItems().clear();
            if (n != null) {
                subStatusCombo.getItems().addAll(StatusManager.getSubStatuses(n));
                if (!subStatusCombo.getItems().isEmpty()) {
                    subStatusCombo.getSelectionModel().select(0);
                }
            }
            updateUiForStatusChange();
        });
        subStatusCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateUiForStatusChange());
        statusCombo.getSelectionModel().select(0);
    }

    private void updateUiForStatusChange() {
        String status = statusCombo.getValue();
        String subStatus = subStatusCombo.getValue();

        boolean isDisposal = "Disposed".equals(status);
        boolean needsBoxId = isDisposal && !"Ready for Wipe".equals(subStatus);
        disposalLocationLabel.setVisible(isDisposal);
        boxIdHBox.setVisible(isDisposal);
        scanSerialField.setDisable(needsBoxId && disposalLocationField.getText().trim().isEmpty());

        boolean isReadyForDeployment = "Processed".equals(status) && "Ready for Deployment".equals(subStatus);
        printLabelsToggle.setVisible(isReadyForDeployment);
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

        setFeedback("Processing " + serial + "...", Color.BLUE);

        Task<ScanUpdateService.UpdateResult> updateTask = new Task<>() {
            @Override
            protected ScanUpdateService.UpdateResult call() throws Exception {
                return updateService.updateBySerial(serial, newStatus, newSubStatus, finalNote);
            }
        };

        updateTask.setOnSucceeded(event -> {
            switch (updateTask.getValue()) {
                case SUCCESS:
                    setFeedback("✔ Success: " + serial, Color.GREEN);
                    resultManager.addSuccess(serial, newStatus + " / " + newSubStatus);
                    if (parentController != null) parentController.refreshData();
                    if (printLabelsToggle.isVisible() && printLabelsToggle.isSelected()) {
                        printDeploymentLabels(serial);
                    }
                    break;
                case NOT_FOUND:
                    setFeedback("❌ Not Found: " + serial, Color.RED);
                    resultManager.addFailure(serial, "Not Found in Database");
                    break;
            }
            scanSerialField.clear();
            scanSerialField.requestFocus();
        });

        updateTask.setOnFailed(event -> {
            setFeedback("❌ DB Error: " + updateTask.getException().getMessage(), Color.RED);
            resultManager.addFailure(serial, "Database Error");
        });

        new Thread(updateTask).start();
    }

    @FXML
    private void onUpdateByLocation() {
        String location = scanLocationField.getText().trim();
        if (location.isEmpty()) return;

        Task<List<Integer>> findDevicesTask = new Task<>() {
            @Override
            protected List<Integer> call() throws Exception {
                return updateService.findDeviceReceiptsByLocation(location);
            }
        };

        findDevicesTask.setOnSucceeded(e -> {
            List<Integer> receiptIds = findDevicesTask.getValue();
            if (receiptIds.isEmpty()) {
                showAlert("No Devices Found", "No active devices found with Box ID: '" + location + "'");
                return;
            }
            confirmAndPerformBulkUpdate(receiptIds, location);
        });

        findDevicesTask.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Error", "Failed to query devices by location: " + e.getSource().getException().getMessage()));
        new Thread(findDevicesTask).start();
    }

    private void confirmAndPerformBulkUpdate(List<Integer> receiptIds, String location) {
        String newStatus = statusCombo.getValue();
        String newSubStatus = subStatusCombo.getValue();
        String finalNote = changeLogField.getText().trim();

        String header = "Update all devices in Box ID '" + location + "'?";
        String content = String.format("You are about to update %d device(s) to:%nStatus: %s%nSub-Status: %s%nThis action cannot be undone.", receiptIds.size(), newStatus, newSubStatus);

        if (StageManager.showConfirmationDialog(getStage(), "Confirm Bulk Update", header, content)) {
            Task<Integer> bulkUpdateTask = new Task<>() {
                @Override
                protected Integer call() throws Exception {
                    return updateService.updateByReceiptIds(receiptIds, newStatus, newSubStatus, finalNote);
                }
            };
            bulkUpdateTask.setOnSucceeded(e -> {
                int count = bulkUpdateTask.getValue();
                setFeedback(String.format("✔ Successfully updated %d devices in '%s'.", count, location), Color.GREEN);
                resultManager.addSuccess("Box ID: " + location, String.format("Updated %d devices", count));
                if (parentController != null) parentController.refreshData();
                scanLocationField.clear();
            });
            bulkUpdateTask.setOnFailed(e -> setFeedback("❌ Bulk update failed: " + e.getSource().getException().getMessage(), Color.RED));
            new Thread(bulkUpdateTask).start();
        }
    }

    @FXML
    private void handleProcessFailedScans() {
        if (!resultManager.hasFailedScans()) {
            showAlert("No Failed Scans", "There are no failed scans to process.");
            return;
        }

        String trackingNumber = "FAILED_SCANS_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        int packageId = new PackageDAO().addPackage(trackingNumber, "SYSTEM", "GENERATED", "DEPOT", "UT", "84660", java.time.LocalDate.now());

        if (packageId == -1) {
            showAlert("Database Error", "Could not create a new package for the failed scans.");
            return;
        }

        Package newPackage = new Package(packageId, trackingNumber, "SYSTEM", "GENERATED", "DEPOT", "UT", "84660", java.time.LocalDate.now());
        List<AssetEntry> failedEntries = new ArrayList<>();
        resultManager.getFailedSerials().forEach(serial -> failedEntries.add(new AssetEntry(serial, "", "", "", "", "", "")));

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/AddAssetDialog.fxml"));
            Parent root = loader.load();
            AddAssetDialogController controller = loader.getController();
            controller.initDataForBulkAdd(newPackage, failedEntries);
            Stage stage = StageManager.createCustomStage(getStage(), "Process Failed Scans for Package " + trackingNumber, root);
            stage.showAndWait();
            resultManager.clearFailedScans();
            if (parentController != null) parentController.refreshData();
        } catch (IOException e) {
            showAlert("Error", "Could not open the 'Add Asset' window.");
        }
    }

    private void printDeploymentLabels(String serialNumber) {
        Optional<AssetInfo> assetOpt = assetDAO.findAssetBySerialNumber(serialNumber);
        if (assetOpt.isEmpty() || assetOpt.get().getModelNumber() == null) {
            resultManager.addFailure(serialNumber, "SKU not found for printing");
            return;
        }
        AssetInfo asset = assetOpt.get();
        String description = asset.getDescription() != null ? asset.getDescription() : "N/A";

        String adtZpl = ZplPrinterService.getAdtLabelZpl(asset.getModelNumber(), description);
        String serialZpl = ZplPrinterService.getSerialLabelZpl(asset.getModelNumber(), serialNumber);

        Optional<String> printerName = findPrinter("GX");
        if (printerName.isEmpty()) {
            showAlert("Printer Not Found", "Could not find a default SKU printer (containing 'GX').");
            return;
        }

        printerService.sendZplToPrinter(printerName.get(), adtZpl);
        printerService.sendZplToPrinter(printerName.get(), serialZpl);
    }

    // --- UI and Helper Methods ---
    @FXML private void onBoxIdScanned() { scanSerialField.requestFocus(); }
    @FXML private void handleClearBoxId() { disposalLocationField.clear(); changeLogField.clear(); disposalLocationField.requestFocus(); }
    private void setFeedback(String message, Color color) { feedbackLabel.setText(message); feedbackLabel.setTextFill(color); }
    private void showAlert(String title, String content) { StageManager.showAlert(getStage(), Alert.AlertType.WARNING, title, content); }
    private Optional<String> findPrinter(String hint) { return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null)).map(PrintService::getName).filter(n -> n.toLowerCase().contains(hint.toLowerCase())).findFirst(); }
    private Stage getStage() { return (Stage) scanSerialField.getScene().getWindow(); }

    public static class ScanResult {
        private final SimpleStringProperty serial, status, timestamp;
        public ScanResult(String serial, String status, String timestamp) { this.serial = new SimpleStringProperty(serial); this.status = new SimpleStringProperty(status); this.timestamp = new SimpleStringProperty(timestamp); }
        public String getSerial() { return serial.get(); }
        public String getStatus() { return status.get(); }
        public String getTimestamp() { return timestamp.get(); }
    }
}