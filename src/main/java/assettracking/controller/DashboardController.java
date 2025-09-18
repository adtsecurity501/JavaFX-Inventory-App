package assettracking.controller;

import assettracking.dao.AppSettingsDAO;
import assettracking.data.TopModelStat;
import assettracking.db.DatabaseConnection;
import assettracking.manager.*;
import assettracking.ui.FlaggedDeviceImporter;
import assettracking.ui.MelRulesImporter;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DashboardController {

    // --- Services and DAO ---
    private final DashboardDataService dataService = new DashboardDataService();
    private final AppSettingsDAO appSettingsDAO = new AppSettingsDAO();
    // --- State and UI Helpers ---
    private final ObservableList<TopModelStat> topModelsList = FXCollections.observableArrayList();
    public Button manageFoldersButton;
    // --- FXML Fields ---
    @FXML
    private BarChart<String, Number> weeklyIntakeChart;
    @FXML
    private PieChart inventoryPieChart, deploymentBreakdownChart;
    @FXML
    private Label laptopsIntakenLabel, laptopsProcessedLabel, tabletsIntakenLabel, desktopsIntakenLabel, monitorsIntakenLabel;
    @FXML
    private Label tabletsProcessedLabel, desktopsProcessedLabel, monitorsProcessedLabel, totalProcessedLabel;
    @FXML
    private Label laptopsDisposedLabel, tabletsDisposedLabel, desktopsDisposedLabel, monitorsDisposedLabel;
    @FXML
    private Label activeTriageLabel, awaitingDisposalLabel, avgTurnaroundLabel, boxesAssembledLabel, labelsCreatedLabel;
    @FXML
    private Label deviceGoalPacingLabel, monitorGoalPacingLabel, timeRangeTitleLabel, breakdownTitleLabel;
    @FXML
    private Label inventoryOverviewTitleLabel, healthTitleLabel, pacingTitleLabel, topModelsTitleLabel;
    @FXML
    private ToggleGroup dateRangeToggleGroup;
    @FXML
    private RadioButton todayRadio, days7Radio;
    @FXML
    private TextField deviceGoalField, monitorGoalField;
    @FXML
    private GridPane mainGridPane;
    @FXML
    private ColumnConstraints leftColumn, rightColumn;
    @FXML
    private ScrollPane rightScrollPane;
    @FXML
    private TableView<TopModelStat> topModelsTable;
    @FXML
    private TableColumn<TopModelStat, String> modelNumberCol;
    @FXML
    private TableColumn<TopModelStat, Integer> modelCountCol;
    @FXML
    private Label statusLabel;
    @FXML
    private Label boxesAssembledTitleLabel, labelsCreatedTitleLabel;

    private double weeklyDeviceGoal = 100.0;
    private double weeklyMonitorGoal = 50.0;
    private ConfettiManager confettiManager;
    private boolean goalMetCelebrated = false;
    @FXML
    private Button importDeviceFilesButton;

    @FXML
    public void initialize() {
        setupTopModelsTable();
        setupUIListeners();
        loadInitialDataAsync();
    }

    public void init(StackPane rootPane) {
        this.confettiManager = new ConfettiManager(rootPane);
    }

    private void setupUIListeners() {
        dateRangeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> refreshAllData());
        mainGridPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSingleColumn = newVal.doubleValue() < 800;
            GridPane.setRowIndex(rightScrollPane, isSingleColumn ? 1 : 0);
            GridPane.setColumnIndex(rightScrollPane, isSingleColumn ? 0 : 1);
            leftColumn.setPercentWidth(isSingleColumn ? 100 : 60);
            rightColumn.setPercentWidth(isSingleColumn ? 0 : 40);
        });
    }

    private void setupTopModelsTable() {
        modelNumberCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().modelNumber()));
        modelCountCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().count()));
        topModelsTable.setItems(topModelsList);
    }

    private void loadInitialDataAsync() {
        Task<Void> initialLoadTask = new Task<>() {
            @Override
            protected Void call() { // Removed 'throws Exception'
                weeklyDeviceGoal = Double.parseDouble(appSettingsDAO.getSetting("device_goal").orElse("100.0"));
                weeklyMonitorGoal = Double.parseDouble(appSettingsDAO.getSetting("monitor_goal").orElse("50.0"));
                return null;
            }
        };

        initialLoadTask.setOnSucceeded(e -> {
            deviceGoalField.setText(String.valueOf((int) weeklyDeviceGoal));
            monitorGoalField.setText(String.valueOf((int) weeklyMonitorGoal));
            refreshAllData();
        });

        initialLoadTask.setOnFailed(e -> Platform.runLater(() -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Connection Failed", "Could not connect to the database. Please ensure the H2 server is running and the network is accessible.\n\nError: " + initialLoadTask.getException().getMessage())));

        new Thread(initialLoadTask).start();
    }

    @FXML
    private void refreshAllData() {
        DatabaseConnection.refreshConnectionPool();
        updateDynamicTitles();
        loadGranularMetrics();
        loadStaticKpis();
        loadDynamicCharts();
        loadTopModelsData();
    }

    private void loadGranularMetrics() {
        Task<Map<String, Integer>> task = new Task<>() {
            @Override
            protected Map<String, Integer> call() throws Exception {
                return dataService.getGranularMetrics(getDateFilterClause("p.receive_date"), getDateFilterClause("ds.last_update"));
            }
        };
        task.setOnSucceeded(e -> {
            Map<String, Integer> metrics = task.getValue();
            animateLabelUpdate(laptopsIntakenLabel, metrics.getOrDefault("laptopsIntaken", 0).toString());
            animateLabelUpdate(laptopsProcessedLabel, metrics.getOrDefault("laptopsProcessed", 0).toString());
            animateLabelUpdate(laptopsDisposedLabel, metrics.getOrDefault("laptopsDisposed", 0).toString());
            animateLabelUpdate(tabletsIntakenLabel, metrics.getOrDefault("tabletsIntaken", 0).toString());
            animateLabelUpdate(tabletsProcessedLabel, metrics.getOrDefault("tabletsProcessed", 0).toString());
            animateLabelUpdate(tabletsDisposedLabel, metrics.getOrDefault("tabletsDisposed", 0).toString());
            animateLabelUpdate(desktopsIntakenLabel, metrics.getOrDefault("desktopsIntaken", 0).toString());
            animateLabelUpdate(desktopsProcessedLabel, metrics.getOrDefault("desktopsProcessed", 0).toString());
            animateLabelUpdate(desktopsDisposedLabel, metrics.getOrDefault("desktopsDisposed", 0).toString());
            animateLabelUpdate(monitorsIntakenLabel, metrics.getOrDefault("monitorsIntaken", 0).toString());
            animateLabelUpdate(monitorsProcessedLabel, metrics.getOrDefault("monitorsProcessed", 0).toString());
            animateLabelUpdate(monitorsDisposedLabel, metrics.getOrDefault("monitorsDisposed", 0).toString());
            int totalDevicesProcessed = metrics.getOrDefault("laptopsProcessed", 0) + metrics.getOrDefault("tabletsProcessed", 0) + metrics.getOrDefault("desktopsProcessed", 0);
            int totalMonitorsProcessed = metrics.getOrDefault("monitorsProcessed", 0);
            animateLabelUpdate(totalProcessedLabel, String.valueOf(totalDevicesProcessed + totalMonitorsProcessed));
            updatePacing(totalDevicesProcessed, totalMonitorsProcessed);
            if (days7Radio.isSelected() && confettiManager != null && !goalMetCelebrated && (totalDevicesProcessed >= weeklyDeviceGoal || totalMonitorsProcessed >= weeklyMonitorGoal)) {
                confettiManager.start();
                goalMetCelebrated = true;
            }
        });
        task.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Dashboard Error", "Could not load performance metrics."));
        new Thread(task).start();
    }

    private void loadStaticKpis() {
        Task<Map<String, String>> task = new Task<>() {
            @Override
            protected Map<String, String> call() throws Exception {
                // This is the fix: We now get the date filter and pass it to the service.
                return dataService.getStaticKpis(getDateFilterClause("ds.last_update"));
            }
        };
        task.setOnSucceeded(e -> {
            Map<String, String> kpis = task.getValue();
            animateLabelUpdate(activeTriageLabel, kpis.getOrDefault("activeTriage", "0"));
            animateLabelUpdate(awaitingDisposalLabel, kpis.getOrDefault("awaitingDisposal", "0"));
            animateLabelUpdate(avgTurnaroundLabel, kpis.getOrDefault("avgTurnaround", "0.0 Days"));
            int boxes = Integer.parseInt(kpis.getOrDefault("boxesAssembled", "0"));
            animateLabelUpdate(boxesAssembledLabel, String.valueOf(boxes));
            animateLabelUpdate(labelsCreatedLabel, String.valueOf(boxes * 2));
        });
        task.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Dashboard Error", "Could not load static KPIs."));
        new Thread(task).start();
    }

    private void loadTopModelsData() {
        Task<List<TopModelStat>> task = new Task<>() {
            @Override
            protected List<TopModelStat> call() throws Exception {
                return dataService.getTopModels(getDateFilterClause("ds.last_update"));
            }
        };
        task.setOnSucceeded(e -> topModelsList.setAll(task.getValue()));
        task.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Error", "Could not load Top Processed Models data."));
        new Thread(task).start();
    }

    @SuppressWarnings("unchecked")
    private void loadDynamicCharts() {
        Task<List<PieChart.Data>> inventoryTask = new Task<>() {
            @Override
            protected List<PieChart.Data> call() throws Exception {
                return dataService.getInventoryOverviewData();
            }
        };
        inventoryTask.setOnSucceeded(e -> setPieChartData(inventoryPieChart, FXCollections.observableArrayList(inventoryTask.getValue())));
        new Thread(inventoryTask).start();
        Task<List<PieChart.Data>> breakdownTask = new Task<>() {
            @Override
            protected List<PieChart.Data> call() throws Exception {
                return dataService.getProcessedBreakdownData(getDateFilterClause("ds.last_update"));
            }
        };
        breakdownTask.setOnSucceeded(e -> setPieChartData(deploymentBreakdownChart, FXCollections.observableArrayList(breakdownTask.getValue())));
        new Thread(breakdownTask).start();
        Task<XYChart.Series<String, Number>> intakeTask = new Task<>() {
            @Override
            protected XYChart.Series<String, Number> call() throws Exception {
                return dataService.getIntakeVolumeData(getDateFilterClause("p.receive_date"));
            }
        };
        intakeTask.setOnSucceeded(e -> weeklyIntakeChart.getData().setAll(intakeTask.getValue()));
        new Thread(intakeTask).start();
    }

    private void updatePacing(int deviceCount, int monitorCount) {
        if (weeklyDeviceGoal > 0) {
            animateLabelUpdate(deviceGoalPacingLabel, String.format("%.1f%%", (deviceCount / weeklyDeviceGoal) * 100));
        } else {
            deviceGoalPacingLabel.setText("N/A");
        }
        if (weeklyMonitorGoal > 0) {
            animateLabelUpdate(monitorGoalPacingLabel, String.format("%.1f%%", (monitorCount / weeklyMonitorGoal) * 100));
        } else {
            monitorGoalPacingLabel.setText("N/A");
        }
    }

    private String getDateFilterClause(String columnName) {
        // This now wraps the column name in a function that casts it to a date.
        // This works for both p.receive_date and ds.last_update
        String dateColumn = String.format("CAST(%s AS DATE)", columnName);

        RadioButton selected = (RadioButton) dateRangeToggleGroup.getSelectedToggle();
        if (selected == null || selected.getText().equals("Last 7 Days")) { // Default case
            return String.format(" %s >= DATEADD('DAY', -7, CURRENT_DATE)", dateColumn);
        }
        if (selected.getText().equals("Today")) {
            return String.format(" %s >= CURRENT_DATE", dateColumn);
        }
        // Last 30 Days
        return String.format(" %s >= DATEADD('DAY', -30, CURRENT_DATE)", dateColumn);
    }

    private void updateDynamicTitles() {
        RadioButton selected = (RadioButton) dateRangeToggleGroup.getSelectedToggle();
        String timeSuffix;
        String pacingTimeSuffix; // Suffix without parentheses for the titles

        if (selected == null || selected.getText().equals("Last 7 Days")) { // Default case
            timeRangeTitleLabel.setText("Metrics for Last 7 Days");
            pacingTitleLabel.setText("Weekly Pacing (Refurbished Only)");
            timeSuffix = "(Last 7 Days)";
            pacingTimeSuffix = "Last 7 Days"; // New
        } else if (selected.getText().equals("Today")) {
            timeRangeTitleLabel.setText("Metrics for Today");
            pacingTitleLabel.setText("Daily Pacing (Refurbished Only)");
            timeSuffix = "(Today)";
            pacingTimeSuffix = "Today"; // New
        } else { // Last 30 Days
            timeRangeTitleLabel.setText("Metrics for Last 30 Days");
            pacingTitleLabel.setText("Monthly Pacing (Refurbished Only)");
            timeSuffix = "(Last 30 Days)";
            pacingTimeSuffix = "Last 30 Days"; // New
        }

        // Update the main titles
        healthTitleLabel.setText("Overall Health " + timeSuffix);
        breakdownTitleLabel.setText("Processed Breakdown " + timeSuffix);
        topModelsTitleLabel.setText("Top Processed Models " + timeSuffix);
        inventoryOverviewTitleLabel.setText("Asset Status Overview (All Time)");

        // --- ADD THESE TWO LINES TO UPDATE THE PACING TITLES ---
        boxesAssembledTitleLabel.setText("Boxes Assembled " + pacingTimeSuffix);
        labelsCreatedTitleLabel.setText("Labels Created " + pacingTimeSuffix);
    }

    private void setPieChartData(PieChart chart, ObservableList<PieChart.Data> data) {
        double total = data.stream().mapToDouble(PieChart.Data::getPieValue).sum();
        if (total == 0) {
            data.clear();
            PieChart.Data noDataSlice = new PieChart.Data("No Data", 1);
            data.add(noDataSlice);
            chart.setData(data);
            Platform.runLater(() -> {
                if (noDataSlice.getNode() != null) noDataSlice.getNode().setVisible(false);
            });
            return;
        }
        data.forEach(d -> {
            String originalName = d.getName();
            if (originalName != null && !originalName.contains("%")) {
                double percentage = (d.getPieValue() / total) * 100;
                d.setName(String.format("%s\n%d (%.1f%%)", originalName, (int) d.getPieValue(), percentage));
            }
        });
        chart.setData(data);
    }

    private Optional<List<String>> showFolderManagementDialog(List<String> initialFolders) {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Manage Import Folders");
        dialog.setHeaderText("Add or remove folders that the application will scan for device files.");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ListView<String> listView = new ListView<>();
        listView.getItems().setAll(initialFolders);

        Button addButton = new Button("Add Folder...");
        Button removeButton = new Button("Remove Selected");
        removeButton.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> removeButton.setDisable(newVal == null));

        addButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select a Folder to Scan");
            File selected = chooser.showDialog(getStage());
            if (selected != null) {
                listView.getItems().add(selected.getAbsolutePath());
            }
        });

        removeButton.setOnAction(e -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                listView.getItems().remove(selected);
            }
        });

        HBox buttonBox = new HBox(10, addButton, removeButton);
        VBox content = new VBox(10, listView, buttonBox);
        dialogPane.setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new ArrayList<>(listView.getItems());
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private void animateLabelUpdate(Label label, String newValue) {
        if (label == null || label.getText().equals(newValue)) return;
        label.setText(newValue);
        ScaleTransition st = new ScaleTransition(Duration.millis(150), label);
        st.setFromX(1);
        st.setFromY(1);
        st.setToX(1.3);
        st.setToY(1.3);
        st.setCycleCount(2);
        st.setAutoReverse(true);
        st.play();
    }

    @FXML
    private void handleManageFolders() {
        AppSettingsDAO settingsDAO = new AppSettingsDAO();
        final String FOLDERS_KEY = "bulk.import.scan.folders";

        List<String> currentFolders = new ArrayList<>(settingsDAO.getSetting(FOLDERS_KEY).map(paths -> Arrays.asList(paths.split(","))).orElse(List.of()));
        Optional<List<String>> updatedFoldersOpt = showFolderManagementDialog(currentFolders);

        updatedFoldersOpt.ifPresent(updatedFolders -> {
            String pathsToSave = String.join(",", updatedFolders);
            settingsDAO.saveSetting(FOLDERS_KEY, pathsToSave);
            StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Settings Saved", "Your import folder list has been updated.");
        });
    }

    @FXML
    private void handleImportAutofill() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/AutofillImportDialog.fxml"));
            Parent root = loader.load();
            Stage stage = StageManager.createCustomStage(getStage(), "Bulk Import Autofill Data", root);
            stage.showAndWait();
        } catch (IOException e) {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Error", "Could not open the import dialog: " + e.getMessage());
        }
    }

    @FXML
    private void handleImportDeviceFiles() {
        AppSettingsDAO settingsDAO = new AppSettingsDAO();
        final String FOLDERS_KEY = "bulk.import.scan.folders";
        Optional<String> savedPathsOpt = settingsDAO.getSetting(FOLDERS_KEY);

        if (savedPathsOpt.isEmpty() || savedPathsOpt.get().isBlank()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Setup Required", "Please configure the import folders first using the 'Manage Import Folders' button.");
            return;
        }

        List<String> foldersToScan = new ArrayList<>(Arrays.asList(savedPathsOpt.get().split(",")));
        DeviceImportService importService = new DeviceImportService();

        Task<List<ImportResult>> importTask = new Task<>() {
            @Override
            protected List<ImportResult> call() throws IOException {
                return importService.runFolderImport(foldersToScan, this::updateMessage);
            }
        };

        importTask.messageProperty().addListener((obs, oldMsg, newMsg) -> statusLabel.setText(newMsg));
        importDeviceFilesButton.setDisable(true);
        statusLabel.setText("Starting import...");

        importTask.setOnSucceeded(e -> {
            importDeviceFilesButton.setDisable(false);
            List<ImportResult> results = importTask.getValue();

            if (results.isEmpty()) {
                statusLabel.setText("Import finished: No new files were found to process.");
                return;
            }

            StringBuilder summary = new StringBuilder("Import Complete:\n\n");
            int totalSuccess = results.stream().mapToInt(ImportResult::successfulCount).sum();
            long totalErrors = results.stream().mapToLong(r -> r.errors().size()).sum();

            summary.append(String.format("Successfully processed: %d records\n", totalSuccess));
            summary.append(String.format("Rejected records: %d\n", totalErrors));

            List<String> topErrors = results.stream().flatMap(r -> r.errors().stream()).limit(10).toList();

            if (!topErrors.isEmpty()) {
                summary.append("\nTop Reasons for Rejection:\n");
                topErrors.forEach(err -> summary.append(String.format("- %s\n", err)));
            }

            String logMessage = logImportErrors(results);
            summary.append(logMessage);

            statusLabel.setText(String.format("Import finished. Processed: %d, Rejected: %d. Refreshing dashboard...", totalSuccess, totalErrors));
            StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Import Results", summary.toString());

            Platform.runLater(this::refreshAllData);
        });

        importTask.setOnFailed(e -> {
            importDeviceFilesButton.setDisable(false);
            Throwable ex = importTask.getException();
            statusLabel.setText("Import failed. See error dialog.");
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Import Failed", "A critical error occurred: " + ex.getMessage());
        });

        new Thread(importTask).start();
    }


    @FXML
    private void handleImportFlags() {
        new FlaggedDeviceImporter().importFromFile(getStage(), this::refreshAllData);
    }

    @FXML
    private void handleManageMelRules() {
        new MelRulesImporter().importFromFile(getStage());
    }

    private String logImportErrors(List<ImportResult> results) {
        List<String> allErrors = results.stream().flatMap(r -> r.errors().stream()).toList();

        if (allErrors.isEmpty()) {
            return "";
        }

        try {
            Path logDir = Paths.get(System.getProperty("user.home"), ".asset_tracker_logs");
            Files.createDirectories(logDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path logFile = logDir.resolve("import_errors_" + timestamp + ".txt");

            try (PrintWriter writer = new PrintWriter(logFile.toFile())) {
                writer.println("Asset Tracking Import Error Log - " + LocalDateTime.now());
                writer.println("=======================================================");
                for (ImportResult result : results) {
                    if (!result.errors().isEmpty()) {
                        writer.printf("\nErrors for file: %s\n", result.file().getName());
                        writer.println("----------------------------------------");
                        result.errors().forEach(writer::println);
                    }
                }
            }
            return String.format("\nA full report of all %d rejected records has been saved to:\n%s", allErrors.size(), logFile.toAbsolutePath());
        } catch (IOException e) {
            return "\nCould not write full error log to file due to an error: " + e.getMessage();
        }
    }

    @FXML
    private void handleApplyGoals() {
        try {
            weeklyDeviceGoal = Double.parseDouble(deviceGoalField.getText());
            appSettingsDAO.saveSetting("device_goal", String.valueOf(weeklyDeviceGoal));
        } catch (NumberFormatException e) {
            deviceGoalField.setText(String.valueOf((int) weeklyDeviceGoal));
        }
        try {
            weeklyMonitorGoal = Double.parseDouble(monitorGoalField.getText());
            appSettingsDAO.saveSetting("monitor_goal", String.valueOf(weeklyMonitorGoal));
        } catch (NumberFormatException e) {
            monitorGoalField.setText(String.valueOf((int) weeklyMonitorGoal));
        }
        goalMetCelebrated = false;
        refreshAllData();
    }


    private Stage getStage() {
        return (Stage) mainGridPane.getScene().getWindow();
    }
}