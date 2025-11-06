package assettracking.controller;

import assettracking.dao.AppSettingsDAO;
import assettracking.data.ImagingResult;
import assettracking.manager.DesktopNotifier;
import assettracking.manager.MachineRemovalService;
import assettracking.manager.StageManager;
import assettracking.service.ImagingEmailService;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ImagingStatusController {

    private static final String FOLDER_KEY = "imaging.outlook.folder";
    private static final String IP_KEY = "imaging.filter.ip";
    private static final String REFRESH_KEY = "imaging.refresh.interval";
    private static final String SUBJECT_KEY = "imaging.subject.filter";
    private static final String COMP_NAME_KEY = "imaging.keyword.comp_name";
    private static final String SERIAL_KEY = "imaging.keyword.serial";
    private static final String TIME_KEY = "imaging.keyword.time";
    private static final String FAILED_KEY = "imaging.keyword.failed";

    private final ObservableList<ImagingResult> imagingResults = FXCollections.observableArrayList();
    private final AppSettingsDAO settingsDAO = new AppSettingsDAO();
    private final MachineRemovalService machineRemovalService = new MachineRemovalService();
    private final ImagingEmailService emailService = new ImagingEmailService();
    private int refreshIntervalMinutes = 5;

    private Timeline autoRefreshTimeline;
    @FXML
    private ToggleButton autoRefreshToggle;
    @FXML
    private RadioButton unreadModeRadio;
    @FXML
    private DatePicker datePicker;
    @FXML
    private ToggleGroup searchModeToggleGroup;
    @FXML
    private Button checkEmailsButton;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private TextArea logTextArea;
    @FXML
    private TableView<ImagingResult> resultsTable;
    @FXML
    private TableColumn<ImagingResult, String> computerNameCol;
    @FXML
    private TableColumn<ImagingResult, String> serialNumberCol;
    @FXML
    private TableColumn<ImagingResult, String> reimageTimeCol;
    @FXML
    private TableColumn<ImagingResult, String> failedInstallsCol;
    @FXML
    private ListView<String> adResultsList;
    @FXML
    private RadioButton rangeModeRadio; // New RadioButton
    @FXML
    private RadioButton dateModeRadio;
    @FXML
    private HBox datePickerBox;          // New HBox container
    @FXML
    private DatePicker endDatePicker;        // New DatePicker
    @FXML
    private Label toLabel;               // New Label

    @FXML
    public void initialize() {
        loadSettings();
        setupAutoRefresh();
        computerNameCol.setCellValueFactory(new PropertyValueFactory<>("computerName"));
        serialNumberCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        reimageTimeCol.setCellValueFactory(new PropertyValueFactory<>("reimageTime"));
        failedInstallsCol.setCellValueFactory(new PropertyValueFactory<>("failedInstalls"));
        resultsTable.setItems(imagingResults);
        setupRowFactoryForCopy();
        if (datePicker.getParent() instanceof HBox) {
            datePickerBox = (HBox) datePicker.getParent();
            toLabel = new Label("To:");
            endDatePicker = new DatePicker(LocalDate.now());

            // Add them to the layout, but keep them hidden initially
            datePickerBox.getChildren().addAll(toLabel, endDatePicker);
            toLabel.setVisible(false);
            toLabel.setManaged(false);
            endDatePicker.setVisible(false);
            endDatePicker.setManaged(false);
        }

        datePicker.setValue(LocalDate.now());

        searchModeToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            boolean isDateMode = newToggle == dateModeRadio;
            boolean isRangeMode = newToggle == rangeModeRadio;

            datePicker.setVisible(isDateMode || isRangeMode);
            datePicker.setManaged(isDateMode || isRangeMode);

            // Show the "To" label and end date picker only for range mode
            toLabel.setVisible(isRangeMode);
            toLabel.setManaged(isRangeMode);
            endDatePicker.setVisible(isRangeMode);
            endDatePicker.setManaged(isRangeMode);
        });
        resultsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !"Not Found".equals(newVal.getSerialNumber())) {
                checkForDuplicates(newVal.getSerialNumber());
            } else {
                adResultsList.getItems().clear();
            }
        });
    }

    @FXML
    private void handleCheckEmails() {
        if (!isOutlookRunning()) {
            StageManager.showAlert(resultsTable.getScene().getWindow(), Alert.AlertType.WARNING, "Outlook is Not Running", "Please open the classic Outlook desktop application before checking for emails.");
            if (autoRefreshTimeline.getStatus() == Animation.Status.RUNNING) {
                autoRefreshToggle.setSelected(false);
            }
            return;
        }

        String folderPath = settingsDAO.getSetting(FOLDER_KEY).orElse("");
        if (folderPath.isEmpty()) {
            statusLabel.setText("Error: Outlook Folder Path is not set. Please configure it in Settings.");
            if (autoRefreshTimeline.getStatus() == Animation.Status.RUNNING) {
                autoRefreshToggle.setSelected(false);
            }
            return;
        }

        checkEmailsButton.setDisable(true);
        logTextArea.clear();
        appendToLog("Starting email check process...");
        statusLabel.setText("Checking for emails...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1.0);
        adResultsList.getItems().clear();

        // --- THIS IS THE CORRECTED LOGIC ---
        List<String> command = new ArrayList<>();
        command.add(folderPath);

        // Only add flags if they have a value
        String subjectFilter = settingsDAO.getSetting(SUBJECT_KEY).orElse("").trim();
        if (!subjectFilter.isEmpty()) {
            command.add("--subject_filter");
            command.add(subjectFilter);
        }

        String ipFilter = settingsDAO.getSetting(IP_KEY).orElse("").trim();
        if (!ipFilter.isEmpty()) {
            command.add("--ip_filter");
            command.add(ipFilter);
        }

        if (unreadModeRadio.isSelected()) {
            command.add("--search_mode");
            command.add("UNREAD");
        } else {
            LocalDate startDate = datePicker.getValue();
            if (startDate == null) {
                statusLabel.setText("Error: Please select a start date.");
                // reset UI and return
                return;
            }

            if (rangeModeRadio.isSelected()) {
                LocalDate endDate = endDatePicker.getValue();
                if (endDate == null) {
                    statusLabel.setText("Error: Please select an end date for the range.");
                    // reset UI and return
                    return;
                }
                command.add("--search_mode");
                command.add("RANGE");
                command.add("--start_date");
                command.add(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                command.add("--end_date");
                command.add(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            } else { // Single Date mode
                command.add("--search_mode");
                command.add("DATE");
                command.add("--start_date");
                command.add(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }

        // Add keyword arguments (these have defaults in the Python script, so they are safe)
        command.add("--kw_comp_name");
        command.add(settingsDAO.getSetting(COMP_NAME_KEY).orElse("Computer Name:"));
        command.add("--kw_serial");
        command.add(settingsDAO.getSetting(SERIAL_KEY).orElse("Serial Number:"));
        command.add("--kw_time");
        command.add(settingsDAO.getSetting(TIME_KEY).orElse("Time to reimage:"));
        command.add("--kw_failed");
        command.add(settingsDAO.getSetting(FAILED_KEY).orElse("items failed to install:"));

        CompletableFuture<List<ImagingResult>> future = emailService.fetchAndParseEmails(command);

        future.thenAccept(results -> Platform.runLater(() -> {
            if (results != null && !results.isEmpty()) {
                imagingResults.addAll(0, results);
                String title = "New Imaging Results";
                String message = "Successfully processed " + results.size() + " new email(s).";
                statusLabel.setText(message);
                DesktopNotifier.showNotification(title, message);
            } else {
                statusLabel.setText("No new imaging emails found matching the criteria.");
            }
        })).whenComplete((v, throwable) -> Platform.runLater(() -> {
            checkEmailsButton.setDisable(false);
            progressBar.setVisible(false);
        }));
        // --- END OF CORRECTED LOGIC ---
    }

    // --- The rest of the file is unchanged ---

    private void setupRowFactoryForCopy() {
        resultsTable.setRowFactory(tv -> {
            TableRow<ImagingResult> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();
            MenuItem copyMenuItem = new MenuItem("Copy Row for Excel");
            copyMenuItem.setOnAction(event -> {
                ImagingResult item = row.getItem();
                if (item != null) {
                    copySingleRowToClipboard(item);
                }
            });
            contextMenu.getItems().add(copyMenuItem);
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                        copySingleRowToClipboard(row.getItem());
                    } else if (event.getButton() == MouseButton.SECONDARY) {
                        contextMenu.show(row, event.getScreenX(), event.getScreenY());
                    }
                }
            });
            return row;
        });
    }

    private void copySingleRowToClipboard(ImagingResult result) {
        if (result == null) return;
        String excelFormattedString = String.join("\t", "", result.getSerialNumber(), "", "", result.getComputerName(), result.getReimageTime(), result.getFailedInstalls());
        final ClipboardContent content = new ClipboardContent();
        content.putString(excelFormattedString);
        Clipboard.getSystemClipboard().setContent(content);
        statusLabel.setText("Copied row for S/N: " + result.getSerialNumber());
    }

    @FXML
    private void handleCopyResults() {
        ImagingResult selectedItem = resultsTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            statusLabel.setText("Please select a row to copy.");
            return;
        }
        copySingleRowToClipboard(selectedItem);
    }

    private void loadSettings() {
        try {
            refreshIntervalMinutes = Integer.parseInt(settingsDAO.getSetting(REFRESH_KEY).orElse("5"));
        } catch (NumberFormatException e) {
            refreshIntervalMinutes = 5;
        }
    }

    private void setupAutoRefresh() {
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.minutes(refreshIntervalMinutes), e -> handleCheckEmails()));
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        autoRefreshToggle.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                autoRefreshToggle.setText("Auto-Refresh: ON");
                autoRefreshTimeline.play();
                statusLabel.setText("Automatic email check enabled (every " + refreshIntervalMinutes + " minutes).");
            } else {
                autoRefreshToggle.setText("Auto-Refresh: OFF");
                autoRefreshTimeline.stop();
                statusLabel.setText("Automatic email check disabled.");
            }
        });
    }

    private void appendToLog(String message) {
        Platform.runLater(() -> logTextArea.appendText(message + "\n"));
    }

    private boolean isOutlookRunning() {
        try {
            Process process = new ProcessBuilder("tasklist.exe", "/fi", "imagename eq OUTLOOK.EXE").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines().anyMatch(line -> line.contains("OUTLOOK.EXE"));
            }
        } catch (IOException e) {
            appendToLog("WARN: Could not check if Outlook is running. Assuming it is.");
            return true;
        }
    }

    @FXML
    private void handleOpenSettings() {
        try {
            boolean wasRunning = autoRefreshTimeline.getStatus() == Animation.Status.RUNNING;
            if (wasRunning) autoRefreshTimeline.stop();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ImagingSettingsDialog.fxml"));
            Parent root = loader.load();
            ImagingSettingsController controller = loader.getController();
            Stage stage = StageManager.createCustomStage(resultsTable.getScene().getWindow(), "Imaging Settings", root);
            stage.showAndWait();
            if (controller.isSaved()) {
                loadSettings();
                statusLabel.setText("Settings have been updated.");
                if (wasRunning) {
                    autoRefreshTimeline = new Timeline(new KeyFrame(Duration.minutes(refreshIntervalMinutes), e -> handleCheckEmails()));
                    autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
                    autoRefreshToggle.setSelected(true);
                }
            } else {
                if (wasRunning) autoRefreshTimeline.play();
            }
        } catch (IOException e) {
            statusLabel.setText("Error: Could not open settings window.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleClearResults() {
        imagingResults.clear();
        adResultsList.getItems().clear();
        statusLabel.setText("Results cleared.");
    }

    @FXML
    private void handleClearLog() {
        logTextArea.clear();
    }

    private void checkForDuplicates(String serialNumber) {
        adResultsList.getItems().clear();
        adResultsList.getItems().add("Searching AD/SCCM for serial: " + serialNumber + "...");
        machineRemovalService.search(List.of(serialNumber)).thenAccept(adResults -> Platform.runLater(() -> {
            adResultsList.getItems().clear();
            if (adResults.isEmpty()) {
                adResultsList.getItems().add("OK: Serial number not found in AD or SCCM.");
            } else {
                adResultsList.getItems().add("WARNING: Found " + adResults.size() + " match(es)!");
                adResults.forEach(r -> adResultsList.getItems().add(String.format("- %s (from %s)", r.computerName(), r.source())));
            }
        }));
    }
}