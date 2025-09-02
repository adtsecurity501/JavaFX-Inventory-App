package assettracking.controller;

import assettracking.data.DeviceStatusView;
import assettracking.db.DatabaseConnection;
import assettracking.manager.DeviceStatusManager;
import assettracking.manager.StatusManager;
import assettracking.ui.DeviceStatusActions;
import assettracking.manager.StageManager;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class DeviceStatusTrackingController {

    // --- (All FXML Fields are the same) ---
    @FXML public Pagination pagination;
    @FXML public TextField serialSearchField;
    @FXML public TableView<DeviceStatusView> statusTable;
    @FXML public TableColumn<DeviceStatusView, String> serialNumberCol;
    @FXML public TableColumn<DeviceStatusView, String> categoryCol;
    @FXML public TableColumn<DeviceStatusView, String> makeCol;
    @FXML public TableColumn<DeviceStatusView, String> descriptionCol;
    @FXML public TableColumn<DeviceStatusView, String> statusCol;
    @FXML public TableColumn<DeviceStatusView, String> subStatusCol;
    @FXML public TableColumn<DeviceStatusView, String> lastUpdateCol;
    @FXML public TableColumn<DeviceStatusView, String> notesCol;
    @FXML public VBox updateDetailsPanel;
    @FXML public TextField serialDisplayField;
    @FXML public ComboBox<String> statusUpdateCombo;
    @FXML public ComboBox<String> subStatusUpdateCombo;
    @FXML public ComboBox<String> statusFilterCombo;
    @FXML public ComboBox<String> categoryFilterCombo;
    @FXML public ComboBox<String> groupByCombo;
    @FXML public DatePicker fromDateFilter;
    @FXML public DatePicker toDateFilter;
    @FXML public ComboBox<Integer> rowsPerPageCombo;
    @FXML public Label flagReasonLabel;
    @FXML private Label boxIdLabel;
    @FXML private TextField boxIdField;
    @FXML private Button updateButton;

    private DeviceStatusManager deviceStatusManager;
    private DeviceStatusActions deviceStatusActions;

    @FXML
    public void initialize() {
        this.deviceStatusManager = new DeviceStatusManager(this);
        this.deviceStatusActions = new DeviceStatusActions(this);
        configureAllUI();
        deviceStatusManager.resetPagination();
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

        statusUpdateCombo.getItems().addAll(StatusManager.getStatuses());

        statusUpdateCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            subStatusUpdateCombo.getItems().clear();
            if (newVal != null) {
                subStatusUpdateCombo.getItems().addAll(StatusManager.getSubStatuses(newVal));
                if (!subStatusUpdateCombo.getItems().isEmpty()) {
                    subStatusUpdateCombo.getSelectionModel().select(0); // Changed from selectFirst()
                }
            }
            updateDisposalControlsVisibility();
        });


        subStatusUpdateCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateDisposalControlsVisibility();
        });
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

    /**
     * MODIFIED: Added a call to refreshData() at the end of the method.
     */
    @FXML
    private void onUpdateDeviceAction() {
        ObservableList<DeviceStatusView> selectedDevices = statusTable.getSelectionModel().getSelectedItems();
        String newStatus = statusUpdateCombo.getValue();
        String newSubStatus = subStatusUpdateCombo.getValue();
        String note = null;

        boolean needsBoxId = "Disposed".equals(newStatus) && !"Ready for Wipe".equals(newSubStatus);

        if (needsBoxId) {
            String boxId = boxIdField.getText().trim();
            if (boxId.isEmpty()) {
                StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "Box ID Required", "A Box ID is required for this disposed status. The update was cancelled.");
                return;
            }
            note = "Box ID: " + boxId;
        }

        deviceStatusManager.updateDeviceStatus(selectedDevices, newStatus, newSubStatus, note);
        boxIdField.clear();

        // --- THIS IS THE NEW LINE ---
        // Refreshes the table to show the change immediately.
        refreshData();
    }

    // --- NO CHANGES TO ANY OTHER METHODS IN THIS FILE ---

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

        statusFilterCombo.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
        categoryFilterCombo.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
        groupByCombo.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
        fromDateFilter.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
        toDateFilter.valueProperty().addListener((obs, old, val) -> deviceStatusManager.resetPagination());
    }

    private void loadFilterCategories() {
        String sql = "SELECT DISTINCT category FROM Receipt_Events WHERE category IS NOT NULL AND category != '' ORDER BY category";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categoryFilterCombo.getItems().add(rs.getString("category"));
            }
        } catch (SQLException e) {
            StageManager.showAlert(statusTable.getScene().getWindow(), Alert.AlertType.ERROR, "Database Error", "Failed to load categories for filtering.");
        }
    }

    private String findFlagReason(String serialNumber) {
        String sql = "SELECT flag_reason FROM Flag_Devices WHERE serial_number = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("flag_reason");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Reason not found.";
    }

    private void configureTableRowFactory() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem historyMenuItem = new MenuItem("View Full History");
        historyMenuItem.setOnAction(event -> {
            DeviceStatusView selectedDevice = statusTable.getSelectionModel().getSelectedItem();
            if (selectedDevice != null) {
                deviceStatusActions.openDeviceHistoryWindow(selectedDevice.getSerialNumber());
            }
        });
        contextMenu.getItems().add(historyMenuItem);

        statusTable.setRowFactory(tv -> {
            TableRow<DeviceStatusView> row = new TableRow<>();
            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty().not())
                            .then(contextMenu)
                            .otherwise((ContextMenu) null)
            );
            row.itemProperty().addListener((obs, previousItem, currentItem) -> {
                // Always remove old styles first
                row.getStyleClass().removeAll("flagged-row", "wip-row", "disposal-row", "processed-row", "shipped-row");
                if (currentItem != null) {
                    if (currentItem.isIsFlagged()) {
                        row.getStyleClass().add("flagged-row");
                    } else {
                        String status = currentItem.getStatus() != null ? currentItem.getStatus() : "";
                        switch (status) {
                            case "WIP": row.getStyleClass().add("wip-row"); break;
                            case "Disposal/EOL": case "Disposed": row.getStyleClass().add("disposal-row"); break;
                            case "Processed": row.getStyleClass().add("processed-row"); break;
                            case "Everon": case "Phone": row.getStyleClass().add("shipped-row"); break;
                        }
                    }
                }
            });
            return row;
        });
    }

    private void clearFilterInputs() {
        serialSearchField.clear();
        statusFilterCombo.getSelectionModel().selectFirst();
        categoryFilterCombo.getSelectionModel().selectFirst();
        groupByCombo.getSelectionModel().selectFirst();
        fromDateFilter.setValue(null);
        toDateFilter.setValue(null);
    }

    @FXML private void onRefreshAction() { refreshData(); }
    @FXML private void onClearFiltersAction() { clearFilterInputs(); deviceStatusManager.resetPagination(); }
    @FXML private void onViewHistoryAction() { deviceStatusActions.openDeviceHistoryWindow(); }
    @FXML private void onSearchAction() { deviceStatusManager.resetPagination(); }
    @FXML private void handleImportFlags() { deviceStatusActions.importFlags(); }
    @FXML private void handleExportToCSV() { deviceStatusActions.exportToCSV(); }
    @FXML private void onScanUpdateAction() { deviceStatusActions.openScanUpdateWindow(); }
    public void refreshData() { deviceStatusManager.resetPagination(); }
    private Window getOwnerWindow() { return statusTable.getScene().getWindow(); }
}