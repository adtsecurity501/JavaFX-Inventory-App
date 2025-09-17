package assettracking.controller;

import assettracking.dao.PackageDAO;
import assettracking.dao.SkuDAO;
import assettracking.data.AssetEntry;
import assettracking.data.Package;
import assettracking.data.Sku;
import assettracking.label.service.ZplPrinterService;
import assettracking.manager.ScanResultManager;
import assettracking.manager.ScanUpdateService;
import assettracking.manager.StageManager;
import assettracking.manager.StatusManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ScanUpdateController {

    // --- Services and Managers ---
    private final ScanUpdateService updateService = new ScanUpdateService();
    private final ScanResultManager resultManager = new ScanResultManager();
    private final PackageDAO packageDAO = new PackageDAO();
    private final SkuDAO skuDAO = new SkuDAO();
    private final ZplPrinterService printerService = new ZplPrinterService();
    public Button clearBoxIdButton;
    // --- FXML Fields ---
    @FXML
    private ComboBox<String> statusCombo, subStatusCombo;
    @FXML
    private TextField changeLogField, scanSerialField, disposalLocationField, scanLocationField;
    @FXML
    private Label feedbackLabel, disposalLocationLabel;
    @FXML
    private TableView<ScanResult> successTable, failedTable;
    @FXML
    private TableColumn<ScanResult, String> successSerialCol, successStatusCol, successTimestampCol;
    @FXML
    private TableColumn<ScanResult, String> failedSerialCol, failedReasonCol, failedTimestampCol;
    @FXML
    private HBox boxIdHBox;
    @FXML
    private CheckBox printLabelsToggle;
    @FXML
    private Button clearSkuButton;
    // --- NEW FXML FIELDS FOR SKU SELECTION & PRINTER ---
    @FXML
    private VBox skuSelectionBox;
    @FXML
    private TextField skuSearchField;
    @FXML
    private ListView<String> skuListView;
    @FXML
    private TextField selectedSkuField;
    @FXML
    private ComboBox<String> labelPrinterCombo;
    private DeviceStatusTrackingController parentController;

    public void setParentController(DeviceStatusTrackingController parentController) {
        this.parentController = parentController;
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        setupStatusComboBoxes();
        setupSkuSearch();
        populatePrinters(); // <-- ADD THIS
        clearSkuButton.setOnAction(e -> selectedSkuField.clear()); // Add this line


        // Add listeners to re-evaluate the UI state
        disposalLocationField.textProperty().addListener((obs, oldText, newText) -> updateUiForStatusChange());
        printLabelsToggle.selectedProperty().addListener((obs, wasSelected, isSelected) -> updateUiForStatusChange());

        updateUiForStatusChange(); // Initial UI setup
    }

    private void populatePrinters() {
        List<String> printerNames = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null)).map(PrintService::getName).collect(Collectors.toList());
        labelPrinterCombo.setItems(FXCollections.observableArrayList(printerNames));

        // Try to set a sensible default, like a Zebra GX printer
        printerNames.stream().filter(n -> n.toLowerCase().contains("gx")).findFirst().ifPresent(labelPrinterCombo::setValue);
    }

    private void setupSkuSearch() {
        skuSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                skuListView.getItems().clear();
                return;
            }

            // This line should call your new combined search method.
            List<String> suggestions = skuDAO.findSkusByKeywordOrSkuNumber(newVal);

            skuListView.setItems(FXCollections.observableArrayList(suggestions));
        });

        // The rest of this method remains the same...
        skuListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String selectedSku = newSelection.split(" - ")[0];
                Platform.runLater(() -> {
                    selectedSkuField.setText(selectedSku);
                    skuSearchField.clear();
                    skuListView.getItems().clear();
                    scanSerialField.requestFocus();
                });
            }
        });
    }


    private String sanitizeSerialNumber(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }

    @FXML
    private void onSerialScanned() {
        String serial = sanitizeSerialNumber(scanSerialField.getText());
        scanSerialField.setText(serial);
        if (serial.isEmpty()) return;

        String skuToPrint = selectedSkuField.getText().trim();
        String printerName = labelPrinterCombo.getValue();

        if (printLabelsToggle.isVisible() && printLabelsToggle.isSelected()) {
            if (printerName == null || printerName.isEmpty()) {
                showAlert("Printer Not Selected", "A printer must be selected from the list to print labels.");
                labelPrinterCombo.requestFocus();
                return;
            }
            if (skuToPrint.isEmpty()) {
                showAlert("SKU Required", "A SKU must be selected from the list before printing labels.");
                skuSearchField.requestFocus();
                return;
            }
        }

        String newStatus = statusCombo.getValue();
        String newSubStatus = subStatusCombo.getValue();
        String boxId = disposalLocationField.getText().trim();

        if ("Disposed".equals(newStatus) && !"Ready for Wipe".equals(newSubStatus) && boxId.isEmpty()) {
            showAlert("Box ID Required", "A Box ID must be entered for this disposed status.");
            disposalLocationField.requestFocus();
            return;
        }

        String finalNote = changeLogField.getText().trim();

        setFeedback("Processing " + serial + "...", Color.BLUE);

        Task<ScanUpdateService.UpdateResult> updateTask = new Task<>() {
            @Override
            protected ScanUpdateService.UpdateResult call() throws Exception {
                return updateService.updateBySerial(serial, newStatus, newSubStatus, finalNote, boxId);
            }
        };

        updateTask.setOnSucceeded(event -> {
            // --- THIS IS THE FIX ---
            // Before updating UI, check if the window still exists.
            if (scanSerialField.getScene() == null || scanSerialField.getScene().getWindow() == null) {
                return; // The window was closed, so do nothing.
            }
            // --- END OF FIX ---

            switch (updateTask.getValue()) {
                case SUCCESS:
                    setFeedback("✓ Success: " + serial, Color.GREEN);
                    resultManager.addSuccess(serial, newStatus + " / " + newSubStatus);
                    if (parentController != null) parentController.refreshData();
                    if (printLabelsToggle.isVisible() && printLabelsToggle.isSelected()) {
                        printDeploymentLabels(serial, skuToPrint, printerName);
                    }
                    break;
                case NOT_FOUND:
                    setFeedback("✗ Not Found: " + serial, Color.RED);
                    resultManager.addFailure(serial, "Not Found in Database");
                    break;
            }
            scanSerialField.clear();
            scanSerialField.requestFocus();
        });

        updateTask.setOnFailed(event -> {
            // --- THIS IS THE FIX ---
            // Also add the check to the failure handler.
            if (scanSerialField.getScene() == null || scanSerialField.getScene().getWindow() == null) {
                return; // The window was closed, so do nothing.
            }
            // --- END OF FIX ---

            setFeedback("DB Error: " + updateTask.getException().getMessage(), Color.RED);
            resultManager.addFailure(serial, "Database Error");
        });

        new Thread(updateTask).start();
    }

    private void updateUiForStatusChange() {
        String status = statusCombo.getValue();
        String subStatus = subStatusCombo.getValue();

        boolean isDisposal = "Disposed".equals(status);
        boolean needsBoxId = isDisposal && !"Ready for Wipe".equals(subStatus);

        disposalLocationLabel.setVisible(isDisposal);
        disposalLocationLabel.setManaged(isDisposal);
        boxIdHBox.setVisible(isDisposal);
        boxIdHBox.setManaged(isDisposal);
        if (!needsBoxId) {
            disposalLocationField.clear();
        }

        boolean isReadyForDeployment = "Processed".equals(status) && "Ready for Deployment".equals(subStatus);
        printLabelsToggle.setVisible(isReadyForDeployment);
        printLabelsToggle.setManaged(isReadyForDeployment);

        boolean showSkuBox = isReadyForDeployment && printLabelsToggle.isSelected();
        skuSelectionBox.setVisible(showSkuBox);
        skuSelectionBox.setManaged(showSkuBox);
        if (!showSkuBox) {
            selectedSkuField.clear();
        }

        scanSerialField.setDisable(needsBoxId && disposalLocationField.getText().trim().isEmpty());
    }

    private void printDeploymentLabels(String serialNumber, String sku, String printerName) {
        // --- THIS IS THE KEY CHANGE ---
        // Instead of looking up the asset's old description, we now look up the
        // official description from the SKU table using the selected SKU number.
        String description = skuDAO.findSkuByNumber(sku).map(Sku::getDescription) // If the Sku object is found, get its description
                .orElse("Description not found for SKU"); // Provide a fallback if it's missing

        // The rest of the method remains the same, but now uses the correct description.
        String adtZpl = ZplPrinterService.getAdtLabelZpl(sku, description);
        String serialZpl = ZplPrinterService.getSerialLabelZpl(sku, serialNumber);

        printerService.sendZplToPrinter(printerName, adtZpl);
        printerService.sendZplToPrinter(printerName, serialZpl);
    }

    // --- All other methods (setupTableColumns, setupStatusComboBoxes, etc.) remain unchanged ---
    private void setupTableColumns() {
        successSerialCol.setCellValueFactory(new PropertyValueFactory<>("serial"));
        successStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        successTimestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        successTable.setItems(resultManager.getSuccessList());

        failedSerialCol.setCellValueFactory(new PropertyValueFactory<>("serial"));
        failedReasonCol.setCellValueFactory(new PropertyValueFactory<>("status"));
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

    @FXML
    private void handleBulkIntake() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/BulkIntakeDialog.fxml"));
            Parent root = loader.load();

            BulkIntakeDialogController dialogController = loader.getController();
            dialogController.init(this.parentController); // Pass a reference to the main controller

            Stage stage = StageManager.createCustomStage(getStage(), "Bulk Intake from List", root);
            stage.showAndWait();

        } catch (IOException e) {
            System.err.println("Service error: " + e.getMessage());
            showAlert("Error", "Could not open the Bulk Intake window.");
        }
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
                setFeedback(String.format("✓ Successfully updated %d devices in '%s'.", count, location), Color.GREEN); // Checkmark ✓
                resultManager.addSuccess("Box ID: " + location, String.format("Updated %d devices", count));
                if (parentController != null) parentController.refreshData();
                scanLocationField.clear();
            });
            bulkUpdateTask.setOnFailed(e -> setFeedback("✖ Bulk update failed: " + e.getSource().getException().getMessage(), Color.RED)); // X mark ✗
            new Thread(bulkUpdateTask).start();
        }
    }

    @FXML
    private void handleProcessFailedScans() {
        if (!resultManager.hasFailedScans()) {
            showAlert("No Failed Scans", "There are no failed scans to process.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/SelectPackageDialog.fxml"));
            Parent root = loader.load();
            SelectPackageDialogController dialogController = loader.getController();

            Stage stage = StageManager.createCustomStage(getStage(), "Select Package for Failed Scans", root);
            stage.showAndWait();

            dialogController.getResult().ifPresent(result -> {
                if (result.createNew()) {
                    // User chose to create a new package
                    String trackingNumber = "FAILED_SCANS_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    // Use the class field 'packageDAO' instead of creating a new instance
                    int packageId = packageDAO.addPackage(trackingNumber, "SYSTEM", "GENERATED", "DEPOT", "UT", "84660", java.time.LocalDate.now());

                    if (packageId != -1) {
                        Package newPackage = new Package(packageId, trackingNumber, "SYSTEM", "GENERATED", "DEPOT", "UT", "84660", java.time.LocalDate.now());
                        openAddAssetDialogForFailedScans(newPackage);
                    } else {
                        showAlert("Database Error", "Could not create a new package for the failed scans.");
                    }
                } else if (result.selectedPackage() != null) {
                    // User selected an existing package
                    openAddAssetDialogForFailedScans(result.selectedPackage());
                }
            });
        } catch (IOException e) {
            System.err.println("Service error: " + e.getMessage());
            showAlert("Error", "Could not open the package selection dialog.");
        }
    }

    private void openAddAssetDialogForFailedScans(Package targetPackage) {
        List<AssetEntry> failedEntries = new ArrayList<>();
        resultManager.getFailedSerials().forEach(serial -> failedEntries.add(new AssetEntry(serial, "", "", "", "", "", "")));

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/AddAssetDialog.fxml"));
            Parent root = loader.load();
            AddAssetDialogController controller = loader.getController();
            controller.initDataForBulkAdd(targetPackage, failedEntries);

            Stage stage = StageManager.createCustomStage(getStage(), "Process Failed Scans for Package " + targetPackage.getTrackingNumber(), root);
            stage.showAndWait();

            // If successful, clear the list
            resultManager.clearFailedScans();
            if (parentController != null) parentController.refreshData();

        } catch (IOException e) {
            System.err.println("Service error: " + e.getMessage());
            showAlert("Error", "Could not open the 'Add Asset' window.");
        }
    }

    @FXML
    private void onBoxIdScanned() {
        scanSerialField.requestFocus();
    }

    @FXML
    private void handleClearBoxId() {
        disposalLocationField.clear();
        changeLogField.clear();
        disposalLocationField.requestFocus();
    }

    private void setFeedback(String message, Color color) {
        feedbackLabel.setText(message);
        feedbackLabel.setTextFill(color);
    }

    private void showAlert(String title, String content) {
        StageManager.showAlert(getStage(), Alert.AlertType.WARNING, title, content);
    }

    private Stage getStage() {
        return (Stage) scanSerialField.getScene().getWindow();
    }

    public static class ScanResult {
        private final SimpleStringProperty serial, status, timestamp;

        public ScanResult(String serial, String status, String timestamp) {
            this.serial = new SimpleStringProperty(serial);
            this.status = new SimpleStringProperty(status);
            this.timestamp = new SimpleStringProperty(timestamp);
        }

        public String getSerial() {
            return serial.get();
        }

        public String getStatus() {
            return status.get();
        }

        public String getTimestamp() {
            return timestamp.get();
        }
    }
}