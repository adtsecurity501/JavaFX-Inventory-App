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
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

    // Track whether we’ve already done an initial load
    private boolean initialLoadDone = false;

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
    private TableColumn<ImagingResult, String> receivedTimeCol;

    @FXML
    private ListView<String> adResultsList;

    @FXML
    private RadioButton rangeModeRadio;
    @FXML
    private RadioButton dateModeRadio;
    @FXML
    private HBox datePickerBox;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private Label toLabel;

    @FXML
    public void initialize() {
        loadSettings();
        setupAutoRefresh();

        computerNameCol.setCellValueFactory(new PropertyValueFactory<>("computerName"));
        serialNumberCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        reimageTimeCol.setCellValueFactory(new PropertyValueFactory<>("reimageTime"));

        // Pretty display for failed installs
        failedInstallsCol.setCellValueFactory(new PropertyValueFactory<>("failedInstalls"));
        failedInstallsCol.setCellFactory(col -> new TableCell<ImagingResult, String>() {
            private final Text text = new Text();
            private final Tooltip tip = new Tooltip();

            {
                text.wrappingWidthProperty().bind(col.widthProperty().subtract(12));
                setGraphic(text);
                setPrefHeight(Control.USE_COMPUTED_SIZE);
                setStyle("-fx-alignment: TOP-LEFT;");

                tip.setWrapText(true);
                tip.setMaxWidth(600);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null || item.isBlank() || item.trim().equals("0")) {
                    text.setText("0");
                    setTooltip(null);
                    return;
                }

                // split on semicolons OR newlines (defensive)
                List<String> parts = Arrays.stream(item.split("\\s*;\\s*|\\r?\\n")).map(String::trim).filter(s -> !s.isBlank()).toList();

                // what we show in the cell (cap to avoid huge rows)
                int maxVisible = 3;
                String cellText;
                if (parts.size() <= maxVisible) {
                    cellText = parts.stream().map(p -> "• " + p).collect(Collectors.joining("\n"));
                } else {
                    cellText = parts.subList(0, maxVisible).stream().map(p -> "• " + p).collect(Collectors.joining("\n")) + "\n• +" + (parts.size() - maxVisible) + " more...";
                }

                text.setText(cellText);

                // tooltip always shows full list
                tip.setText(parts.stream().map(p -> "• " + p).collect(Collectors.joining("\n")));
                setTooltip(tip);
            }
        });

        receivedTimeCol.setCellValueFactory(new PropertyValueFactory<>("receivedTime"));

        resultsTable.setItems(imagingResults);
        setupRowFactoryForCopy();

        // Range mode UI wiring
        if (datePicker.getParent() instanceof HBox) {
            datePickerBox = (HBox) datePicker.getParent();
            toLabel = new Label("To:");
            endDatePicker = new DatePicker(LocalDate.now());

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
        boolean isAutoRefresh = !Platform.isFxApplicationThread();

        if (!isOutlookRunning()) {
            StageManager.showAlert(resultsTable.getScene().getWindow(), Alert.AlertType.WARNING, "Outlook is Not Running", "Please open the classic Outlook desktop application before checking for emails.");
            if (isAutoRefresh) autoRefreshToggle.setSelected(false);
            return;
        }

        String folderPath = settingsDAO.getSetting(FOLDER_KEY).orElse("");
        if (folderPath.isEmpty()) {
            statusLabel.setText("Error: Outlook Folder Path is not set. Please configure it in Settings.");
            if (isAutoRefresh) autoRefreshToggle.setSelected(false);
            return;
        }

        checkEmailsButton.setDisable(true);
        if (!isAutoRefresh) logTextArea.clear();

        appendToLog(isAutoRefresh ? "Auto-Refresh triggered..." : "Manual email check started...");
        statusLabel.setText("Checking for emails...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1.0);

        List<String> command = new ArrayList<>();
        command.add(folderPath);

        settingsDAO.getSetting(SUBJECT_KEY).filter(s -> !s.isBlank()).ifPresent(s -> {
            command.add("--subject_filter");
            command.add(s);
        });

        settingsDAO.getSetting(IP_KEY).filter(s -> !s.isBlank()).ifPresent(s -> {
            command.add("--ip_filter");
            command.add(s);
        });

        // --- SEARCH MODE + DATE PARAMS ---
        if (isAutoRefresh || unreadModeRadio.isSelected()) {
            command.add("--search_mode");
            command.add("UNREAD");
            if (isAutoRefresh) appendToLog("Auto-Refresh: Forcing search mode to 'UNREAD'.");
        } else if (rangeModeRadio.isSelected()) {
            LocalDate startDate = datePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            if (startDate == null || endDate == null) {
                resetUiOnError("Please select a start and end date for the range search.");
                return;
            }
            command.add("--search_mode");
            command.add("RANGE");
            command.add("--start_date");
            command.add(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            command.add("--end_date");
            command.add(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        } else {
            LocalDate date = datePicker.getValue();
            if (date == null) {
                resetUiOnError("Please select a date for the search.");
                return;
            }
            command.add("--search_mode");
            command.add("DATE");
            command.add("--start_date");
            command.add(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        command.add("--kw_serial");
        command.add(settingsDAO.getSetting(SERIAL_KEY).orElse("Serial Number"));
        command.add("--kw_time");
        command.add(settingsDAO.getSetting(TIME_KEY).orElse("Job Total Run Time"));
        command.add("--kw_failed");
        command.add(settingsDAO.getSetting(FAILED_KEY).orElse("NOTINSTALLED"));

        CompletableFuture<List<ImagingResult>> future = emailService.fetchAndParseEmails(command);

        future.thenAccept(results -> Platform.runLater(() -> {

            boolean firstLoad = !initialLoadDone;
            initialLoadDone = true;

            if (results == null || results.isEmpty()) {
                statusLabel.setText("No imaging emails found matching the criteria.");
                return;
            }

            Set<String> existingSerials = imagingResults.stream().map(ImagingResult::getSerialNumber).collect(Collectors.toSet());

            List<ImagingResult> newUniqueResults = results.stream().filter(r -> !existingSerials.contains(r.getSerialNumber())).toList();

            if (newUniqueResults.isEmpty()) {
                statusLabel.setText("No new imaging emails found matching the criteria.");
                return;
            }

            imagingResults.addAll(0, newUniqueResults);

            // ---- TOAST LOGIC ----
            if (firstLoad) {
                String msg = newUniqueResults.size() + " computers are ready";
                statusLabel.setText("Loaded " + newUniqueResults.size() + " imaging result(s).");
                DesktopNotifier.showNotification("Imaging Results Loaded", msg);
            } else {
                if (newUniqueResults.size() == 1) {
                    ImagingResult r = newUniqueResults.get(0);
                    String msg = r.getComputerName() + " is finished";
                    statusLabel.setText("New imaging result: " + r.getComputerName());
                    DesktopNotifier.showNotification("Imaging Finished", msg);
                } else {
                    String msg = newUniqueResults.size() + " computers are ready";
                    statusLabel.setText("Successfully processed " + newUniqueResults.size() + " new email(s).");
                    DesktopNotifier.showNotification("New Imaging Results", msg);
                }
            }

        })).whenComplete((v, throwable) -> Platform.runLater(() -> {
            checkEmailsButton.setDisable(false);
            progressBar.setVisible(false);
        }));
    }

    private void resetUiOnError(String message) {
        statusLabel.setText("Error: " + message);
        checkEmailsButton.setDisable(false);
        progressBar.setVisible(false);
    }

    private void setupRowFactoryForCopy() {
        resultsTable.setRowFactory(tv -> {
            TableRow<ImagingResult> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();
            MenuItem copyMenuItem = new MenuItem("Copy Row for Excel");

            copyMenuItem.setOnAction(event -> {
                ImagingResult item = row.getItem();
                if (item != null) copySingleRowToClipboard(item);
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

        String failed = result.getFailedInstalls();

        // If no failed apps, don't copy "0"
        if (failed == null || failed.isBlank() || failed.equals("0") || failed.toLowerCase().startsWith("0 items")) {
            failed = "";
        }

        String excelFormattedString = String.join("\t", result.getComputerName(), result.getReimageTime(), failed);

        ClipboardContent content = new ClipboardContent();
        content.putString(excelFormattedString);
        Clipboard.getSystemClipboard().setContent(content);

        statusLabel.setText("Copied row for: " + result.getComputerName());
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

    private String formatFailedInstalls(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty() || s.equals("0")) return "0";

        // Split on semicolons OR newlines
        String[] parts = s.split("\\s*(?:;|\\r?\\n)+\\s*");

        List<String> cleaned = Arrays.stream(parts).map(String::trim).filter(p -> !p.isBlank())
                // extra safety to strip any leftover noise
                .filter(p -> !p.equalsIgnoreCase("NOTINSTALLED")).filter(p -> !p.matches("[0-9a-fA-F-]{8,}")).filter(p -> !p.toLowerCase().startsWith("application")).toList();

        if (cleaned.isEmpty()) return s;
        if (cleaned.size() == 1) return cleaned.get(0);

        return cleaned.stream().map(p -> "• " + p).collect(Collectors.joining("\n"));
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
