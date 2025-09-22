package assettracking.controller;

import assettracking.dao.bulk.iPadProvisioningDAO;
import assettracking.data.bulk.BulkDevice;
import assettracking.data.bulk.RosterEntry;
import assettracking.data.bulk.StagedDevice;
import assettracking.manager.DeviceImportService;
import assettracking.manager.ProvisioningExportService;
import assettracking.manager.RosterImportService;
import assettracking.manager.StageManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class iPadProvisioningController {

    // --- Services and DAO ---
    private final DeviceImportService deviceImporter = new DeviceImportService();
    private final RosterImportService rosterImporter = new RosterImportService();
    private final ProvisioningExportService exportService = new ProvisioningExportService();
    private final iPadProvisioningDAO dao = new iPadProvisioningDAO();
    // --- UI State and Data Lists ---
    private final ObservableList<RosterEntry> rosterList = FXCollections.observableArrayList();
    private final ObservableList<StagedDevice> stagedDeviceList = FXCollections.observableArrayList();
    private final ObservableList<BulkDevice> dbDeviceList = FXCollections.observableArrayList();
    // --- FXML Fields ---
    @FXML
    private Button importDeviceListButton, importRosterButton, exportButton, stageUnassignedButton;
    @FXML
    private Label deviceListStatusLabel, statusLabel;
    @FXML
    private TextField snRefFilterField, serialScanField, dbSearchField;
    @FXML
    private TableView<RosterEntry> rosterTable;
    @FXML
    private TableColumn<RosterEntry, String> rosterNameCol, rosterSnRefCol;
    @FXML
    private TableView<StagedDevice> stagingTable;
    @FXML
    private TableColumn<StagedDevice, String> stageFirstNameCol, stageLastNameCol, stageSerialCol, stageImeiCol, stageSimCol, stageSnRefCol, stageEmailCol, stageCarrierCol, stageCarrierAccountCol;
    @FXML
    private TableView<BulkDevice> dbResultsTable;
    @FXML
    private TableColumn<BulkDevice, String> dbSerialCol, dbImeiCol, dbSimCol, dbDeviceNameCol;
    @FXML
    private ToggleButton bulkModeToggle;
    @FXML
    private Button clearStagingButton;

    private FilteredList<RosterEntry> filteredRosterList;
    private boolean isDeviceListLoaded = false;
    private boolean isRosterLoaded = false;

    @FXML
    public void initialize() {
        setupTablesAndFilters();
        setupEventListeners();
        refreshData();
        updateWorkflowControls();
    }

    private void setupTablesAndFilters() {
        // Roster Table
        rosterNameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFirstName() + " " + cellData.getValue().getLastName()));
        rosterSnRefCol.setCellValueFactory(new PropertyValueFactory<>("snReferenceNumber"));
        filteredRosterList = new FilteredList<>(rosterList, p -> true);
        rosterTable.setItems(filteredRosterList);
        snRefFilterField.textProperty().addListener((obs, o, n) -> filteredRosterList.setPredicate(entry -> n == null || n.isEmpty() || entry.getSnReferenceNumber().toLowerCase().endsWith(n.toLowerCase())));

        // Staging Table
        stageCarrierCol.setCellValueFactory(new PropertyValueFactory<>("carrier"));
        stageCarrierAccountCol.setCellValueFactory(new PropertyValueFactory<>("carrierAccountNumber"));
        stageFirstNameCol.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        stageLastNameCol.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        stageSerialCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        stageImeiCol.setCellValueFactory(new PropertyValueFactory<>("imei"));
        stageSimCol.setCellValueFactory(new PropertyValueFactory<>("sim"));
        stageSnRefCol.setCellValueFactory(new PropertyValueFactory<>("snReferenceNumber"));
        stageEmailCol.setCellValueFactory(new PropertyValueFactory<>("employeeEmail"));
        stageSimCol.setCellFactory(TextFieldTableCell.forTableColumn());
        stagingTable.setItems(stagedDeviceList);
        stagingTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(StagedDevice item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("t-mobile-override-row");
                if (item != null && !empty && item.isWasAutoSetToTmobile()) {
                    getStyleClass().add("t-mobile-override-row");
                }
            }
        });

        // DB Search Table
        dbSerialCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        dbImeiCol.setCellValueFactory(new PropertyValueFactory<>("imei"));
        dbSimCol.setCellValueFactory(new PropertyValueFactory<>("iccid"));
        dbDeviceNameCol.setCellValueFactory(new PropertyValueFactory<>("deviceName"));
        dbResultsTable.setItems(dbDeviceList);
    }

    private void setupEventListeners() {
        serialScanField.setOnAction(event -> {
            if (bulkModeToggle.isSelected()) snRefFilterField.requestFocus();
            else handleStageUnassigned();
        });
        bulkModeToggle.selectedProperty().addListener((obs, o, n) -> {
            bulkModeToggle.setText("Bulk Assignment Mode: " + (n ? "ON" : "OFF"));
            if (n) bulkModeToggle.getStyleClass().add("success");
            else bulkModeToggle.getStyleClass().remove("success");
            updateWorkflowControls();
        });
        stageSimCol.setOnEditCommit(event -> updateSimCardInDb(event.getRowValue().getSerialNumber(), event.getNewValue()));
        snRefFilterField.setOnAction(event -> handleAddDeviceToStaging());
        dbSearchField.setOnAction(event -> handleDbSearch());
    }

    @FXML
    private void handleClearStaging() {
        if (!stagedDeviceList.isEmpty()) {
            boolean confirmed = StageManager.showConfirmationDialog(getStage(), "Confirm Clear", "Are you sure you want to remove all " + stagedDeviceList.size() + " devices from the staging area?", "This action cannot be undone.");
            if (confirmed) {
                stagedDeviceList.clear();
                updateWorkflowControls();
                statusLabel.setText("Staging area cleared.");
            }
        }
    }

    public void refreshData() {
        Task<Integer> dbCheckTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return dao.getDeviceCount();
            }
        };
        dbCheckTask.setOnSucceeded(e -> {
            if (dbCheckTask.getValue() > 0) {
                isDeviceListLoaded = true;
                deviceListStatusLabel.setText(String.format("Device List: %d devices in DB.", dbCheckTask.getValue()));
                updateWorkflowControls();
            }
        });
        new Thread(dbCheckTask).start();
    }

    private void updateWorkflowControls() {
        boolean isStagingEmpty = stagedDeviceList.isEmpty(); // Calculate once for efficiency

        boolean assignmentReady = isDeviceListLoaded && isRosterLoaded && bulkModeToggle.isSelected();
        serialScanField.setDisable(!isDeviceListLoaded);
        stageUnassignedButton.setDisable(!isDeviceListLoaded);
        bulkModeToggle.setDisable(!isDeviceListLoaded);
        snRefFilterField.setDisable(!assignmentReady);
        rosterTable.setDisable(!assignmentReady);

        // This now correctly enables/disables BOTH buttons based on the list's state
        exportButton.setDisable(isStagingEmpty);
        clearStagingButton.setDisable(isStagingEmpty);

        if (isDeviceListLoaded) {
            serialScanField.requestFocus();
        }
    }

    @FXML
    private void handleImportDeviceList() {
        File file = showFileChooser("Select 'Device Information Full' Excel File");
        if (file == null) return;

        Task<Integer> importTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return deviceImporter.importFromFile(file);
            }
        };
        importTask.setOnRunning(e -> statusLabel.setText("Importing device list..."));
        importTask.setOnSucceeded(e -> {
            int count = importTask.getValue();
            deviceListStatusLabel.setText(String.format("Device List: %d devices loaded/updated.", count));
            statusLabel.setText("Device list import successful!");
            isDeviceListLoaded = true;
            updateWorkflowControls();
        });
        importTask.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Import Error", "Failed to process device file: " + importTask.getException().getMessage()));
        new Thread(importTask).start();
    }

    @FXML
    private void handleImportRoster() {
        File file = showFileChooser("Select 'Sales Readiness Roster' Excel File");
        if (file == null) return;

        Task<List<RosterEntry>> importTask = new Task<>() {
            @Override
            protected List<RosterEntry> call() throws Exception {
                return rosterImporter.importFromFile(file);
            }
        };
        importTask.setOnRunning(e -> statusLabel.setText("Loading roster..."));
        importTask.setOnSucceeded(e -> {
            rosterList.setAll(importTask.getValue());
            statusLabel.setText(String.format("Roster loaded with %d entries.", rosterList.size()));
            isRosterLoaded = true;
            updateWorkflowControls();
        });
        importTask.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Import Error", "Failed to read roster file: " + importTask.getException().getMessage()));
        new Thread(importTask).start();
    }

    @FXML
    private void handleAddDeviceToStaging() {
        String serial = serialScanField.getText().trim().toUpperCase();
        if (serial.isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Error", "Please scan a serial number.");
            return;
        }
        if (stagedDeviceList.stream().anyMatch(d -> d.getSerialNumber().equals(serial))) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Duplicate", "Device " + serial + " is already staged.");
            return;
        }

        RosterEntry employee = rosterTable.getItems().size() == 1 ? rosterTable.getItems().getFirst() : rosterTable.getSelectionModel().getSelectedItem();
        if (employee == null) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Selection Error", "Please select an employee.");
            return;
        }

        try {
            Optional<BulkDevice> deviceOpt = dao.findDeviceBySerial(serial);
            if (deviceOpt.isPresent()) {
                StagedDevice newDevice = new StagedDevice(employee, deviceOpt.get());
                if (bulkModeToggle.isSelected() && "PR".equalsIgnoreCase(employee.getCountry())) {
                    newDevice.setCarrier("T-Mobile");
                    newDevice.setCarrierAccountNumber("TMB-x285");
                    newDevice.setWasAutoSetToTmobile(true);
                }
                stagedDeviceList.add(newDevice);
                statusLabel.setText("Added " + serial);
                updateWorkflowControls();
            } else {
                StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Not Found", "Serial " + serial + " not found.");
            }
        } catch (SQLException e) {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Error", "Could not look up serial: " + e.getMessage());
        }
        serialScanField.clear();
        snRefFilterField.clear();
        serialScanField.requestFocus();
    }

    @FXML
    private void handleStageUnassigned() {
        String serial = serialScanField.getText().trim().toUpperCase();
        if (serial.isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Error", "Please scan a serial number.");
            return;
        }
        if (stagedDeviceList.stream().anyMatch(d -> d.getSerialNumber().equals(serial))) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Duplicate", "Device " + serial + " is already staged.");
            return;
        }

        try {
            dao.findDeviceBySerial(serial).ifPresentOrElse(device -> {
                stagedDeviceList.add(new StagedDevice(device));
                statusLabel.setText("Added unassigned device: " + serial);
                updateWorkflowControls();
            }, () -> StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Not Found", "Serial " + serial + " not found."));
        } catch (SQLException e) {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Error", "Could not look up serial: " + e.getMessage());
        }
        serialScanField.clear();
        serialScanField.requestFocus();
    }

    @FXML
    private void handleExport() {
        File file = showSaveDialog();
        if (file == null) return;

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<StagedDevice> devicesToExport = new ArrayList<>(stagedDeviceList);
                exportService.exportToFile(file, devicesToExport);
                dao.saveAssignments(devicesToExport);
                return null;
            }
        };
        exportTask.setOnRunning(e -> statusLabel.setText("Exporting..."));
        exportTask.setOnSucceeded(e -> {
            StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Success", "Export complete and assignments saved.");
            stagedDeviceList.clear();
            updateWorkflowControls();
            statusLabel.setText("Export complete.");
        });
        exportTask.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Export Error", "An error occurred: " + e.getSource().getException().getMessage()));
        new Thread(exportTask).start();
    }

    @FXML
    private void handleDbSearch() {
        String query = dbSearchField.getText().trim();
        if (query.isEmpty()) {
            dbDeviceList.clear();
            return;
        }
        Task<List<BulkDevice>> searchTask = new Task<>() {
            @Override
            protected List<BulkDevice> call() throws Exception {
                return dao.searchDevices(query);
            }
        };
        searchTask.setOnSucceeded(e -> {
            dbDeviceList.setAll(searchTask.getValue());
            statusLabel.setText("Found " + dbDeviceList.size() + " devices in DB.");
        });
        searchTask.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "DB Search Error", "Failed to search devices: " + searchTask.getException().getMessage()));
        new Thread(searchTask).start();
    }

    @FXML
    private void handleSetTmobile() {
        StagedDevice selectedDevice = stagingTable.getSelectionModel().getSelectedItem();
        if (selectedDevice == null) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "No Selection", "Please select a device in the staging table to update.");
            return;
        }
        selectedDevice.setCarrier("T-Mobile");
        selectedDevice.setCarrierAccountNumber("TMB-x285");
        stagingTable.refresh();
        statusLabel.setText("Set " + selectedDevice.getSerialNumber() + " to T-Mobile.");
    }

    @FXML
    private void handleUpdateSim() {
        StagedDevice selectedDevice = stagingTable.getSelectionModel().getSelectedItem();
        if (selectedDevice == null) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "No Selection", "Please select a device in the staging table to update its SIM.");
            return;
        }
        Optional<String> result = StageManager.showTextInputDialog(getStage(), "Update SIM Card Number", "Update SIM for device: " + selectedDevice.getSerialNumber(), "Please scan or enter the NEW SIM card (ICCID):", selectedDevice.getSim());
        result.ifPresent(newSimRaw -> {
            String newSim = newSimRaw.trim();
            if (newSim.isEmpty() || newSim.equals(selectedDevice.getSim())) return;
            selectedDevice.setSim(newSim);
            stagingTable.refresh();
            updateSimCardInDb(selectedDevice.getSerialNumber(), newSim);
            statusLabel.setText("Updated SIM for " + selectedDevice.getSerialNumber());
        });
    }

    @FXML
    private void handleRemoveSelected() {
        stagedDeviceList.removeAll(stagingTable.getSelectionModel().getSelectedItems());
        updateWorkflowControls();
    }

    private void updateSimCardInDb(String serialNumber, String newSim) {
        Task<Void> updateTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                dao.updateSimCard(serialNumber, newSim);
                return null;
            }
        };
        updateTask.setOnSucceeded(e -> statusLabel.setText("Updated SIM in DB for " + serialNumber));
        updateTask.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Error", "Failed to update SIM in DB: " + updateTask.getException().getMessage()));
        new Thread(updateTask).start();
    }

    private File showFileChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        return fc.showOpenDialog(getStage());
    }

    private File showSaveDialog() {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(String.format("ADT - vMOX Bulk Order %s.xlsx", LocalDate.now().format(DateTimeFormatter.ofPattern("M-d-yyyy"))));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        return fc.showSaveDialog(getStage());
    }

    private Stage getStage() {
        return (Stage) statusLabel.getScene().getWindow();
    }
}