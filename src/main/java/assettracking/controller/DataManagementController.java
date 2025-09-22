package assettracking.controller;

import assettracking.dao.AppSettingsDAO;
import assettracking.dao.DeviceStatusDAO;
import assettracking.manager.*;
import assettracking.ui.FlaggedDeviceImporter;
import assettracking.ui.MelRulesImporter;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class DataManagementController {

    private static final Logger logger = LoggerFactory.getLogger(DataManagementController.class);
    @FXML
    private Button bulkIntakeButton;
    @FXML
    private Button runAutoImportButton;
    @FXML
    private Label statusLabel;
    @FXML
    private TextField deviceGoalField;
    @FXML
    private TextField monitorGoalField;

    private DeviceStatusDAO deviceStatusDAO;
    private AppSettingsDAO appSettingsDAO;
    private DeviceImportService deviceImportService;

    @FXML
    public void initialize() {
        this.deviceStatusDAO = new DeviceStatusDAO(null, null);
        this.appSettingsDAO = new AppSettingsDAO();
        this.deviceImportService = new DeviceImportService();

    }

    @FXML
    private void handleApplyGoals() {
        try {
            double deviceGoal = Double.parseDouble(deviceGoalField.getText());
            appSettingsDAO.saveSetting("device_goal", String.valueOf(deviceGoal));

            double monitorGoal = Double.parseDouble(monitorGoalField.getText());
            appSettingsDAO.saveSetting("monitor_goal", String.valueOf(monitorGoal));

            statusLabel.setText("Dashboard goals have been updated. Refresh the Dashboard to see the new pacing.");
            StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Goals Updated", "Dashboard goals have been saved.");

        } catch (NumberFormatException e) {
            statusLabel.setText("Error: Goals must be valid numbers.");
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Invalid Input", "Goals must be valid numbers.");
        }
    }

    @FXML
    private void handleExport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Asset Report");
        FileChooser.ExtensionFilter xlsxFilter = new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx");
        FileChooser.ExtensionFilter csvFilter = new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv");
        fileChooser.getExtensionFilters().addAll(xlsxFilter, csvFilter);
        File file = fileChooser.showSaveDialog(getStage());

        if (file != null) {
            final Window owner = getStage();
            String extension = fileChooser.getSelectedExtensionFilter().getExtensions().getFirst();

            Task<Void> exportTask = new Task<>() {
                @Override
                protected Void call() {
                    if (extension.equals("*.xlsx")) {
                        new ReportingService().exportToXLSX(file, owner);
                    } else {
                        new ReportingService().exportToCSV(file, owner);
                    }
                    return null;
                }
            };

            exportTask.setOnSucceeded(e -> statusLabel.setText("Export complete: " + file.getName()));
            exportTask.setOnFailed(e -> {
                statusLabel.setText("Export failed. See logs for details.");
                Throwable ex = e.getSource().getException();
                logger.error("Export task failed", ex);
                StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Export Failed", "A critical error occurred during export: " + ex.getMessage());
            });

            MainViewController.getInstance().bindProgressBar(exportTask);
            new Thread(exportTask).start();
        }
    }

    @FXML
    private void handleManageFlags() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/FlagManagementDialog.fxml"));
            Parent root = loader.load();
            FlagManagementDialogController dialogController = loader.getController();

            dialogController.setOnSaveCallback(() -> statusLabel.setText("Flag list updated. Refresh the Device Status Tracking tab to see changes."));

            Stage stage = StageManager.createCustomStage(getStage(), "Manage Flagged Devices", root);
            stage.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open Flag Management window", e);
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Error", "Could not open the Flag Management window.");
        }
    }

    @FXML
    private void handleRunAutoImport() {
        final String FOLDERS_KEY = "bulk.import.scan.folders";
        Optional<String> savedPathsOpt = appSettingsDAO.getSetting(FOLDERS_KEY);

        if (savedPathsOpt.isEmpty() || savedPathsOpt.get().isBlank()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Setup Required", "Please configure the import folders first using 'Manage Import Folders'.");
            return;
        }

        List<String> foldersToScan = new ArrayList<>(Arrays.asList(savedPathsOpt.get().split(",")));

        Task<List<ImportResult>> importTask = new FolderImportTask(foldersToScan, deviceImportService);

        statusLabel.textProperty().bind(importTask.messageProperty());
        runAutoImportButton.setDisable(true);

        importTask.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            runAutoImportButton.setDisable(false);

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
            statusLabel.setText(String.format("Import finished. Processed: %d, Rejected: %d.", totalSuccess, totalErrors));
            StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Import Results", summary.toString());
        });

        importTask.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            runAutoImportButton.setDisable(false);
            Throwable ex = importTask.getException();
            statusLabel.setText("Import failed. See error dialog.");
            logger.error("Automated import task failed", ex);
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Import Failed", "A critical error occurred: " + ex.getMessage());
        });

        MainViewController.getInstance().bindProgressBar(importTask);
        new Thread(importTask).start();
    }

    @FXML
    private void handleBulkIntake() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/BulkIntakeDialog.fxml"));
            Parent root = loader.load();
            BulkIntakeDialogController dialogController = loader.getController();
            dialogController.init(null);

            Stage stage = StageManager.createCustomStage(getStage(), "Bulk Intake from List", root);
            stage.showAndWait();
            statusLabel.setText("Bulk intake process finished. Refresh the 'Device Status Tracking' tab to see new devices.");
        } catch (IOException e) {
            logger.error("Failed to open Bulk Intake window", e);
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Error", "Could not open the Bulk Intake window: " + e.getMessage());
        }
    }

    @FXML
    private void handleBulkUpdate() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/BulkUpdateDialog.fxml"));
            Parent root = loader.load();
            BulkUpdateDialogController dialogController = loader.getController();
            dialogController.initData(this.deviceStatusDAO, () -> statusLabel.setText("Bulk update finished. Refresh the 'Device Status Tracking' tab to see changes."));

            Stage stage = StageManager.createCustomStage(getStage(), "Bulk Status Update from List", root);
            stage.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open Bulk Update window", e);
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Error", "Could not open the Bulk Update window: " + e.getMessage());
        }
    }

    @FXML
    private void handleImportAutofill() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/AutofillImportDialog.fxml"));
            Parent root = loader.load();
            Stage stage = StageManager.createCustomStage(getStage(), "Bulk Import Autofill Data", root);
            stage.showAndWait();
            statusLabel.setText("Autofill import process finished.");
        } catch (IOException e) {
            logger.error("Failed to open Autofill Import dialog", e);
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Error", "Could not open the import dialog: " + e.getMessage());
        }
    }

    @FXML
    private void handleImportFlags() {
        new FlaggedDeviceImporter().importFromFile(getStage(), () -> statusLabel.setText("Flag import finished. Refresh views to see changes."));
    }

    @FXML
    private void handleUpdateMelRules() {
        new MelRulesImporter().importFromFile(getStage());
        statusLabel.setText("MEL Rules import process finished.");
    }

    @FXML
    private void handleManageFolders() {
        final String FOLDERS_KEY = "bulk.import.scan.folders";
        List<String> currentFolders = new ArrayList<>(appSettingsDAO.getSetting(FOLDERS_KEY).map(paths -> Arrays.asList(paths.split(","))).orElse(List.of()));

        showFolderManagementDialog(currentFolders).ifPresent(updatedFolders -> {
            String pathsToSave = String.join(",", updatedFolders);
            appSettingsDAO.saveSetting(FOLDERS_KEY, pathsToSave);
            statusLabel.setText("Import folder list has been updated.");
            StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Settings Saved", "Your import folder list has been updated.");
        });
    }

    private Optional<List<String>> showFolderManagementDialog(List<String> initialFolders) {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.initOwner(getStage());
        dialog.setTitle("Manage Import Folders");
        dialog.setHeaderText("Add or remove folders for automated scanning.");

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

    private String logImportErrors(List<ImportResult> results) {
        List<String> allErrors = results.stream().flatMap(r -> r.errors().stream()).toList();
        if (allErrors.isEmpty()) return "";
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
            logger.error("Could not write full error log to file", e);
            return "\nCould not write full error log to file due to an error: " + e.getMessage();
        }
    }

    private Stage getStage() {
        return (Stage) bulkIntakeButton.getScene().getWindow();
    }
}