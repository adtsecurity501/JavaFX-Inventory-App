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
    @FXML private TextField dbSearchField;
    @FXML private TableView<BulkDevice> dbResultsTable;
    @FXML private TableColumn<BulkDevice, String> dbSerialCol;
    @FXML private TableColumn<BulkDevice, String> dbImeiCol;
    @FXML private TableColumn<BulkDevice, String> dbSimCol;
    @FXML private TableColumn<BulkDevice, String> dbDeviceNameCol;

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

        serialScanField.setOnAction(event -> snRefFilterField.requestFocus());
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
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not connect to check for existing devices.");
        });

        new Thread(dbCheckTask).start();
    }

    private void updateWorkflowControls() {
        boolean isReady = isDeviceListLoaded && isRosterLoaded;
        snRefFilterField.setDisable(!isReady);
        serialScanField.setDisable(!isReady);
        if (isReady) serialScanField.requestFocus();
    }

    private void setupRosterTable() {
        rosterNameCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFirstName() + " " + cellData.getValue().getLastName()));
        rosterSnRefCol.setCellValueFactory(new PropertyValueFactory<>("snReferenceNumber"));

        filteredRosterList = new FilteredList<>(rosterList, p -> true);
        rosterTable.setItems(filteredRosterList);

        snRefFilterField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredRosterList.setPredicate(rosterEntry -> {
                if (newVal == null || newVal.isEmpty()) return true;
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
            updateSimCard(device.getSerialNumber(), event.getNewValue());
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
        searchTask.setOnFailed(e -> showAlert(Alert.AlertType.ERROR, "DB Search Error", "Failed to search devices: " + searchTask.getException().getMessage()));

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
                showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save devices: " + ex.getMessage());
                statusLabel.setText("Import failed.");
            }
        });
        importTask.setOnFailed(e -> {
            showAlert(Alert.AlertType.ERROR, "Import Error", "Failed to read device file: " + importTask.getException().getMessage());
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

        RosterEntry employeeToAssign = rosterTable.getItems().size() == 1 ? rosterTable.getItems().get(0) : rosterTable.getSelectionModel().getSelectedItem();

        if (employeeToAssign == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Error", "Please select a single employee from the table.");
            return;
        }

        try {
            Optional<BulkDevice> deviceOpt = dao.findDeviceBySerial(serial);
            if (deviceOpt.isPresent()) {
                if (stagedDeviceList.stream().anyMatch(d -> d.getSerialNumber().equals(serial))) {
                    showAlert(Alert.AlertType.WARNING, "Duplicate Entry", "Device " + serial + " is already staged.");
                } else {
                    stagedDeviceList.add(new StagedDevice(employeeToAssign, deviceOpt.get()));
                    statusLabel.setText("Added " + serial);
                    exportButton.setDisable(false);
                }
            } else {
                showAlert(Alert.AlertType.WARNING, "Device Not Found", "Serial number " + serial + " was not found.");
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not look up serial: " + e.getMessage());
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
    private void handleUpdateSerial() {
        StagedDevice selectedDevice = stagingTable.getSelectionModel().getSelectedItem();
        if (selectedDevice == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a device in the staging table to update.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Update Serial Number");
        dialog.setHeaderText("Update serial for " + selectedDevice.getFirstName() + " " + selectedDevice.getLastName());
        dialog.setContentText("Please scan or enter the NEW serial number:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newSerialRaw -> {
            String newSerial = newSerialRaw.trim().toUpperCase();
            if (newSerial.isEmpty() || newSerial.equals(selectedDevice.getSerialNumber())) return;

            if (stagedDeviceList.stream().anyMatch(d -> d.getSerialNumber().equals(newSerial))) {
                showAlert(Alert.AlertType.ERROR, "Duplicate Error", "Serial " + newSerial + " is already staged.");
                return;
            }

            Task<Optional<BulkDevice>> fetchTask = new Task<>() {
                @Override
                protected Optional<BulkDevice> call() throws Exception {
                    return dao.findDeviceBySerial(newSerial);
                }
            };

            fetchTask.setOnSucceeded(e -> {
                Optional<BulkDevice> newDeviceOpt = fetchTask.getValue();
                if (newDeviceOpt.isPresent()) {
                    RosterEntry originalRoster = new RosterEntry(selectedDevice.getFirstName(), selectedDevice.getLastName(), selectedDevice.getEmployeeEmail(), selectedDevice.getSnReferenceNumber(), selectedDevice.getDepotOrderNumber());
                    StagedDevice updatedStagedDevice = new StagedDevice(originalRoster, newDeviceOpt.get());
                    stagedDeviceList.set(stagingTable.getSelectionModel().getSelectedIndex(), updatedStagedDevice);
                    statusLabel.setText("Updated serial for " + selectedDevice.getLastName() + " to " + newSerial);
                } else {
                    showAlert(Alert.AlertType.ERROR, "Device Not Found", "Serial " + newSerial + " not found in the database.");
                }
            });
            fetchTask.setOnFailed(e -> showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to look up new serial: " + fetchTask.getException().getMessage()));

            new Thread(fetchTask).start();
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

        File templateFile = new File(getClass().getResource("/template/Device_Submission_Template.xlsx").getFile());

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
            showAlert(Alert.AlertType.INFORMATION, "Success", "Export complete and assignments saved.");
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