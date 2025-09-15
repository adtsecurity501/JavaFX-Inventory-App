package assettracking.controller;

import assettracking.dao.AssetDAO;
import assettracking.dao.DeviceStatusDAO;
import assettracking.data.AssetInfo;
import assettracking.data.DeviceStatusView;
import assettracking.db.DatabaseConnection;
import assettracking.manager.DeviceStatusManager;
import assettracking.manager.StageManager;
import assettracking.manager.StatusManager;
import assettracking.ui.DeviceStatusActions;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DeviceStatusTrackingController {

    private static List<String> cachedCategories = null;
    private final AssetDAO assetDAO = new AssetDAO();
    @FXML
    public Pagination pagination;
    @FXML
    public TextField serialSearchField;
    @FXML
    public TableView<DeviceStatusView> statusTable;
    @FXML
    public TableColumn<DeviceStatusView, String> serialNumberCol;
    @FXML
    public TableColumn<DeviceStatusView, String> categoryCol;
    @FXML
    public TableColumn<DeviceStatusView, String> makeCol;
    @FXML
    public TableColumn<DeviceStatusView, String> descriptionCol;
    @FXML
    public TableColumn<DeviceStatusView, String> statusCol;
    @FXML
    public TableColumn<DeviceStatusView, String> subStatusCol;
    @FXML
    public TableColumn<DeviceStatusView, String> lastUpdateCol;
    @FXML
    public TableColumn<DeviceStatusView, String> notesCol;
    @FXML
    public VBox updateDetailsPanel;
    @FXML
    public TextField serialDisplayField;
    @FXML
    public ComboBox<String> statusUpdateCombo;
    @FXML
    public ComboBox<String> subStatusUpdateCombo;
    @FXML
    public ComboBox<String> statusFilterCombo;
    @FXML
    public ComboBox<String> subStatusFilterCombo;
    @FXML
    public ComboBox<String> categoryFilterCombo;
    @FXML
    public ComboBox<String> groupByCombo;
    @FXML
    public DatePicker fromDateFilter;
    @FXML
    public DatePicker toDateFilter;
    @FXML
    public ComboBox<Integer> rowsPerPageCombo;
    @FXML
    public Label flagReasonLabel;
    @FXML
    private Label boxIdLabel;
    @FXML
    private TextField boxIdField;
    private DeviceStatusManager deviceStatusManager;
    private DeviceStatusActions deviceStatusActions;
    private DeviceStatusDAO deviceStatusDAO;


    @FXML
    public void initialize() {
        this.deviceStatusManager = new DeviceStatusManager(this);
        this.deviceStatusDAO = new DeviceStatusDAO(this.deviceStatusManager, this.deviceStatusManager.getDeviceStatusList());
        this.deviceStatusActions = new DeviceStatusActions(this);
        configureAllUI();
        deviceStatusManager.resetPagination();
    }

    private void handleEditDevice() {
        DeviceStatusView selectedDevice = statusTable.getSelectionModel().getSelectedItem();
        if (selectedDevice == null) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "No Selection", "Please select a device to edit.");
            return;
        }

        Optional<AssetInfo> assetOpt = assetDAO.findAssetBySerialNumber(selectedDevice.getSerialNumber());
        AssetInfo assetToEdit = assetOpt.orElseGet(() -> {
            System.out.println("Warning: No Physical_Assets record found for " + selectedDevice.getSerialNumber() + ". Creating edit object from view data.");
            AssetInfo infoFromView = new AssetInfo();
            infoFromView.setSerialNumber(selectedDevice.getSerialNumber());
            infoFromView.setCategory(selectedDevice.getCategory());
            infoFromView.setMake(selectedDevice.getMake());
            infoFromView.setDescription(selectedDevice.getDescription());
            return infoFromView;
        });

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/AddAssetDialog.fxml"));
            Parent root = loader.load();
            AddAssetDialogController controller = loader.getController();
            controller.initDataForEdit(assetToEdit, this::refreshData);

            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Edit Asset: " + selectedDevice.getSerialNumber(), root);
            stage.showAndWait();

        } catch (IOException e) {
            System.err.println("Service error: " + e.getMessage());
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Could not open the edit window.");
        }
    }

    @FXML
    private void handleBulkUpdateFromList() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/BulkUpdateDialog.fxml"));
            Parent root = loader.load();

            BulkUpdateDialogController dialogController = loader.getController();
            dialogController.initData(this.deviceStatusDAO, this::refreshData);

            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Bulk Status Update from List", root);
            stage.showAndWait();

        } catch (IOException e) {
            System.err.println("Service error: " + e.getMessage());
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Could not open the Bulk Update window.");
        }
    }

    private void configureAllUI() {
        setupTableColumns();
        setupStatusMappings();
        setupTableSelectionListener();
        configureTableRowFactory();
        setupFilters();
    }

    private void setupStatusMappings() {
        statusFilterCombo.getItems().add("All Statuses");
        statusFilterCombo.getItems().addAll(StatusManager.getStatuses());
        statusFilterCombo.getSelectionModel().selectFirst();

        subStatusFilterCombo.getItems().add("All Sub-Statuses");
        subStatusFilterCombo.getSelectionModel().selectFirst();

        statusFilterCombo.valueProperty().addListener((obs, oldStatus, newStatus) -> {
            subStatusFilterCombo.getItems().clear();
            subStatusFilterCombo.getItems().add("All Sub-Statuses");
            if (newStatus != null && !"All Statuses".equals(newStatus)) {
                List<String> subStatuses = StatusManager.getSubStatuses(newStatus);
                if (subStatuses != null) {
                    subStatusFilterCombo.getItems().addAll(subStatuses);
                }
            }
            subStatusFilterCombo.getSelectionModel().selectFirst();
        });

        statusUpdateCombo.getItems().addAll(StatusManager.getStatuses());

        statusUpdateCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            subStatusUpdateCombo.getItems().clear();
            if (newVal != null) {
                subStatusUpdateCombo.getItems().addAll(StatusManager.getSubStatuses(newVal));
                if (!subStatusUpdateCombo.getItems().isEmpty()) {
                    subStatusUpdateCombo.getSelectionModel().select(0);
                }
            }
            updateDisposalControlsVisibility();
        });

        subStatusUpdateCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateDisposalControlsVisibility());
    }

    private void setupTableSelectionListener() {
        statusTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean isItemSelected = newSelection != null;
            updateDetailsPanel.setVisible(isItemSelected);
            updateDetailsPanel.setManaged(isItemSelected);

            if (isItemSelected) {
                serialDisplayField.setText(newSelection.getSerialNumber());
                statusUpdateCombo.setValue(newSelection.getStatus());
                subStatusUpdateCombo.setValue(newSelection.getSubStatus());

                if (newSelection.isIsFlagged()) {
                    String reason = findFlagReason(newSelection.getSerialNumber());
                    flagReasonLabel.setText("Flag Reason: " + reason);
                    flagReasonLabel.setVisible(true);
                    flagReasonLabel.setManaged(true);
                } else {
                    flagReasonLabel.setVisible(false);
                    flagReasonLabel.setManaged(false);
                }
                updateDisposalControlsVisibility();
            } else {
                flagReasonLabel.setVisible(false);
                flagReasonLabel.setManaged(false);
            }
        });
    }

    private void updateDisposalControlsVisibility() {
        String status = statusUpdateCombo.getValue();
        String subStatus = subStatusUpdateCombo.getValue();
        boolean needsBoxId = "Disposed".equals(status) && !"Ready for Wipe".equals(subStatus);
        boxIdLabel.setVisible(needsBoxId);
        boxIdLabel.setManaged(needsBoxId);
        boxIdField.setVisible(needsBoxId);
        boxIdField.setManaged(needsBoxId);
        if (!needsBoxId) {
            boxIdField.clear();
        }
    }

    @FXML
    private void onUpdateDeviceAction() {
        ObservableList<DeviceStatusView> selectedDevices = statusTable.getSelectionModel().getSelectedItems();
        String newStatus = statusUpdateCombo.getValue();
        String newSubStatus = subStatusUpdateCombo.getValue();

        String boxId = null;
        String note = "";

        boolean needsBoxId = "Disposed".equals(newStatus) && !"Ready for Wipe".equals(newSubStatus);
        if (needsBoxId) {
            boxId = boxIdField.getText().trim();
            if (boxId.isEmpty()) {
                StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "Box ID Required", "A Box ID is required for this disposed status. The update was cancelled.");
                return;
            }
        }
        deviceStatusManager.updateDeviceStatus(selectedDevices, newStatus, newSubStatus, note, boxId);
        boxIdField.clear();
        refreshData();
    }

    private void setupTableColumns() {
        serialNumberCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        makeCol.setCellValueFactory(new PropertyValueFactory<>("make"));
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        subStatusCol.setCellValueFactory(new PropertyValueFactory<>("subStatus"));
        lastUpdateCol.setCellValueFactory(new PropertyValueFactory<>("lastUpdate"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("changeNote"));
        statusTable.setItems(deviceStatusManager.getDeviceStatusList());
        statusTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        statusTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        statusTable.setPlaceholder(new Label("No devices found matching the current filters."));
    }

    private void setupFilters() {
        categoryFilterCombo.getItems().add("All Categories");
        loadFilterCategories();
        categoryFilterCombo.getSelectionModel().selectFirst();
        groupByCombo.getItems().addAll("None", "Status", "Category");
        groupByCombo.getSelectionModel().selectFirst();
        rowsPerPageCombo.getItems().addAll(50, 100, 200, 500);
        rowsPerPageCombo.setValue(deviceStatusManager.getRowsPerPage());
        rowsPerPageCombo.valueProperty().addListener((obs, old, val) -> {
            if (val != null) {
                deviceStatusManager.setRowsPerPage(val);
                deviceStatusManager.resetPagination();
            }
        });

        subStatusFilterCombo.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
        statusFilterCombo.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
        categoryFilterCombo.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
        groupByCombo.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
        fromDateFilter.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
        toDateFilter.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
    }

    private void loadFilterCategories() {
        if (cachedCategories != null) {
            categoryFilterCombo.getItems().addAll(cachedCategories);
            return;
        }
        cachedCategories = new ArrayList<>();
        String sql = """
                    SELECT DISTINCT pa.category
                    FROM physical_assets pa
                    JOIN (
                        SELECT serial_number, MAX(receipt_id) AS max_receipt_id
                        FROM receipt_events
                        GROUP BY serial_number
                    ) latest ON pa.serial_number = latest.serial_number
                    WHERE pa.category IS NOT NULL AND pa.category != ''
                    ORDER BY pa.category
                """;

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                cachedCategories.add(rs.getString("category"));
            }
            categoryFilterCombo.getItems().addAll(cachedCategories);
        } catch (SQLException e) {
            Platform.runLater(() -> StageManager.showAlert(statusTable.getScene().getWindow(), Alert.AlertType.ERROR, "Database Error", "Failed to load categories for filtering."));
        }
    }

    private String findFlagReason(String serialNumber) {
        String sql = "SELECT flag_reason FROM flag_devices WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("flag_reason");
                }
            }
        } catch (SQLException e) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Database Error", "Could not look up flag reason: " + e.getMessage());
        }
        return "Reason not found.";
    }

    @FXML
    private void handleReassignPackage() {
        DeviceStatusView selectedDevice = statusTable.getSelectionModel().getSelectedItem();
        if (selectedDevice == null) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "No Selection", "Please select a device to reassign.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/SelectPackageDialog.fxml"));
            Parent root = loader.load();
            SelectPackageDialogController dialogController = loader.getController();
            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Select New Package for " + selectedDevice.getSerialNumber(), root);
            stage.showAndWait();

            dialogController.getResult().ifPresent(result -> {
                if (!result.createNew() && result.selectedPackage() != null) {
                    assettracking.data.Package newPackage = result.selectedPackage();
                    boolean success = new assettracking.dao.ReceiptEventDAO().updatePackageId(selectedDevice.getReceiptId(), newPackage.getPackageId());

                    if (success) {
                        StageManager.showAlert(getOwnerWindow(), Alert.AlertType.INFORMATION, "Success", "Device was successfully moved to package " + newPackage.getTrackingNumber());
                        refreshData();
                    } else {
                        StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Failed to update the package in the database.");
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("Service error: " + e.getMessage());
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Could not open package selection dialog.");
        }
    }

    private void configureTableRowFactory() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem historyMenuItem = new MenuItem("View Full History");
        historyMenuItem.setOnAction(event -> handleViewHistory());
        MenuItem editMenuItem = new MenuItem("Edit Selected Device...");
        editMenuItem.setOnAction(event -> handleEditDevice());
        MenuItem reassignMenuItem = new MenuItem("Reassign Package...");
        reassignMenuItem.setOnAction(event -> handleReassignPackage());
        MenuItem deleteMenuItem = new MenuItem("Delete Selected Device...");
        deleteMenuItem.setOnAction(event -> handleDeleteDevice());
        contextMenu.getItems().addAll(historyMenuItem, new SeparatorMenuItem(), reassignMenuItem, editMenuItem, deleteMenuItem);

        statusTable.setRowFactory(tv -> {
            TableRow<DeviceStatusView> row = new TableRow<>();
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty().not()).then(contextMenu).otherwise((ContextMenu) null));
            row.itemProperty().addListener((obs, previousItem, currentItem) -> {
                row.getStyleClass().removeAll("flagged-row", "wip-row", "disposal-row", "processed-row", "shipped-row");
                if (currentItem != null) {
                    if (currentItem.isIsFlagged()) {
                        row.getStyleClass().add("flagged-row");
                    } else {
                        String status = currentItem.getStatus() != null ? currentItem.getStatus() : "";
                        switch (status) {
                            case "WIP":
                                row.getStyleClass().add("wip-row");
                                break;
                            case "Disposal/EOL":
                            case "Disposed":
                                row.getStyleClass().add("disposal-row");
                                break;
                            case "Processed":
                                row.getStyleClass().add("processed-row");
                                break;
                            case "Everon":
                            case "Phone":
                                row.getStyleClass().add("shipped-row");
                                break;
                        }
                    }
                }
            });
            return row;
        });
    }

    private void handleDeleteDevice() {
        DeviceStatusView selectedDevice = statusTable.getSelectionModel().getSelectedItem();
        if (selectedDevice == null) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "No Selection", "Please select a device to delete.");
            return;
        }
        if (StageManager.showDeleteConfirmationDialog(getOwnerWindow(), "device", selectedDevice.getSerialNumber())) {
            deviceStatusManager.updateDeviceStatus(FXCollections.observableArrayList(selectedDevice), "Disposed", "Deleted (Mistake)", "Entry deleted by user.", null);
            refreshData();
        }
    }

    @FXML
    private void handleManageFlags() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/FlagManagementDialog.fxml"));
            Parent root = loader.load();
            FlagManagementDialogController dialogController = loader.getController();
            dialogController.setOnSaveCallback(this::refreshData);

            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Manage Flagged Devices", root);
            stage.showAndWait();

        } catch (IOException e) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Could not open the Flag Management window.");
        }
    }

    private void handleViewHistory() {
        DeviceStatusView selectedDevice = statusTable.getSelectionModel().getSelectedItem();
        if (selectedDevice != null) {
            deviceStatusActions.openDeviceHistoryWindow(selectedDevice.getSerialNumber());
        }
    }

    private void clearFilterInputs() {
        serialSearchField.clear();
        statusFilterCombo.getSelectionModel().selectFirst();
        categoryFilterCombo.getSelectionModel().selectFirst();
        groupByCombo.getSelectionModel().selectFirst();
        fromDateFilter.setValue(null);
        toDateFilter.setValue(null);
    }

    @FXML
    private void handleExportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Asset Report");

        FileChooser.ExtensionFilter csvFilter = new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv");
        FileChooser.ExtensionFilter xlsxFilter = new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx");
        fileChooser.getExtensionFilters().addAll(csvFilter, xlsxFilter);

        File file = fileChooser.showSaveDialog(getOwnerWindow());

        if (file != null) {
            String extension = fileChooser.getSelectedExtensionFilter().getExtensions().getFirst();

            if (extension.equals("*.csv")) {
                String csvPath = file.getAbsolutePath().toLowerCase().endsWith(".csv") ? file.getAbsolutePath() : file.getAbsolutePath() + ".csv";
                deviceStatusActions.exportToCSV(new File(csvPath));
            } else if (extension.equals("*.xlsx")) {
                String xlsxPath = file.getAbsolutePath().toLowerCase().endsWith(".xlsx") ? file.getAbsolutePath() : file.getAbsolutePath() + ".xlsx";
                deviceStatusActions.exportToXLSX(new File(xlsxPath));
            }
        }
    }

    @FXML
    private void onRefreshAction() {
        refreshData();
    }

    @FXML
    private void onClearFiltersAction() {
        clearFilterInputs();
        deviceStatusManager.resetPagination();
    }

    @FXML
    private void onViewHistoryAction() {
        handleViewHistory();
    }

    @FXML
    private void onSearchAction() {
        deviceStatusManager.resetPagination();
    }

    @FXML
    private void handleExport() {
        deviceStatusActions.exportToCSV(null);
    }

    @FXML
    private void onScanUpdateAction() {
        deviceStatusActions.openScanUpdateWindow();
    }

    public void refreshData() {
        deviceStatusManager.resetPagination();
    }

    private Window getOwnerWindow() {
        return statusTable.getScene().getWindow();
    }
}