package assettracking.controller;

import assettracking.dao.bulk.iPadProvisioningDAO;
import assettracking.data.bulk.BulkDevice;
import assettracking.data.bulk.RosterEntry;
import assettracking.data.bulk.StagedDevice;
import assettracking.ui.ExcelReader;
import assettracking.ui.ExcelWriter;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class iPadProvisioningController {

    // FXML Injections
    @FXML private Button importDeviceListButton;
    @FXML private Label deviceListStatusLabel;
    @FXML private Button importRosterButton;
    @FXML private TextField snRefFilterField;
    @FXML private TableView<RosterEntry> rosterTable;
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
    @FXML private Button exportButton;
    @FXML private Label statusLabel;

    private final iPadProvisioningDAO dao = new iPadProvisioningDAO();
    private final ObservableList<RosterEntry> rosterList = FXCollections.observableArrayList();
    private FilteredList<RosterEntry> filteredRosterList;
    private final ObservableList<StagedDevice> stagedDeviceList = FXCollections.observableArrayList();

    private boolean isDeviceListLoaded = false;
    private boolean isRosterLoaded = false;

    @FXML
    public void initialize() {
        setupRosterTable();
        setupStagingTable();

        // --- MODIFIED: Check the DB on startup ---
        checkInitialDeviceState();
        updateWorkflowControls(); // Initial state is disabled

        serialScanField.setOnAction(event -> snRefFilterField.requestFocus());
        snRefFilterField.setOnAction(event -> handleAddDeviceToStaging());
    }

    // --- NEW: Method to check DB for existing devices when the tab is opened ---
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
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not connect to check for existing devices.");
        });

        new Thread(dbCheckTask).start();
    }

    private void updateWorkflowControls() {
        boolean isReady = isDeviceListLoaded && isRosterLoaded;
        snRefFilterField.setDisable(!isReady);
        serialScanField.setDisable(!isReady);

        if (isReady) {
            serialScanField.requestFocus();
        }
    }

    // ... (The rest of your methods remain unchanged)

    private void setupRosterTable() {
        rosterNameCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFirstName() + " " + cellData.getValue().getLastName()));
        rosterSnRefCol.setCellValueFactory(new PropertyValueFactory<>("snReferenceNumber"));

        filteredRosterList = new FilteredList<>(rosterList, p -> true);
        rosterTable.setItems(filteredRosterList);

        snRefFilterField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredRosterList.setPredicate(rosterEntry -> {
                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }
                return rosterEntry.getSnReferenceNumber().toLowerCase().endsWith(newVal.toLowerCase());
            });
        });
    }

    private void setupStagingTable() {
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
            String newSim = event.getNewValue();
            updateSimCard(device.getSerialNumber(), newSim);
        });

        stagingTable.setItems(stagedDeviceList);
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
                updateMessage("Reading Excel file...");
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
                showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save devices to the database: " + ex.getMessage());
                statusLabel.setText("Import failed.");
            }
        });

        importTask.setOnFailed(e -> {
            showAlert(Alert.AlertType.ERROR, "Import Error", "Failed to read the device Excel file: " + importTask.getException().getMessage());
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
        importTask.setOnFailed(e -> showAlert(Alert.AlertType.ERROR, "Import Error", "Failed to read roster file: " + importTask.getException().getMessage()));

        new Thread(importTask).start();
    }

    @FXML
    private void handleAddDeviceToStaging() {
        String serial = serialScanField.getText().trim().toUpperCase();
        if (serial.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Error", "Please scan a serial number first.");
            serialScanField.requestFocus();
            return;
        }

        ObservableList<RosterEntry> visibleEmployees = rosterTable.getItems();
        RosterEntry employeeToAssign = null;

        if (visibleEmployees.size() == 1) {
            employeeToAssign = visibleEmployees.get(0);
        } else {
            employeeToAssign = rosterTable.getSelectionModel().getSelectedItem();
        }

        if (employeeToAssign == null) {
            if (visibleEmployees.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Selection Error", "No employee matches the SN Ref #. Please check the number.");
                snRefFilterField.requestFocus();
            } else {
                showAlert(Alert.AlertType.WARNING, "Selection Error", "Multiple employees match the SN Ref #. Please click one from the table to select it.");
                rosterTable.requestFocus();
            }
            return;
        }

        try {
            Optional<BulkDevice> deviceOpt = dao.findDeviceBySerial(serial);
            if (deviceOpt.isPresent()) {
                boolean alreadyStaged = stagedDeviceList.stream()
                        .anyMatch(d -> d.getSerialNumber().equals(serial));

                if(alreadyStaged) {
                    showAlert(Alert.AlertType.WARNING, "Duplicate Entry", "Device with serial number " + serial + " is already in the staging list.");
                } else {
                    StagedDevice newStagedDevice = new StagedDevice(employeeToAssign, deviceOpt.get());
                    stagedDeviceList.add(newStagedDevice);
                    statusLabel.setText("Added " + serial);
                    exportButton.setDisable(false);
                }
            } else {
                showAlert(Alert.AlertType.WARNING, "Device Not Found", "Serial number " + serial + " was not found in the imported device list.");
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not look up serial number: " + e.getMessage());
            e.printStackTrace();
        }

        serialScanField.clear();
        snRefFilterField.clear();
        serialScanField.requestFocus();
    }

    private void updateSimCard(String serialNumber, String newSim) {
        Task<Void> updateTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                dao.updateSimCard(serialNumber, newSim);
                return null;
            }
        };
        updateTask.setOnSucceeded(e -> statusLabel.setText("Updated SIM for " + serialNumber));
        updateTask.setOnFailed(e -> showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to update SIM: " + updateTask.getException().getMessage()));
        new Thread(updateTask).start();
    }

    @FXML
    private void handleRemoveSelected() {
        stagedDeviceList.removeAll(stagingTable.getSelectionModel().getSelectedItems());
        if(stagedDeviceList.isEmpty()){
            exportButton.setDisable(true);
        }
    }

    @FXML
    private void handleExport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Exported Template");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M-d-yyyy");
        String currentDate = LocalDate.now().format(formatter);
        String initialFileName = String.format("ADT - vMOX Bulk Order %s.xlsx", currentDate);
        fileChooser.setInitialFileName(initialFileName);

        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showSaveDialog(getStage());
        if (file == null) return;

        File templateFile = new File(getClass().getResource("/template/Device_Submission_Template.xlsx").getFile());
        List<StagedDevice> dataToExport = new ArrayList<>(stagedDeviceList);

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ExcelWriter.writeTemplate(templateFile, file, dataToExport);
                dao.saveAssignments(dataToExport);
                return null;
            }
        };

        exportTask.setOnRunning(e -> statusLabel.setText("Exporting..."));
        exportTask.setOnSucceeded(e -> {
            showAlert(Alert.AlertType.INFORMATION, "Success", "Export complete and assignments have been saved.");
            stagedDeviceList.clear();
            exportButton.setDisable(true);
            statusLabel.setText("Export complete.");
        });
        exportTask.setOnFailed(e -> {
            showAlert(Alert.AlertType.ERROR, "Export Error", "An error occurred during export: " + exportTask.getException().getMessage());
            exportTask.getException().printStackTrace();
        });

        new Thread(exportTask).start();
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private Stage getStage() {
        return (Stage) statusLabel.getScene().getWindow();
    }
}