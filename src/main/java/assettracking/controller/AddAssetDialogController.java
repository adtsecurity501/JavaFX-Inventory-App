package assettracking.controller;

import assettracking.controller.handler.MonitorIntakeHandler;
import assettracking.controller.handler.StandardIntakeHandler;
import assettracking.dao.AssetDAO;
import assettracking.dao.ReceiptEventDAO;
import assettracking.dao.SkuDAO;
import assettracking.data.AssetEntry;
import assettracking.data.AssetInfo;
import assettracking.data.Package;
import assettracking.db.DatabaseConnection;
import assettracking.label.service.ZplPrinterService;
import assettracking.manager.StageManager;
import assettracking.manager.StatusManager;
import assettracking.ui.AutoCompletePopup;
import assettracking.ui.AutoCompleteTableCell;
import assettracking.ui.SerialLookupTableCell;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class AddAssetDialogController {

    private static final Set<String> cachedCategories = null;
    private final ObservableList<AssetEntry> assetEntries = FXCollections.observableArrayList();
    private final AssetDAO assetDAO = new AssetDAO();
    private final SkuDAO skuDAO = new SkuDAO();
    private final ReceiptEventDAO receiptEventDAO = new ReceiptEventDAO();
    private final ZplPrinterService printerService = new ZplPrinterService();
    public ToggleGroup conditionToggleGroup;
    @FXML
    public TextField serialField;
    @FXML
    private Label dispositionWarningLabel;
    // --- FXML Fields ---
    @FXML
    private RadioButton standardIntakeRadio, monitorIntakeRadio, refurbRadioButton, newRadioButton;
    @FXML
    private ToggleGroup intakeModeToggleGroup;
    @FXML
    private VBox standardModePane, monitorModePane;
    @FXML
    private ComboBox<String> monitorPrinterCombo, sellScrapStatusCombo, sellScrapSubStatusCombo;
    @FXML
    private TextField monitorSerialField, monitorModelField, monitorDescriptionField, monitorSkuSearchField, monitorSelectedSkuField;
    @FXML
    private ListView<String> monitorSkuListView;
    @FXML
    private Label monitorFeedbackLabel, probableCauseLabel, melActionLabel, feedbackLabel;
    @FXML
    private CheckBox standardMonitorCheckBox, bulkAddCheckBox, sellScrapCheckBox;
    @FXML
    private GridPane standardMonitorPane, manualMonitorPane;
    @FXML
    private TextField manualSerialField, manualDescriptionField, makeField, modelField, descriptionField, imeiField, categoryField;
    @FXML
    private TextField disqualificationField, boxIdField;
    @FXML
    private Button functioningButton, saveButton, closeButton;
    @FXML
    private BorderPane textModePane, tableModePane;
    @FXML
    private ToggleButton multiSerialToggle;
    @FXML
    private TextArea serialArea;
    @FXML
    private TableView<AssetEntry> deviceTable;
    @FXML
    private TableColumn<AssetEntry, String> serialCol, imeiCol, categoryCol, makeCol, modelCol, descriptionCol, causeCol;
    @FXML
    private Label sellScrapStatusLabel, sellScrapSubStatusLabel, disqualificationLabel, boxIdLabel;
    private AutoCompletePopup descriptionPopup;
    private AutoCompletePopup modelPopup;
    private AutoCompletePopup monitorDescriptionPopup;
    private AutoCompletePopup monitorModelPopup;
    private Package currentPackage;
    private PackageDetailController parentController;
    private StandardIntakeHandler standardIntakeHandler;
    private MonitorIntakeHandler monitorIntakeHandler;
    private boolean isEditMode = false;
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        this.standardIntakeHandler = new StandardIntakeHandler(this);
        this.monitorIntakeHandler = new MonitorIntakeHandler(this);
        setupViewToggles();
        setupDispositionControls();
        setupTable();
        setupAutocomplete();
        setupMonitorIntake();
        setupInputSanitization(); // <-- THIS METHOD IS NOW FIXED
        refurbRadioButton.setSelected(true);
        standardIntakeRadio.setSelected(true);
    }

    // --- THIS IS THE FIX ---
    // The previous implementation of this method incorrectly handled the cursor
    // position, causing it to jump when letters were typed. This corrected version
    // sanitizes the input and reliably moves the cursor to the end of the text,
    // which is the expected behavior when typing.
    private void setupInputSanitization() {
        // Listener for the main serial field
        serialField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String sanitized = sanitizeSerialNumber(newVal);
                if (!newVal.equals(sanitized)) {
                    Platform.runLater(() -> {
                        serialField.setText(sanitized);
                        serialField.end(); // Reliably move caret to the end
                    });
                }
            }
        });

        // Listener for the monitor serial field
        monitorSerialField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String sanitized = sanitizeSerialNumber(newVal);
                if (!newVal.equals(sanitized)) {
                    Platform.runLater(() -> {
                        monitorSerialField.setText(sanitized);
                        monitorSerialField.end(); // Reliably move caret to the end
                    });
                }
            }
        });

        // Listener for the manual serial field
        manualSerialField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String sanitized = sanitizeSerialNumber(newVal);
                if (!newVal.equals(sanitized)) {
                    Platform.runLater(() -> {
                        manualSerialField.setText(sanitized);
                        manualSerialField.end(); // Reliably move caret to the end
                    });
                }
            }
        });
    }

    // This helper method is unchanged but is essential for the fix.
    private String sanitizeSerialNumber(String input) {
        if (input == null) return "";
        // Removes anything that is NOT a letter or a number, then converts to uppercase.
        return input.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }
    // --- END OF FIX ---


    public String[] getSerialsFromArea() {
        return Arrays.stream(serialArea.getText().trim().split("\\r?\\n")).map(this::sanitizeSerialNumber) // Sanitize each line
                .filter(s -> !s.isEmpty())       // Remove any lines that are now blank
                .toArray(String[]::new);
    }

    private void setupAutocomplete() {
        // Autocomplete for standard text fields
        new AutoCompletePopup(makeField, () -> assetDAO.findDistinctValuesLike("make", makeField.getText()));
        new AutoCompletePopup(categoryField, () -> assetDAO.getAllDistinctCategories().stream().filter(s -> s.toLowerCase().contains(categoryField.getText().toLowerCase())).collect(Collectors.toList()));
        descriptionPopup = new AutoCompletePopup(descriptionField, () -> assetDAO.findDescriptionsLike(descriptionField.getText())).setOnSuggestionSelected(selectedValue -> assetDAO.findSkuDetails(selectedValue, "description").ifPresent(this::populateFieldsFromSku));
        modelPopup = new AutoCompletePopup(modelField, () -> assetDAO.findModelNumbersLike(modelField.getText())).setOnSuggestionSelected(selectedValue -> assetDAO.findSkuDetails(selectedValue, "model_number").ifPresent(this::populateFieldsFromSku));
    }

    /**
     * Sets the text and visibility of the new warning label in the disposition pane.
     */
    public void setDispositionWarning(String text) {
        if (text == null || text.isEmpty()) {
            dispositionWarningLabel.setVisible(false);
            dispositionWarningLabel.setManaged(false);
        } else {
            dispositionWarningLabel.setText(text);
            dispositionWarningLabel.setVisible(true);
            dispositionWarningLabel.setManaged(true);
        }
    }


    public void initDataForEdit(AssetInfo assetInfo, Runnable onSaveCallback) {
        this.isEditMode = true;
        this.onSaveCallback = onSaveCallback;

        setupAutocomplete();

        setFormAssetDetails(assetInfo);
        serialField.setText(assetInfo.getSerialNumber());

        standardIntakeRadio.setSelected(true);
        saveButton.setText("Save Changes");

        serialField.setEditable(false);
        multiSerialToggle.setDisable(true);
        bulkAddCheckBox.setDisable(true);
        standardIntakeRadio.setDisable(true);
        monitorIntakeRadio.setDisable(true);

        Node dispositionPane = sellScrapCheckBox.getParent().getParent().getParent();
        if (dispositionPane != null) {
            dispositionPane.setVisible(false);
            dispositionPane.setManaged(false);
        }
    }

    private void populateFieldsFromSku(AssetInfo sku) {
        Platform.runLater(() -> {
            descriptionPopup.suppressListener(true);
            modelPopup.suppressListener(true);

            descriptionField.setText(sku.getDescription());
            modelField.setText(sku.getModelNumber());
            makeField.setText(sku.getMake());
            if (sku.getCategory() != null && !sku.getCategory().isEmpty()) {
                categoryField.setText(sku.getCategory());
            }
            standardIntakeHandler.applyMelRule(sku.getModelNumber(), sku.getDescription());

            descriptionPopup.suppressListener(false);
            modelPopup.suppressListener(false);
        });
    }

    private void setupMonitorIntake() {
        setupMonitorSkuSearch();
        standardMonitorCheckBox.selectedProperty().addListener((obs, oldVal, isStandard) -> {
            standardMonitorPane.setVisible(isStandard);
            standardMonitorPane.setManaged(isStandard);
            manualMonitorPane.setVisible(!isStandard);
            manualMonitorPane.setManaged(!isStandard);
            functioningButton.setDisable(!isStandard);
        });

        monitorDescriptionPopup = new AutoCompletePopup(monitorDescriptionField, () -> assetDAO.findDescriptionsLike(monitorDescriptionField.getText())).setOnSuggestionSelected(selectedValue -> assetDAO.findSkuDetails(selectedValue, "description").ifPresent(this::populateMonitorFieldsFromSku));

        monitorModelPopup = new AutoCompletePopup(monitorModelField, () -> assetDAO.findModelNumbersLike(monitorModelField.getText())).setOnSuggestionSelected(selectedValue -> assetDAO.findSkuDetails(selectedValue, "model_number").ifPresent(this::populateMonitorFieldsFromSku));

        List<String> printerNames = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null)).map(PrintService::getName).collect(Collectors.toList());
        monitorPrinterCombo.setItems(FXCollections.observableArrayList(printerNames));
        printerNames.stream().filter(n -> n.toLowerCase().contains("gx")).findFirst().ifPresent(monitorPrinterCombo::setValue);
    }

    private void populateMonitorFieldsFromSku(AssetInfo sku) {
        Platform.runLater(() -> {
            monitorDescriptionPopup.suppressListener(true);
            monitorModelPopup.suppressListener(true);

            monitorDescriptionField.setText(sku.getDescription());
            monitorModelField.setText(sku.getModelNumber());

            monitorDescriptionPopup.suppressListener(false);
            monitorModelPopup.suppressListener(false);
        });
    }

    public String getMonitorSerial() {
        return monitorSerialField.getText().trim();
    }

    public String getMonitorModel() {
        return monitorModelField.getText().trim();
    }

    public String getMonitorDescription() {
        return monitorDescriptionField.getText().trim();
    }

    public String getMonitorPrinter() {
        return monitorPrinterCombo.getValue();
    }

    public String getMonitorSelectedSku() {
        return monitorSelectedSkuField.getText().trim();
    }

    public boolean isStandardMonitor() {
        return standardMonitorCheckBox.isSelected();
    }

    public String getManualSerial() {
        return manualSerialField.getText().trim();
    }

    public String getManualDescription() {
        return manualDescriptionField.getText().trim();
    }

    public String getSerial() {
        return serialField.getText().trim();
    }

    public boolean isMultiSerialMode() {
        return multiSerialToggle.isSelected();
    }

    public boolean isBulkAddMode() {
        return bulkAddCheckBox.isSelected();
    }

    public boolean isNewCondition() {
        return newRadioButton.isSelected();
    }

    public boolean isSellScrap() {
        return sellScrapCheckBox.isSelected();
    }

    public String getScrapStatus() {
        return sellScrapStatusCombo.getValue();
    }

    public String getScrapSubStatus() {
        return sellScrapSubStatusCombo.getValue();
    }

    public String getScrapReason() {
        return disqualificationField.getText().trim();
    }

    public String getBoxId() {
        return boxIdField.getText().trim();
    }

    public AssetInfo getAssetDetailsFromForm() {
        AssetInfo details = new AssetInfo();
        details.setMake(makeField.getText());
        details.setModelNumber(modelField.getText());
        details.setDescription(descriptionField.getText());
        details.setCategory(categoryField.getText());
        details.setImei(imeiField.getText());
        return details;
    }

    public void updateMonitorFeedback(String message) {
        Platform.runLater(() -> monitorFeedbackLabel.setText(message));
    }

    public void clearMonitorFieldsAndFocus() {
        monitorSerialField.clear();
        monitorModelField.clear();
        monitorDescriptionField.clear();
        monitorSkuSearchField.clear();
        monitorSelectedSkuField.clear();
        monitorSkuListView.getItems().clear();
        monitorSerialField.requestFocus();
    }

    public void updateStandardIntakeFeedback(String message) {
        feedbackLabel.setText(message);
    }

    public void setProbableCause(String text) {
        probableCauseLabel.setText(text);
    }

    public void setMelAction(String text) {
        melActionLabel.setText(text);
    }

    public void setDispositionFieldsForDispose() {
        sellScrapCheckBox.setSelected(true);
        sellScrapStatusCombo.setValue("Disposed");
        sellScrapSubStatusCombo.setValue("Ready for Wipe");
        disqualificationField.clear();
        boxIdField.requestFocus();
    }

    public void setFlaggedDeviceFields() {
        probableCauseLabel.setText("Flagged Device");
        sellScrapCheckBox.setSelected(true);
        sellScrapStatusCombo.setValue("Flag!");
        sellScrapSubStatusCombo.setValue("Requires Review");
        sellScrapStatusCombo.setDisable(true);
        sellScrapSubStatusCombo.setDisable(true);
    }

    public void setFormAssetDetails(AssetInfo asset) {
        Platform.runLater(() -> {
            imeiField.setText(asset.getImei());
            categoryField.setText(asset.getCategory());
            makeField.setText(asset.getMake());
            modelField.setText(asset.getModelNumber());
            descriptionField.setText(asset.getDescription());
        });
    }

    public void disableSaveButton(boolean disable) {
        saveButton.setDisable(disable);
        closeButton.setDisable(disable);
    }

    public void initData(Package pkg, PackageDetailController parent) {
        this.currentPackage = pkg;
        this.parentController = parent;
    }

    public void initDataForBulkAdd(Package pkg, List<AssetEntry> entries) {
        this.currentPackage = pkg;
        bulkAddCheckBox.setSelected(true);

        Task<List<AssetEntry>> lookupTask = new Task<>() {
            @Override
            protected List<AssetEntry> call() throws Exception {
                List<AssetEntry> populatedEntries = new ArrayList<>();
                // Open ONE connection for the entire loop
                try (Connection conn = getDbConnection()) {
                    for (AssetEntry entry : entries) {
                        String serial = entry.getSerialNumber();
                        if (serial == null || serial.isEmpty()) continue;

                        // Use the new, efficient DAO method that reuses the connection
                        Optional<AssetInfo> assetOpt = assetDAO.findAssetBySerialNumber(conn, serial);

                        if (assetOpt.isPresent()) {
                            AssetInfo assetInfo = assetOpt.get();
                            populatedEntries.add(new AssetEntry(serial, assetInfo.getImei(), assetInfo.getCategory(), assetInfo.getMake(), assetInfo.getModelNumber(), assetInfo.getDescription(), ""));
                        } else {
                            populatedEntries.add(entry);
                        }
                    }
                }
                return populatedEntries;
            }
        };

        lookupTask.setOnSucceeded(e -> assetEntries.setAll(lookupTask.getValue()));

        lookupTask.setOnFailed(e -> StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Lookup Failed", "A database error occurred while looking up serial numbers."));

        new Thread(lookupTask).start();
    }


    @FXML
    private void handleLookupSerial() {
        // ADD THIS LINE at the beginning to clear the warning on each new lookup
        setDispositionWarning(null);
        standardIntakeHandler.handleLookupSerial();
    }

    @FXML
    private void handleSave() {
        if (isEditMode) {
            AssetInfo details = getAssetDetailsFromForm();
            details.setSerialNumber(serialField.getText());

            Task<Boolean> updateTask = new Task<>() {
                @Override
                protected Boolean call() {
                    return assetDAO.updateAsset(details);
                }
            };
            updateTask.setOnSucceeded(e -> {
                if (updateTask.getValue()) {
                    if (onSaveCallback != null) {
                        onSaveCallback.run();
                    }
                    handleClose();
                } else {
                    StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Update Failed", "Could not save changes to the database.");
                }
            });
            new Thread(updateTask).start();

        } else {
            standardIntakeHandler.handleSave();
        }
    }

    @FXML
    private void handleFunctioningButton() {
        monitorIntakeHandler.handleFunctioningButton();
    }

    @FXML
    private void handleBrokenButton() {
        monitorIntakeHandler.handleBrokenButton();
    }

    @FXML
    private void handleAddRow() {
        assetEntries.add(new AssetEntry("", "", "", "", "", "", ""));
    }

    @FXML
    private void handleRemoveRow() {
        assetEntries.remove(deviceTable.getSelectionModel().getSelectedItem());
    }

    @FXML
    public void handleClose() {
        ((Stage) closeButton.getScene().getWindow()).close();
    }

    public AssetDAO getAssetDAO() {
        return assetDAO;
    }

    public SkuDAO getSkuDAO() {
        return skuDAO;
    }

    public ReceiptEventDAO getReceiptEventDAO() {
        return receiptEventDAO;
    }

    public ZplPrinterService getPrinterService() {
        return printerService;
    }

    public Connection getDbConnection() throws SQLException {
        return DatabaseConnection.getInventoryConnection();
    }

    public Stage getOwnerWindow() {
        return (Stage) standardModePane.getScene().getWindow();
    }

    public Package getCurrentPackage() {
        return currentPackage;
    }

    public PackageDetailController getParentController() {
        return parentController;
    }

    public ObservableList<AssetEntry> getAssetEntries() {
        return assetEntries;
    }

    private void setupMonitorSkuSearch() {
        monitorSkuSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                monitorSkuListView.getItems().clear();
                return;
            }
            List<String> suggestions = skuDAO.findSkusLike(newVal);
            monitorSkuListView.setItems(FXCollections.observableArrayList(suggestions));
        });
        monitorSkuListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String selectedSku = newSelection.contains(" - ") ? newSelection.split(" - ")[0] : "N/A";
                Platform.runLater(() -> {
                    monitorSelectedSkuField.setText(selectedSku);
                    monitorSkuSearchField.clear();
                    monitorSkuListView.getItems().clear();
                });
            }
        });
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
            textModePane.setVisible(!newVal);
            textModePane.setManaged(!newVal);
            tableModePane.setVisible(newVal);
            tableModePane.setManaged(newVal);
        });

        // --- MODIFIED LISTENER ---
        multiSerialToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            serialField.setVisible(!newVal);
            serialField.setManaged(!newVal);
            serialArea.setVisible(newVal);
            serialArea.setManaged(newVal);

            // This is the crucial check. 'oldVal' is null only on the very first
            // time the listener runs during initialization. We want to skip the
            // focus request at that moment and only run it on subsequent user clicks.
            if (oldVal != null) {
                if (newVal) {
                    serialArea.requestFocus();
                } else {
                    serialField.requestFocus();
                }
            }
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
        sellScrapStatusCombo.getSelectionModel().select(0);
        sellScrapStatusCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            sellScrapSubStatusCombo.getItems().clear();
            if (newVal != null) {
                sellScrapSubStatusCombo.getItems().addAll(StatusManager.getSubStatuses(newVal));
                if (!sellScrapSubStatusCombo.getItems().isEmpty()) {
                    sellScrapSubStatusCombo.getSelectionModel().select(0);
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
            sellScrapSubStatusCombo.getSelectionModel().select(0);
        }
        sellScrapCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            sellScrapStatusLabel.setDisable(!newVal);
            sellScrapStatusCombo.setDisable(!newVal);
            sellScrapSubStatusLabel.setDisable(!newVal);
            sellScrapSubStatusCombo.setDisable(!newVal);
            disqualificationLabel.setDisable(!newVal);
            disqualificationField.setDisable(!newVal);
            boxIdLabel.setDisable(!newVal);
            boxIdField.setDisable(!newVal);
        });
    }

    private void setupTable() {
        // Serial Number Column with Autofill on Enter
        serialCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        serialCol.setCellFactory(col -> new SerialLookupTableCell(assetDAO));
        serialCol.setOnEditCommit(event -> event.getRowValue().setSerialNumber(event.getNewValue()));

        // IMEI Column (standard text editing)
        imeiCol.setCellValueFactory(new PropertyValueFactory<>("imei"));
        imeiCol.setCellFactory(TextFieldTableCell.forTableColumn());
        imeiCol.setOnEditCommit(event -> event.getRowValue().setImei(event.getNewValue()));

        // Category and Make columns (simple autocomplete, no cross-cell updates)
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setCellFactory(col -> new AutoCompleteTableCell<>(fragment -> skuDAO.findDistinctValuesLike("category", fragment)));
        categoryCol.setOnEditCommit(event -> event.getRowValue().setCategory(event.getNewValue()));

        makeCol.setCellValueFactory(new PropertyValueFactory<>("make"));
        makeCol.setCellFactory(col -> new AutoCompleteTableCell<>(fragment -> skuDAO.findDistinctValuesLike("manufac", fragment)));
        makeCol.setOnEditCommit(event -> event.getRowValue().setMake(event.getNewValue()));

        // --- THIS SECTION IS NOW UPDATED WITH THE NEW CALLBACK LOGIC ---

        // Model Number Column with Autocomplete and cross-cell update
        modelCol.setCellValueFactory(new PropertyValueFactory<>("modelNumber"));
        modelCol.setCellFactory(col -> new AutoCompleteTableCell<>(assetDAO::findModelNumbersLike, // Suggestion provider
                (assetEntry, selectedModel) -> { // Callback logic
                    assetDAO.findSkuDetails(selectedModel, "model_number").ifPresent(skuDetails -> {
                        // Update the data object for the row
                        assetEntry.setMake(skuDetails.getMake());
                        assetEntry.setCategory(skuDetails.getCategory());
                        assetEntry.setDescription(skuDetails.getDescription());
                        // Refresh the table to show changes in other cells
                        deviceTable.refresh();
                    });
                }));
        modelCol.setOnEditCommit(event -> event.getRowValue().setModelNumber(event.getNewValue()));

        // Description Column with Autocomplete and cross-cell update
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionCol.setCellFactory(col -> new AutoCompleteTableCell<>(assetDAO::findDescriptionsLike, // Suggestion provider
                (assetEntry, selectedDescription) -> { // Callback logic
                    assetDAO.findSkuDetails(selectedDescription, "description").ifPresent(skuDetails -> {
                        assetEntry.setMake(skuDetails.getMake());
                        assetEntry.setCategory(skuDetails.getCategory());
                        assetEntry.setModelNumber(skuDetails.getModelNumber());
                        deviceTable.refresh();
                    });
                }));
        descriptionCol.setOnEditCommit(event -> event.getRowValue().setDescription(event.getNewValue()));

        causeCol.setCellValueFactory(new PropertyValueFactory<>("probableCause"));

        deviceTable.setItems(assetEntries);
        deviceTable.setEditable(true);
        deviceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }
}