package assettracking.controller;

import assettracking.dao.AssetDAO;
import assettracking.dao.SkuDAO;
import assettracking.dao.ReceiptEventDAO;
import assettracking.data.AssetEntry;
import assettracking.data.AssetInfo;
import assettracking.data.Package;
import assettracking.data.ReceiptEvent;
import assettracking.data.MelRule;
import assettracking.db.DatabaseConnection;
import assettracking.label.service.ZplPrinterService;
import assettracking.manager.StatusManager;
import assettracking.ui.AutoCompletePopup;
import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class AddAssetDialogController {

    // --- (All FXML fields are the same) ---
    @FXML private RadioButton standardIntakeRadio;
    @FXML private RadioButton monitorIntakeRadio;
    @FXML private ToggleGroup intakeModeToggleGroup;
    @FXML private VBox standardModePane;
    @FXML private VBox monitorModePane;
    @FXML private ComboBox<String> monitorPrinterCombo;
    @FXML private TextField monitorSerialField;
    @FXML private TextField monitorModelField;
    @FXML private TextField monitorDescriptionField;
    @FXML private TextField monitorSkuSearchField;
    @FXML private ListView<String> monitorSkuListView;
    @FXML private TextField monitorSelectedSkuField;
    @FXML private Label monitorFeedbackLabel;
    @FXML private CheckBox standardMonitorCheckBox;
    @FXML private GridPane standardMonitorPane;
    @FXML private GridPane manualMonitorPane;
    @FXML private TextField manualSerialField;
    @FXML private TextField manualDescriptionField;
    @FXML private Button functioningButton;
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
    @FXML private ToggleGroup conditionToggleGroup;
    @FXML private RadioButton refurbRadioButton;
    @FXML private RadioButton newRadioButton;
    @FXML private Label boxIdLabel;
    @FXML private TextField boxIdField;

    private Package currentPackage;
    private PackageDetailController parentController;
    private final ObservableList<AssetEntry> assetEntries = FXCollections.observableArrayList();
    private final AssetDAO assetDAO = new AssetDAO();
    private final ReceiptEventDAO receiptEventDAO = new ReceiptEventDAO();
    private final ZplPrinterService printerService = new ZplPrinterService();
    private final SkuDAO skuDAO = new SkuDAO();

    public void initData(Package pkg, PackageDetailController parent) {
        this.currentPackage = pkg;
        this.parentController = parent;
        loadCategories();
    }

    @FXML
    public void initialize() {
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

    private void setupMonitorIntake() {
        standardMonitorCheckBox.selectedProperty().addListener((obs, oldVal, isStandard) -> {
            standardMonitorPane.setVisible(isStandard);
            standardMonitorPane.setManaged(isStandard);
            manualMonitorPane.setVisible(!isStandard);
            manualMonitorPane.setManaged(!isStandard);
            functioningButton.setDisable(!isStandard);

            // Setup for the NEW dedicated SKU search
            setupMonitorSkuSearch();
        });

        new AutoCompletePopup(monitorDescriptionField, () -> assetDAO.findDescriptionsLike(monitorDescriptionField.getText()))
                .setOnSuggestionSelected(selectedValue ->
                        assetDAO.findSkuDetails(selectedValue, "description").ifPresent(sku -> Platform.runLater(() -> {
                            monitorDescriptionField.setText(sku.getDescription());
                            monitorModelField.setText(sku.getModelNumber());
                        }))
                );

        new AutoCompletePopup(monitorModelField, () -> assetDAO.findModelNumbersLike(monitorModelField.getText()))
                .setOnSuggestionSelected(selectedValue ->
                        assetDAO.findSkuDetails(selectedValue, "model_number").ifPresent(sku -> Platform.runLater(() -> {
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
        boxIdLabel.setDisable(true);
        boxIdField.setDisable(true);

        boxIdLabel.setVisible(false);
        boxIdLabel.setManaged(false);
        boxIdField.setVisible(false);
        boxIdField.setManaged(false);

        sellScrapStatusCombo.setItems(FXCollections.observableArrayList(StatusManager.getStatuses()));
        sellScrapStatusCombo.getSelectionModel().selectFirst();

        sellScrapStatusCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            sellScrapSubStatusCombo.getItems().clear();
            if (newVal != null) {
                sellScrapSubStatusCombo.getItems().addAll(StatusManager.getSubStatuses(newVal));
                if (!sellScrapSubStatusCombo.getItems().isEmpty()) {
                    sellScrapSubStatusCombo.getSelectionModel().selectFirst();
                }

                boolean isDisposed = "Disposed".equals(newVal);
                boxIdLabel.setVisible(isDisposed);
                boxIdLabel.setManaged(isDisposed);
                boxIdField.setVisible(isDisposed);
                boxIdField.setManaged(isDisposed);

                disqualificationLabel.setVisible(!isDisposed);
                disqualificationLabel.setManaged(!isDisposed);
                disqualificationField.setVisible(!isDisposed);
                disqualificationField.setManaged(!isDisposed);
            }
        });

        if (sellScrapStatusCombo.getValue() != null) {
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
            boxIdLabel.setDisable(!selected);
            boxIdField.setDisable(!selected);
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
            applyMelRule(sku.getModelNumber(), sku.getDescription());
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

            applyMelRule(asset.getModelNumber(), asset.getDescription());
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
        if (sellScrapCheckBox.isSelected() && "Disposed".equals(sellScrapStatusCombo.getValue())) {
            String subStatus = sellScrapSubStatusCombo.getValue();
            if (!"Ready for Wipe".equals(subStatus) && boxIdField.getText().trim().isEmpty()) {
                StageManager.showAlert(saveButton.getScene().getWindow(), Alert.AlertType.WARNING, "Box ID Required", "A Box ID is required for this disposed status. It is optional only for 'Ready for Wipe'.");
                return;
            }
        }

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

    /**
     * MODIFIED to use the default constructor and setters instead of the failing parameterized constructor.
     */
    private Task<String> createSaveTask() {
        final boolean isBulkMode = bulkAddCheckBox.isSelected();
        final String[] serials = multiSerialToggle.isSelected() ? serialArea.getText().trim().split("\\r?\\n") : new String[]{serialField.getText().trim()};
        final List<AssetEntry> entriesFromTable = new ArrayList<>(assetEntries);

        final boolean isScrap = sellScrapCheckBox.isSelected();
        final String scrapStatus = sellScrapStatusCombo.getValue();
        final String scrapSubStatus = sellScrapSubStatusCombo.getValue();
        final String scrapReason = disqualificationField.getText().trim();
        final String boxId = boxIdField.getText().trim();
        final boolean isNewCondition = newRadioButton.isSelected();

        // --- THIS IS THE CRITICAL FIX ---
        final AssetInfo singleEntryInfo;
        if (!isBulkMode) {
            AssetInfo info = new AssetInfo();
            info.setMake(makeField.getText());
            info.setModelNumber(modelField.getText());
            info.setDescription(descriptionField.getText());
            info.setCategory(categoryBox.getValue());
            info.setImei(imeiField.getText());
            singleEntryInfo = info;
        } else {
            singleEntryInfo = null;
        }
        // --- END OF FIX ---

        return new Task<>() {
            @Override
            protected String call() throws Exception {
                if (isBulkMode) {
                    return processTable(entriesFromTable, isScrap, scrapStatus, scrapSubStatus, scrapReason, boxId, isNewCondition);
                } else {
                    return processTextArea(serials, singleEntryInfo, isScrap, scrapStatus, scrapSubStatus, scrapReason, boxId, isNewCondition);
                }
            }
        };
    }

    private String processTextArea(String[] serialNumbers, AssetInfo singleEntryDetails, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId, boolean isNewCondition) throws SQLException {
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
                    createInitialStatus(conn, newReceiptId, isNewCondition, isScrap, scrapStatus, scrapSubStatus, scrapReason, boxId);
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

    private String processTable(List<AssetEntry> entries, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId, boolean isNewCondition) throws SQLException {
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
                AssetInfo assetInfo = new AssetInfo();
                assetInfo.setSerialNumber(serial);
                assetInfo.setMake(entry.getMake());
                assetInfo.setModelNumber(entry.getModelNumber());
                assetInfo.setDescription(entry.getDescription());
                assetInfo.setCategory(entry.getCategory());
                assetInfo.setImei(entry.getImei());

                if (!isReturn) {
                    assetDAO.addAsset(assetInfo);
                }

                ReceiptEvent newReceipt = new ReceiptEvent(0, serial, currentPackage.getPackageId(), assetInfo.getCategory(), assetInfo.getMake(), assetInfo.getModelNumber(), assetInfo.getDescription(), assetInfo.getImei());
                int newReceiptId = receiptEventDAO.addReceiptEvent(newReceipt);

                if (newReceiptId != -1) {
                    createInitialStatus(conn, newReceiptId, isNewCondition, isScrap, scrapStatus, scrapSubStatus, scrapReason, boxId);
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

    private void createInitialStatus(Connection conn, int receiptId, boolean isNewCondition, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId) throws SQLException {
        String finalStatus;
        String finalSubStatus;
        String finalReason = null;

        if (isScrap) {
            finalStatus = scrapStatus;
            finalSubStatus = scrapSubStatus;
            if ("Disposed".equals(scrapStatus) && boxId != null && !boxId.isEmpty()) {
                finalReason = "Box ID: " + boxId;
                if (scrapReason != null && !scrapReason.isEmpty()) {
                    finalReason += ". " + scrapReason;
                }
            } else {
                finalReason = scrapReason;
            }
        } else if (isNewCondition) {
            finalStatus = "Processed";
            finalSubStatus = "Ready for Deployment";
        } else {
            finalStatus = "Intake";
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
                stmt.setString(1, finalReason);
                stmt.setInt(2, receiptId);
                stmt.executeUpdate();
            }
        } else {
            String dispositionQuery = "INSERT INTO Disposition_Info (receipt_id, other_disqualification) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(dispositionQuery)) {
                stmt.setInt(1, receiptId);
                stmt.setString(2, finalReason);
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
        StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, title, content);
    }

    @FXML
    private void handleFunctioningButton() {
        processFunctioningMonitor();
    }

    @FXML
    private void handleBrokenButton() {
        String serial;
        String model;
        String description;

        if (standardMonitorCheckBox.isSelected()) {
            serial = monitorSerialField.getText().trim();
            model = monitorModelField.getText().trim();
            description = monitorDescriptionField.getText().trim();
            if (serial.isEmpty() || model.isEmpty() || description.isEmpty()) {
                showAlert("Input Required", "Serial, Model, and Description are required for standard monitors.");
                return;
            }
        } else {
            serial = manualSerialField.getText().trim();
            description = manualDescriptionField.getText().trim();
            model = "NON-STANDARD";
            if (serial.isEmpty() || description.isEmpty()) {
                showAlert("Input Required", "Serial and Manual Description are required for non-standard monitors.");
                return;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MonitorDisposalDialog.fxml"));
            Parent root = loader.load();

            MonitorDisposalDialogController dialogController = loader.getController();
            dialogController.initData(serial);

            Stage dialogStage = StageManager.createCustomStage(getOwnerWindow(), "Set Disposal Status", root);
            dialogStage.showAndWait();

            dialogController.getResult().ifPresent(result -> {
                processBrokenMonitor(serial, model, description, result.status(), result.subStatus(), result.reason());
            });

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "Could not open the disposal dialog window.");
        }
    }

    /**
     * COMPLETELY REWRITTEN to use the new, explicit SKU selection workflow.
     */
    private void processFunctioningMonitor() {
        String serial = monitorSerialField.getText().trim();
        String model = monitorModelField.getText().trim();
        String description = monitorDescriptionField.getText().trim();
        String printer = monitorPrinterCombo.getValue();

        // Get the SKU directly from the new, dedicated field
        String officialSku = monitorSelectedSkuField.getText().trim();

        // --- NEW VALIDATION ---
        if (serial.isEmpty() || model.isEmpty()) {
            showAlert("Input Required", "Serial Number and Model are required.");
            return;
        }
        if (officialSku.isEmpty()) {
            showAlert("SKU Required", "You must search for and select a Label SKU to print.");
            return;
        }
        if (printer == null) {
            showAlert("Printer Required", "Please select a printer.");
            return;
        }
        // --- END VALIDATION ---

        // Get the full "kit" description for the selected SKU for the label.
        // Fall back to the component description if a master record isn't found.
        String finalLabelDescription = assetDAO.findDescriptionBySkuNumber(officialSku)
                .orElse(description);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AssetInfo assetInfo = new AssetInfo();
                assetInfo.setSerialNumber(serial);
                assetInfo.setMake("Dell");
                assetInfo.setModelNumber(model);
                assetInfo.setDescription(description);
                assetInfo.setCategory("Monitor");

                if (assetDAO.findAssetBySerialNumber(serial).isEmpty()) {
                    assetDAO.addAsset(assetInfo);
                }
                ReceiptEvent newReceipt = new ReceiptEvent(0, serial, currentPackage.getPackageId(), assetInfo.getCategory(), assetInfo.getMake(), assetInfo.getModelNumber(), assetInfo.getDescription(), null);
                int newReceiptId = receiptEventDAO.addReceiptEvent(newReceipt);

                if (newReceiptId == -1) throw new Exception("Failed to create receipt event for S/N: " + serial);

                try (Connection conn = DatabaseConnection.getInventoryConnection()) {
                    createInitialStatus(conn, newReceiptId, false, false, "Processed", "Ready for Deployment", null, null);
                }

                updateMonitorFeedback("Printing labels for " + serial + " with SKU: " + officialSku + "...");

                String skuZpl = ZplPrinterService.getAdtLabelZpl(officialSku, finalLabelDescription);
                String serialZpl = ZplPrinterService.getSerialLabelZpl(officialSku, serial);

                boolean s1 = printerService.sendZplToPrinter(printer, skuZpl);
                boolean s2 = printerService.sendZplToPrinter(printer, serialZpl);
                if (!s1 || !s2) throw new Exception("Failed to print one or both labels.");
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            updateMonitorFeedback("Success: " + serial + " processed and labels printed.");
            clearMonitorFields();
            if (parentController != null) parentController.refreshData();
        });
        task.setOnFailed(e -> updateMonitorFeedback("Error: " + e.getSource().getException().getMessage()));

        new Thread(task).start();
    }

    /**
     * NEW METHOD: Sets up the search functionality for the dedicated Monitor Label SKU field.
     */
    private void setupMonitorSkuSearch() {
        // Listener to update the suggestion list as the user types
        monitorSkuSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                monitorSkuListView.getItems().clear();
                return;
            }
            // Use the SkuDAO to find relevant SKUs
            List<String> suggestions = skuDAO.findSkusLike(newVal);
            monitorSkuListView.setItems(FXCollections.observableArrayList(suggestions));
        });

        // Listener for when a user clicks a suggestion in the list
        monitorSkuListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // The suggestion is formatted as "SKU - Description", so we split it.
                String selectedSku = newSelection.split(" - ")[0];
                Platform.runLater(() -> {
                    monitorSelectedSkuField.setText(selectedSku); // Set the selected SKU in the read-only field
                    monitorSkuSearchField.clear(); // Clear the search box
                    monitorSkuListView.getItems().clear(); // Hide the list
                });
            }
        });
    }


    private void processBrokenMonitor(String serial, String model, String description, String status, String subStatus, String reason) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AssetInfo assetInfo = new AssetInfo();
                assetInfo.setSerialNumber(serial);
                assetInfo.setMake("Dell");
                assetInfo.setModelNumber(model);
                assetInfo.setDescription(description);
                assetInfo.setCategory("Monitor");

                if (assetDAO.findAssetBySerialNumber(serial).isEmpty()) {
                    assetDAO.addAsset(assetInfo);
                }
                ReceiptEvent newReceipt = new ReceiptEvent(0, serial, currentPackage.getPackageId(), assetInfo.getCategory(), assetInfo.getMake(), assetInfo.getModelNumber(), assetInfo.getDescription(), null);
                int newReceiptId = receiptEventDAO.addReceiptEvent(newReceipt);

                if (newReceiptId == -1) throw new Exception("Failed to create receipt event for S/N: " + serial);

                try (Connection conn = DatabaseConnection.getInventoryConnection()) {
                    createInitialStatus(conn, newReceiptId, false, true, status, subStatus, reason, null);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            updateMonitorFeedback("Success: " + serial + " processed with status '" + status + "'.");
            clearMonitorFields();
            if (parentController != null) parentController.refreshData();
        });
        task.setOnFailed(e -> updateMonitorFeedback("Error: " + e.getSource().getException().getMessage()));

        new Thread(task).start();
    }

    private void updateMonitorFeedback(String message) {
        Platform.runLater(() -> monitorFeedbackLabel.setText(message));
    }

    /**
     * MODIFIED to clear the new SKU fields as well.
     */
    private void clearMonitorFields() {
        monitorSerialField.clear();
        monitorModelField.clear();
        monitorDescriptionField.clear();
        monitorSkuSearchField.clear();
        monitorSelectedSkuField.clear();
        monitorSkuListView.getItems().clear();
        monitorSerialField.requestFocus();
    }

    private void applyMelRule(String modelNumber, String description) {
        Optional<MelRule> ruleOpt = assetDAO.findMelRule(modelNumber, description);

        sellScrapCheckBox.setSelected(false);
        disqualificationField.clear();
        melActionLabel.setText("");

        ruleOpt.ifPresent(rule -> {
            melActionLabel.setText("MEL Action: " + rule.action());
            if ("Dispose".equalsIgnoreCase(rule.action())) {
                sellScrapCheckBox.setSelected(true);
                sellScrapStatusCombo.setValue("Disposed");
                sellScrapSubStatusCombo.setValue("Ready for Wipe");
                disqualificationField.clear();
                boxIdField.requestFocus();
            }
        });
    }

    private Stage getOwnerWindow() {
        return (Stage) standardModePane.getScene().getWindow();
    }
}