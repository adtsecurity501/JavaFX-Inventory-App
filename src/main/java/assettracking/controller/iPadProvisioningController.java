package assettracking.controller;

import assettracking.dao.bulk.iPadProvisioningDAO;
import assettracking.data.bulk.BulkDevice;
import assettracking.data.bulk.RosterEntry;
import assettracking.data.bulk.StagedDevice;
import assettracking.ui.ExcelReader;
import assettracking.ui.ExcelWriter;
import assettracking.manager.StageManager;
import javafx.application.Platform;
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
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class iPadProvisioningController {

    // --- All FXML Fields ---
    @FXML private Button importDeviceListButton;
    @FXML private Label deviceListStatusLabel;
    @FXML private Button importRosterButton;
    @FXML private TextField snRefFilterField;
    @FXML private TableView<RosterEntry> rosterTable;
    // --- FIX: All @FXML declarations are now present ---
    @FXML private TableColumn<RosterEntry, String> rosterNameCol;
    @FXML private TableColumn<RosterEntry, String> rosterSnRefCol;
    @FXML private TextField serialScanField;
    @FXML private TableView<StagedDevice> stagingTable;
    @FXML private TableColumn<StagedDevice, String> stageFirstNameCol;
    @FXML private TableColumn<StagedDevice, String> stageLastNameCol;
    @FXML private TableColumn<StagedDevice, String> stageSerialCol;
    @FXML private TableColumn<StagedDevice, String> stageImeiCol;
    @FXML private TableColumn<StagedDevice, String> stageSimCol;
    @FXML private TableColumn<StagedDevice, String> stageSnRefCol;
    @FXML private TableColumn<StagedDevice, String> stageEmailCol;
    @FXML private TableColumn<StagedDevice, String> stageCarrierCol;
    @FXML private TableColumn<StagedDevice, String> stageCarrierAccountCol;
    @FXML private Button exportButton;
    @FXML private Label statusLabel;
    @FXML private TextField dbSearchField;
    @FXML private TableView<BulkDevice> dbResultsTable;
    @FXML private TableColumn<BulkDevice, String> dbSerialCol;
    @FXML private TableColumn<BulkDevice, String> dbImeiCol;
    @FXML private TableColumn<BulkDevice, String> dbSimCol;
    @FXML private TableColumn<BulkDevice, String> dbDeviceNameCol;
    @FXML private Button stageUnassignedButton;
    @FXML private ToggleButton bulkModeToggle;

    private final iPadProvisioningDAO dao = new iPadProvisioningDAO();
    private final ObservableList<RosterEntry> rosterList = FXCollections.observableArrayList();
    private FilteredList<RosterEntry> filteredRosterList;
    private final ObservableList<StagedDevice> stagedDeviceList = FXCollections.observableArrayList();
    private final ObservableList<BulkDevice> dbDeviceList = FXCollections.observableArrayList();

    private boolean isDeviceListLoaded = false;
    private boolean isRosterLoaded = false;

    @FXML
    public void initialize() {
        setupRosterTable();
        setupStagingTable();
        setupDbSearchTable();
        checkInitialDeviceState();
        updateWorkflowControls();

        serialScanField.setOnAction(event -> {
            if (bulkModeToggle.isSelected()) {
                snRefFilterField.requestFocus();
            } else {
                handleStageUnassigned();
            }
        });

        bulkModeToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                bulkModeToggle.setText("Bulk Assignment Mode: ON");
                bulkModeToggle.getStyleClass().add("success");
            } else {
                bulkModeToggle.setText("Bulk Assignment Mode: OFF");
                bulkModeToggle.getStyleClass().remove("success");
            }
            updateWorkflowControls();
        });

        snRefFilterField.setOnAction(event -> handleAddDeviceToStaging());
        dbSearchField.setOnAction(event -> handleDbSearch());
    }

    private void checkInitialDeviceState() {
        Task<Integer> dbCheckTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return dao.getDeviceCount();
            }
        };

        dbCheckTask.setOnSucceeded(e -> {
            int count = dbCheckTask.getValue();
            if (count > 0) {
                isDeviceListLoaded = true;
                deviceListStatusLabel.setText(String.format("Device List: %d devices loaded from database.", count));
                updateWorkflowControls();
            }
        });

        dbCheckTask.setOnFailed(e -> {
            deviceListStatusLabel.setText("Database connection failed.");
            StageManager.showAlert(getStage().getScene().getWindow(), Alert.AlertType.ERROR, "Database Error", "Could not connect to check for existing devices.");
        });

        new Thread(dbCheckTask).start();
    }

    private void updateWorkflowControls() {
        serialScanField.setDisable(!isDeviceListLoaded);
        stageUnassignedButton.setDisable(!isDeviceListLoaded);
        bulkModeToggle.setDisable(!isDeviceListLoaded);

        boolean isAssignmentReady = isDeviceListLoaded && isRosterLoaded && bulkModeToggle.isSelected();
        snRefFilterField.setDisable(!isAssignmentReady);
        rosterTable.setDisable(!isAssignmentReady);

        if (isDeviceListLoaded) {
            serialScanField.requestFocus();
        }
    }

    private void setupRosterTable() {
        rosterNameCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFirstName() + " " + cellData.getValue().getLastName()));
        rosterSnRefCol.setCellValueFactory(new PropertyValueFactory<>("snReferenceNumber"));

        filteredRosterList = new FilteredList<>(rosterList, p -> true);
        rosterTable.setItems(filteredRosterList);

        snRefFilterField.textProperty().addListener((obs, oldVal, newVal) -> filteredRosterList.setPredicate(rosterEntry -> {
            if (newVal == null || newVal.isEmpty()) return true;
            return rosterEntry.getSnReferenceNumber().toLowerCase().endsWith(newVal.toLowerCase());
        }));
    }

    private void setupStagingTable() {
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
        stageSimCol.setOnEditCommit(event -> {
            StagedDevice device = event.getRowValue();
            updateSimCardInDb(device.getSerialNumber(), event.getNewValue());
        });

        stagingTable.setItems(stagedDeviceList);
    }

    private void setupDbSearchTable() {
        dbSerialCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        dbImeiCol.setCellValueFactory(new PropertyValueFactory<>("imei"));
        dbSimCol.setCellValueFactory(new PropertyValueFactory<>("iccid"));
        dbDeviceNameCol.setCellValueFactory(new PropertyValueFactory<>("deviceName"));
        dbResultsTable.setItems(dbDeviceList);
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
    private void handleImportDeviceList() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select 'Device Information Full' Excel File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showOpenDialog(getStage());
        if (file == null) return;

        Task<List<BulkDevice>> importTask = new Task<>() {
            @Override
            protected List<BulkDevice> call() throws Exception {
                return ExcelReader.readDeviceFile(file);
            }
        };

        importTask.setOnRunning(e -> statusLabel.setText("Importing device list..."));
        importTask.setOnSucceeded(e -> {
            List<BulkDevice> devices = importTask.getValue();
            try {
                dao.upsertBulkDevices(devices);
                deviceListStatusLabel.setText(String.format("Device List: %d devices loaded/updated.", devices.size()));
                statusLabel.setText("Device list import successful!");
                isDeviceListLoaded = true;
                updateWorkflowControls();
            } catch (SQLException ex) {
                StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Error", "Failed to save devices: " + ex.getMessage());
                statusLabel.setText("Import failed.");
            }
        });
        importTask.setOnFailed(e -> {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Import Error", "Failed to read device file: " + importTask.getException().getMessage());
            statusLabel.setText("Import failed.");
        });
        new Thread(importTask).start();
    }

    @FXML
    private void handleImportRoster() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select 'Sales Readiness Roster' Excel File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showOpenDialog(getStage());
        if (file == null) return;

        Task<List<RosterEntry>> importTask = new Task<>() {
            @Override
            protected List<RosterEntry> call() throws Exception {
                return ExcelReader.readRosterFile(file);
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
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Error", "Please scan a serial number first.");
            serialScanField.requestFocus();
            return;
        }

        RosterEntry employeeToAssign = rosterTable.getItems().size() == 1 ? rosterTable.getItems().getFirst() : rosterTable.getSelectionModel().getSelectedItem();

        if (employeeToAssign == null) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Selection Error", "Please select a single employee from the table.");
            return;
        }

        try {
            Optional<BulkDevice> deviceOpt = dao.findDeviceBySerial(serial);
            if (deviceOpt.isPresent()) {
                if (stagedDeviceList.stream().anyMatch(d -> d.getSerialNumber().equals(serial))) {
                    StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Duplicate Entry", "Device " + serial + " is already staged.");
                } else {
                    stagedDeviceList.add(new StagedDevice(employeeToAssign, deviceOpt.get()));
                    statusLabel.setText("Added " + serial);
                    exportButton.setDisable(false);
                }
            } else {
                StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Device Not Found", "Serial number " + serial + " was not found.");
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
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Error", "Please scan a serial number first.");
            serialScanField.requestFocus();
            return;
        }

        try {
            Optional<BulkDevice> deviceOpt = dao.findDeviceBySerial(serial);
            if (deviceOpt.isPresent()) {
                if (stagedDeviceList.stream().anyMatch(d -> d.getSerialNumber().equals(serial))) {
                    StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Duplicate Entry", "Device " + serial + " is already staged.");
                } else {
                    stagedDeviceList.add(new StagedDevice(deviceOpt.get()));
                    statusLabel.setText("Added unassigned device: " + serial);
                    exportButton.setDisable(false);
                }
            } else {
                StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Device Not Found", "Serial number " + serial + " was not found in the imported device list.");
            }
        } catch (SQLException e) {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Error", "Could not look up serial: " + e.getMessage());
        }

        serialScanField.clear();
        serialScanField.requestFocus();
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

        TextInputDialog dialog = new TextInputDialog(selectedDevice.getSim());
        dialog.setTitle("Update SIM Card Number");
        dialog.setHeaderText("Update SIM for device: " + selectedDevice.getSerialNumber());
        dialog.setContentText("Please scan or enter the NEW SIM card (ICCID):");

        Optional<String> result = dialog.showAndWait();
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
        if(stagedDeviceList.isEmpty()) exportButton.setDisable(true);
    }

    @FXML
    private void handleExport() {
        FileChooser fileChooser = new FileChooser();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M-d-yyyy");
        String currentDate = LocalDate.now().format(formatter);
        fileChooser.setInitialFileName(String.format("ADT - vMOX Bulk Order %s.xlsx", currentDate));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showSaveDialog(getStage());
        if (file == null) return;

        URL resourceUrl = getClass().getResource("/template/Device_Submission_Template.xlsx");
        if (resourceUrl == null) {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Template Not Found", "The required Excel template 'Device_Submission_Template.xlsx' could not be found in the application resources.");
            return;
        }
        File templateFile = new File(resourceUrl.getFile());

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ExcelWriter.writeTemplate(templateFile, file, new ArrayList<>(stagedDeviceList));
                dao.saveAssignments(new ArrayList<>(stagedDeviceList));
                return null;
            }
        };

        exportTask.setOnRunning(e -> statusLabel.setText("Exporting..."));
        exportTask.setOnSucceeded(e -> {
            StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Success", "Export complete and assignments saved.");
            stagedDeviceList.clear();
            exportButton.setDisable(true);
            statusLabel.setText("Export complete.");
        });
        exportTask.setOnFailed(e -> {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Export Error", "An error occurred during export: " + e.getSource().getException().getMessage());
        });
        new Thread(exportTask).start();
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        StageManager.showAlert(getStage(), alertType, title, content);
    }

    private Stage getStage() {
        return (Stage) statusLabel.getScene().getWindow();
    }
}