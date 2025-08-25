package assettracking.controller;

import assettracking.dao.AssetDAO;
import assettracking.dao.ReceiptEventDAO;
import assettracking.data.AssetEntry;
import assettracking.data.AssetInfo;
import assettracking.data.Package;
import assettracking.data.ReceiptEvent;
import assettracking.db.DatabaseConnection;
import assettracking.label.service.ZplPrinterService;
import assettracking.manager.StatusManager; // <-- IMPORT ADDED
import assettracking.ui.AutoCompletePopup;
import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class AddAssetDialogController {

    // FXML Fields for Intake Mode Switching
    @FXML private RadioButton standardIntakeRadio;
    @FXML private RadioButton monitorIntakeRadio;
    @FXML private ToggleGroup intakeModeToggleGroup;
    @FXML private VBox standardModePane;
    @FXML private VBox monitorModePane;

    // FXML Fields for Monitor Intake
    @FXML private ComboBox<String> monitorPrinterCombo;
    @FXML private TextField monitorSerialField;
    @FXML private TextField monitorModelField;
    @FXML private TextField monitorDescriptionField;
    @FXML private Label monitorFeedbackLabel;

    // FXML Fields for Standard Intake
    @FXML private CheckBox bulkAddCheckBox;
    @FXML private BorderPane textModePane;
    @FXML private BorderPane tableModePane;
    @FXML private ToggleButton multiSerialToggle;
    @FXML private TextField serialField;
    @FXML private TextArea serialArea;
    @FXML private ComboBox<String> categoryBox;
    @FXML private TextField makeField;
    @FXML private TextField modelField;
    @FXML private TextField descriptionField;
    @FXML private TextField imeiField;
    // Note: lookupButton is used by the FXML onAction event, so the warning is safe to ignore.
    @FXML private Button lookupButton;
    @FXML private Label probableCauseLabel;
    @FXML private Label melActionLabel;
    @FXML private TableView<AssetEntry> deviceTable;
    @FXML private TableColumn<AssetEntry, String> serialCol;
    @FXML private TableColumn<AssetEntry, String> imeiCol;
    @FXML private TableColumn<AssetEntry, String> categoryCol;
    @FXML private TableColumn<AssetEntry, String> makeCol;
    @FXML private TableColumn<AssetEntry, String> modelCol;
    @FXML private TableColumn<AssetEntry, String> descriptionCol;
    @FXML private TableColumn<AssetEntry, String> causeCol;
    @FXML private CheckBox sellScrapCheckBox;
    @FXML private Label sellScrapStatusLabel;
    @FXML private ComboBox<String> sellScrapStatusCombo;
    @FXML private Label sellScrapSubStatusLabel;
    @FXML private ComboBox<String> sellScrapSubStatusCombo;
    @FXML private Label disqualificationLabel;
    @FXML private TextField disqualificationField;
    @FXML private Label feedbackLabel;
    @FXML private Button saveButton;
    @FXML private Button closeButton;
    // Note: conditionToggleGroup is used by FXML to group RadioButtons, so the warning is safe to ignore.
    @FXML private ToggleGroup conditionToggleGroup;
    @FXML private RadioButton refurbRadioButton;
    @FXML private RadioButton newRadioButton;

    private Package currentPackage;
    private PackageDetailController parentController;
    private final ObservableList<AssetEntry> assetEntries = FXCollections.observableArrayList();
    private final AssetDAO assetDAO = new AssetDAO();
    private final ReceiptEventDAO receiptEventDAO = new ReceiptEventDAO();
    private final ZplPrinterService printerService = new ZplPrinterService();

    public void initData(Package pkg, PackageDetailController parent) {
        this.currentPackage = pkg;
        this.parentController = parent;
        loadCategories();
    }

    @FXML
    public void initialize() {
        // setupStatusMappings(); // <-- REMOVED
        setupViewToggles();
        setupDispositionControls();
        setupTable();
        setupAutocomplete();
        setupMonitorIntake();

        refurbRadioButton.setSelected(true);
        standardIntakeRadio.setSelected(true);
    }

    private void loadCategories() {
        Task<List<String>> loadCategoriesTask = new Task<>() {
            @Override
            protected List<String> call() {
                return new ArrayList<>(assetDAO.getAllDistinctCategories());
            }
        };
        loadCategoriesTask.setOnSucceeded(e -> categoryBox.setItems(FXCollections.observableArrayList(loadCategoriesTask.getValue())));
        new Thread(loadCategoriesTask).start();
    }

    // setupStatusMappings() method was here <-- REMOVED

    private void setupMonitorIntake() {
        new AutoCompletePopup(monitorDescriptionField, () -> assetDAO.findDescriptionsLike(monitorDescriptionField.getText()))
                .setOnSuggestionSelected(selectedValue ->
                        assetDAO.findSkuDetails(selectedValue, "description").ifPresent(sku -> Platform.runLater(() -> {
                            monitorDescriptionField.setText(sku.getDescription());
                            monitorModelField.setText(sku.getModelNumber());
                        }))
                );

        List<String> printerNames = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName).collect(Collectors.toList());
        monitorPrinterCombo.setItems(FXCollections.observableArrayList(printerNames));

        printerNames.stream().filter(n -> n.toLowerCase().contains("gx")).findFirst()
                .ifPresent(monitorPrinterCombo::setValue);
    }

    private void setupViewToggles() {
        intakeModeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isMonitorMode = newVal == monitorIntakeRadio;
            monitorModePane.setVisible(isMonitorMode);
            monitorModePane.setManaged(isMonitorMode);
            standardModePane.setVisible(!isMonitorMode);
            standardModePane.setManaged(!isMonitorMode);
        });

        tableModePane.setVisible(false);
        tableModePane.setManaged(false);
        serialArea.setVisible(false);
        serialArea.setManaged(false);

        bulkAddCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean isBulkMode = newVal;
            textModePane.setVisible(!isBulkMode);
            textModePane.setManaged(!isBulkMode);
            tableModePane.setVisible(isBulkMode);
            tableModePane.setManaged(isBulkMode);
        });

        multiSerialToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean isMulti = newVal;
            serialField.setVisible(!isMulti);
            serialField.setManaged(!isMulti);
            serialArea.setVisible(isMulti);
            serialArea.setManaged(isMulti);
            if (isMulti) serialArea.requestFocus(); else serialField.requestFocus();
        });
    }

    private void setupDispositionControls() {
        sellScrapStatusLabel.setDisable(true);
        sellScrapStatusCombo.setDisable(true);
        sellScrapSubStatusLabel.setDisable(true);
        sellScrapSubStatusCombo.setDisable(true);
        disqualificationLabel.setDisable(true);
        disqualificationField.setDisable(true);

        sellScrapStatusCombo.setItems(FXCollections.observableArrayList(StatusManager.getStatuses())); // <-- MODIFIED
        sellScrapStatusCombo.getSelectionModel().selectFirst();

        sellScrapStatusCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            sellScrapSubStatusCombo.getItems().clear();
            if (newVal != null) { // <-- MODIFIED BLOCK
                sellScrapSubStatusCombo.getItems().addAll(StatusManager.getSubStatuses(newVal));
                if (!sellScrapSubStatusCombo.getItems().isEmpty()) {
                    sellScrapSubStatusCombo.getSelectionModel().selectFirst();
                }
            }
        });

        if (sellScrapStatusCombo.getValue() != null) { // <-- MODIFIED BLOCK
            sellScrapSubStatusCombo.getItems().addAll(StatusManager.getSubStatuses(sellScrapStatusCombo.getValue()));
            sellScrapSubStatusCombo.getSelectionModel().selectFirst();
        }

        sellScrapCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean selected = newVal;
            sellScrapStatusLabel.setDisable(!selected);
            sellScrapStatusCombo.setDisable(!selected);
            sellScrapSubStatusLabel.setDisable(!selected);
            sellScrapSubStatusCombo.setDisable(!selected);
            disqualificationLabel.setDisable(!selected);
            disqualificationField.setDisable(!selected);
        });
    }

    private void setupTable() {
        serialCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        serialCol.setCellFactory(TextFieldTableCell.forTableColumn());
        serialCol.setOnEditCommit(event -> event.getRowValue().setSerialNumber(event.getNewValue()));

        imeiCol.setCellValueFactory(new PropertyValueFactory<>("imei"));
        imeiCol.setCellFactory(TextFieldTableCell.forTableColumn());
        imeiCol.setOnEditCommit(event -> event.getRowValue().setImei(event.getNewValue()));

        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setCellFactory(TextFieldTableCell.forTableColumn());
        categoryCol.setOnEditCommit(event -> event.getRowValue().setCategory(event.getNewValue()));

        makeCol.setCellValueFactory(new PropertyValueFactory<>("make"));
        makeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        makeCol.setOnEditCommit(event -> event.getRowValue().setMake(event.getNewValue()));

        modelCol.setCellValueFactory(new PropertyValueFactory<>("modelNumber"));
        modelCol.setCellFactory(TextFieldTableCell.forTableColumn());
        modelCol.setOnEditCommit(event -> event.getRowValue().setModelNumber(event.getNewValue()));

        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descriptionCol.setOnEditCommit(event -> event.getRowValue().setDescription(event.getNewValue()));

        causeCol.setCellValueFactory(new PropertyValueFactory<>("probableCause"));

        deviceTable.setItems(assetEntries);
        deviceTable.setEditable(true);

        deviceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    private void setupAutocomplete() {
        new AutoCompletePopup(descriptionField, () -> assetDAO.findDescriptionsLike(descriptionField.getText()))
                .setOnSuggestionSelected(selectedValue ->
                        assetDAO.findSkuDetails(selectedValue, "description").ifPresent(this::populateFieldsFromSku)
                );

        new AutoCompletePopup(modelField, () -> assetDAO.findModelNumbersLike(modelField.getText()))
                .setOnSuggestionSelected(selectedValue -> assetDAO.findSkuDetails(selectedValue, "model_number").ifPresent(this::populateFieldsFromSku));
    }

    private void populateFieldsFromSku(AssetInfo sku) {
        Platform.runLater(() -> {
            descriptionField.setText(sku.getDescription());
            modelField.setText(sku.getModelNumber());
            makeField.setText(sku.getMake());
            if (sku.getCategory() != null && !sku.getCategory().isEmpty()) {
                categoryBox.setValue(sku.getCategory());
            }
            assetDAO.findActionFromMelRules(sku.getModelNumber(), sku.getDescription()).ifPresent(action ->
                    melActionLabel.setText("MEL Action: " + action));
        });
    }

    @FXML
    private void handleLookupSerial() {
        String serialToLookup = serialField.getText().trim();
        if (serialToLookup.isEmpty()) return;

        imeiField.clear();
        categoryBox.getSelectionModel().clearSelection();
        makeField.clear();
        modelField.clear();
        descriptionField.clear();
        probableCauseLabel.setText("");
        melActionLabel.setText("");

        assetDAO.findAssetBySerialNumber(serialToLookup).ifPresent(asset -> Platform.runLater(() -> {
            imeiField.setText(asset.getImei());
            categoryBox.setValue(asset.getCategory());
            makeField.setText(asset.getMake());
            modelField.setText(asset.getModelNumber());
            descriptionField.setText(asset.getDescription());

            assetDAO.findActionFromMelRules(asset.getModelNumber(), asset.getDescription()).ifPresent(action ->
                    melActionLabel.setText("MEL Action: " + action));
        }));

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement flagStmt = conn.prepareStatement("SELECT flag_reason, sub_status FROM Flag_Devices WHERE serial_number = ?")) {
            flagStmt.setString(1, serialToLookup);
            ResultSet rs = flagStmt.executeQuery();
            if (rs.next()) {
                final String reason = rs.getString("flag_reason");
                final String subStatus = rs.getString("sub_status");
                Platform.runLater(() -> {
                    probableCauseLabel.setText("Flagged Reason: " + reason);
                    sellScrapCheckBox.setSelected(true);
                    sellScrapStatusCombo.setValue("Action Required");
                    sellScrapSubStatusCombo.setValue(subStatus);
                });
            }
        } catch (SQLException e) {
            Platform.runLater(() -> probableCauseLabel.setText("DB Error checking flag."));
        }
    }

    @FXML
    private void handleAddRow() {
        assetEntries.add(new AssetEntry("", "", "", "", "", "", ""));
    }

    @FXML
    private void handleRemoveRow() {
        AssetEntry selected = deviceTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            assetEntries.remove(selected);
        }
    }

    @FXML
    private void handleSave() {
        saveButton.setDisable(true);
        closeButton.setDisable(true);
        feedbackLabel.setText("Processing...");

        Task<String> saveTask = createSaveTask();

        saveTask.setOnSucceeded(event -> {
            String result = saveTask.getValue();
            if (parentController != null) {
                parentController.refreshData();
            }

            if (result.toLowerCase().contains("error")) {
                feedbackLabel.setText(result);
                saveButton.setDisable(false);
                closeButton.setDisable(false);
            } else {
                handleClose();
            }
        });

        saveTask.setOnFailed(event -> {
            Throwable ex = saveTask.getException();
            String errorMessage = "Critical Error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            feedbackLabel.setText(errorMessage);
            StageManager.showAlert(saveButton.getScene().getWindow(), Alert.AlertType.ERROR, "Save Failed", errorMessage);
            saveButton.setDisable(false);
            closeButton.setDisable(false);
        });

        new Thread(saveTask).start();
    }

    private Task<String> createSaveTask() {
        final boolean isBulkMode = bulkAddCheckBox.isSelected();
        final String[] serials = multiSerialToggle.isSelected() ? serialArea.getText().trim().split("\\r?\\n") : new String[]{serialField.getText().trim()};
        final List<AssetEntry> entriesFromTable = new ArrayList<>(assetEntries);

        final boolean isScrap = sellScrapCheckBox.isSelected();
        final String scrapStatus = sellScrapStatusCombo.getValue();
        final String scrapSubStatus = sellScrapSubStatusCombo.getValue();
        final String scrapReason = disqualificationField.getText().trim();
        final boolean isNewCondition = newRadioButton.isSelected();

        final AssetInfo singleEntryInfo = !isBulkMode ? new AssetInfo("", makeField.getText(), modelField.getText(), descriptionField.getText(), categoryBox.getValue(), imeiField.getText(), false, "") : null;

        return new Task<>() {
            @Override
            protected String call() throws Exception {
                if (isBulkMode) {
                    return processTable(entriesFromTable, isScrap, scrapStatus, scrapSubStatus, scrapReason, isNewCondition);
                } else {
                    return processTextArea(serials, singleEntryInfo, isScrap, scrapStatus, scrapSubStatus, scrapReason, isNewCondition);
                }
            }
        };
    }

    private String processTextArea(String[] serialNumbers, AssetInfo singleEntryDetails, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, boolean isNewCondition) throws SQLException {
        if (serialNumbers.length == 0 || serialNumbers[0].isEmpty()) {
            return "Input Required: Please enter at least one serial number.";
        }

        int successCount = 0, returnCount = 0, newCount = 0;
        StringBuilder errors = new StringBuilder();

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            conn.setAutoCommit(false);
            for (String originalSerial : serialNumbers) {
                final String serial = originalSerial.trim();
                if (serial.isEmpty()) continue;

                boolean isReturn = assetDAO.findAssetBySerialNumber(serial).isPresent();

                AssetInfo assetInfo = assetDAO.findAssetBySerialNumber(serial).orElse(singleEntryDetails);
                assetInfo.setSerialNumber(serial);

                if (!isReturn) {
                    assetDAO.addAsset(assetInfo);
                    newCount++;
                } else {
                    returnCount++;
                }

                ReceiptEvent newReceipt = new ReceiptEvent(0, serial, currentPackage.getPackageId(), assetInfo.getCategory(), assetInfo.getMake(), assetInfo.getModelNumber(), assetInfo.getDescription(), assetInfo.getImei());
                int newReceiptId = receiptEventDAO.addReceiptEvent(newReceipt);

                if (newReceiptId != -1) {
                    createInitialStatus(conn, newReceiptId, isNewCondition, isScrap, scrapStatus, scrapSubStatus, scrapReason);
                    successCount++;
                } else {
                    errors.append("Failed to create receipt for S/N: ").append(serial).append("\n");
                }
            }

            if (!errors.isEmpty()) {
                conn.rollback();
                return "Errors occurred:\n" + errors;
            } else {
                conn.commit();
                return String.format("Successfully processed %d receipts (%d new, %d returns).", successCount, newCount, returnCount);
            }
        }
    }

    private String processTable(List<AssetEntry> entries, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, boolean isNewCondition) throws SQLException {
        if (entries.isEmpty()) { return "No devices in the table to process."; }

        int successCount = 0, duplicateCount = 0;
        StringBuilder errors = new StringBuilder();
        Set<String> processedSerials = new HashSet<>();


        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            conn.setAutoCommit(false);
            for (AssetEntry entry : entries) {
                String serial = entry.getSerialNumber().trim();
                if (serial.isEmpty()) continue;

                if (processedSerials.contains(serial)) {
                    duplicateCount++;
                    continue;
                }

                boolean isReturn = assetDAO.findAssetBySerialNumber(serial).isPresent();
                AssetInfo assetInfo = new AssetInfo(serial, entry.getMake(), entry.getModelNumber(), entry.getDescription(), entry.getCategory(), entry.getImei(), false, "");

                if (!isReturn) {
                    assetDAO.addAsset(assetInfo);
                }

                ReceiptEvent newReceipt = new ReceiptEvent(0, serial, currentPackage.getPackageId(), assetInfo.getCategory(), assetInfo.getMake(), assetInfo.getModelNumber(), assetInfo.getDescription(), assetInfo.getImei());
                int newReceiptId = receiptEventDAO.addReceiptEvent(newReceipt);

                if (newReceiptId != -1) {
                    createInitialStatus(conn, newReceiptId, isNewCondition, isScrap, scrapStatus, scrapSubStatus, scrapReason);
                    successCount++;
                    processedSerials.add(serial);
                } else {
                    errors.append("Failed to create receipt for S/N: ").append(serial).append("\n");
                }
            }

            if (!errors.isEmpty()) {
                conn.rollback();
                return "Errors occurred:\n" + errors;
            } else {
                conn.commit();
                String result = String.format("Successfully processed %d assets.", successCount);
                if (duplicateCount > 0) {
                    result += " Skipped " + duplicateCount + " duplicate serial(s).";
                }
                return result;
            }
        }
    }

    private void createInitialStatus(Connection conn, int receiptId, boolean isNewCondition, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason) throws SQLException {
        String finalStatus;
        String finalSubStatus;

        if (isScrap) {
            finalStatus = scrapStatus;
            finalSubStatus = scrapSubStatus;
        } else if (isNewCondition) {
            finalStatus = "Processed";
            finalSubStatus = "Ready for Deployment";
        } else {
            finalStatus = "WIP";
            finalSubStatus = "In Evaluation";
        }

        if (recordExistsByReceiptId(conn, "Device_Status", receiptId)) {
            String statusQuery = "UPDATE Device_Status SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP WHERE receipt_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(statusQuery)) {
                stmt.setString(1, finalStatus);
                stmt.setString(2, finalSubStatus);
                stmt.setInt(3, receiptId);
                stmt.executeUpdate();
            }
        } else {
            String statusQuery = "INSERT INTO Device_Status (receipt_id, status, sub_status, last_update) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement stmt = conn.prepareStatement(statusQuery)) {
                stmt.setInt(1, receiptId);
                stmt.setString(2, finalStatus);
                stmt.setString(3, finalSubStatus);
                stmt.executeUpdate();
            }
        }

        if (recordExistsByReceiptId(conn, "Disposition_Info", receiptId)) {
            String dispositionQuery = "UPDATE Disposition_Info SET other_disqualification = ? WHERE receipt_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(dispositionQuery)) {
                stmt.setString(1, isScrap ? scrapReason : null);
                stmt.setInt(2, receiptId);
                stmt.executeUpdate();
            }
        } else {
            String dispositionQuery = "INSERT INTO Disposition_Info (receipt_id, other_disqualification) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(dispositionQuery)) {
                stmt.setInt(1, receiptId);
                stmt.setString(2, isScrap ? scrapReason : null);
                stmt.executeUpdate();
            }
        }
    }

    private boolean recordExistsByReceiptId(Connection conn, String tableName, int id) throws SQLException {
        String sql = "SELECT 1 FROM " + tableName + " WHERE receipt_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    @FXML
    private void handleFunctioningButton() {
        processMonitorIntake(true);
    }

    @FXML
    private void handleBrokenButton() {
        processMonitorIntake(false);
    }

    private void processMonitorIntake(boolean isFunctioning) {
        String serial = monitorSerialField.getText().trim();
        String model = monitorModelField.getText().trim();
        String description = monitorDescriptionField.getText().trim();
        String printer = monitorPrinterCombo.getValue();

        if (serial.isEmpty() || model.isEmpty() || description.isEmpty()) {
            showAlert("Input Required", "Serial, Model (SKU), and Description are required.");
            return;
        }
        if (isFunctioning && printer == null) {
            showAlert("Printer Required", "Please select a printer to print labels.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AssetInfo assetInfo = new AssetInfo(serial, "Dell", model, description, "Monitor", null, false, "");
                if (assetDAO.findAssetBySerialNumber(serial).isEmpty()) {
                    assetDAO.addAsset(assetInfo);
                }
                ReceiptEvent newReceipt = new ReceiptEvent(0, serial, currentPackage.getPackageId(), assetInfo.getCategory(), assetInfo.getMake(), assetInfo.getModelNumber(), assetInfo.getDescription(), null);
                int newReceiptId = receiptEventDAO.addReceiptEvent(newReceipt);

                if (newReceiptId == -1) throw new Exception("Failed to create receipt event for S/N: " + serial);

                try (Connection conn = DatabaseConnection.getInventoryConnection()) {
                    if (isFunctioning) {
                        createInitialStatus(conn, newReceiptId, false, false, "Processed", "Ready for Deployment", null);
                    } else {
                        createInitialStatus(conn, newReceiptId, false, true, "Disposal/EOL", "Can-Am, Pending Pickup", "Received Broken");
                    }
                }

                if (isFunctioning) {
                    updateMonitorFeedback("Printing labels for " + serial + "...");
                    String skuZpl = ZplPrinterService.getAdtLabelZpl(model, description);
                    String serialZpl = ZplPrinterService.getSerialLabelZpl(model, serial);
                    boolean s1 = printerService.sendZplToPrinter(printer, skuZpl);
                    boolean s2 = printerService.sendZplToPrinter(printer, serialZpl);
                    if (!s1 || !s2) throw new Exception("Failed to print one or both labels.");
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            String successMsg = isFunctioning ? " processed and labels printed." : " processed as broken.";
            updateMonitorFeedback("Success: " + serial + successMsg);
            clearMonitorFields();
            if (parentController != null) parentController.refreshData();
        });
        task.setOnFailed(e -> updateMonitorFeedback("Error: " + e.getSource().getException().getMessage()));

        new Thread(task).start();
    }

    private void updateMonitorFeedback(String message) {
        Platform.runLater(() -> monitorFeedbackLabel.setText(message));
    }

    private void clearMonitorFields() {
        monitorSerialField.clear();
        monitorModelField.clear();
        monitorDescriptionField.clear();
        monitorSerialField.requestFocus();
    }
}