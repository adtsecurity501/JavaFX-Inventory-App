package assettracking.controller;

import assettracking.dao.AppSettingsDAO;
import assettracking.manager.StageManager;
import assettracking.service.ImagingEmailService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ImagingSettingsController {

    // Constants for settings keys
    private static final String FOLDER_KEY = "imaging.outlook.folder";
    private static final String IP_KEY = "imaging.filter.ip";
    private static final String REFRESH_KEY = "imaging.refresh.interval";
    private static final String SUBJECT_KEY = "imaging.subject.filter";
    private static final String COMP_NAME_KEY = "imaging.keyword.comp_name";
    private static final String SERIAL_KEY = "imaging.keyword.serial";
    private static final String TIME_KEY = "imaging.keyword.time";
    private static final String FAILED_KEY = "imaging.keyword.failed";

    private final AppSettingsDAO settingsDAO = new AppSettingsDAO();
    private final ImagingEmailService emailService = new ImagingEmailService();
    private boolean saved = false;

    @FXML
    private TextField folderNameField;
    @FXML
    private TextField ipFilterField;
    @FXML
    private TextField refreshIntervalField;
    @FXML
    private TextField subjectFilterField;
    @FXML
    private TextField computerNameKeywordField;
    @FXML
    private TextField serialNumberKeywordField;
    @FXML
    private TextField reimageTimeKeywordField;
    @FXML
    private TextField failedInstallsKeywordField;
    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        // Load all settings, providing sensible defaults
        folderNameField.setText(settingsDAO.getSetting(FOLDER_KEY).orElse("OSD Include"));
        subjectFilterField.setText(settingsDAO.getSetting(SUBJECT_KEY).orElse("OSD Completion"));
        ipFilterField.setText(settingsDAO.getSetting(IP_KEY).orElse("10.68.47."));
        refreshIntervalField.setText(settingsDAO.getSetting(REFRESH_KEY).orElse("5"));

        // --- THIS BLOCK IS UPDATED WITH YOUR NEW DEFAULTS ---
        computerNameKeywordField.setText(settingsDAO.getSetting(COMP_NAME_KEY).orElse("Computer Name:")); // Stays for consistency
        serialNumberKeywordField.setText(settingsDAO.getSetting(SERIAL_KEY).orElse("Serial Number:")); // Note: No colon
        reimageTimeKeywordField.setText(settingsDAO.getSetting(TIME_KEY).orElse("Job Total Run Time:"));
        failedInstallsKeywordField.setText(settingsDAO.getSetting(FAILED_KEY).orElse("NOTINSTALLED"));
        // --- END OF UPDATE ---
    }

    @FXML
    private void handleSave() {
        try {
            int interval = Integer.parseInt(refreshIntervalField.getText().trim());
            if (interval <= 0) {
                StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Invalid Input", "Refresh interval must be a positive number.");
                return;
            }
        } catch (NumberFormatException e) {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Invalid Input", "Refresh interval must be a valid number.");
            return;
        }

        // Save all settings
        settingsDAO.saveSetting(FOLDER_KEY, folderNameField.getText().trim());
        settingsDAO.saveSetting(SUBJECT_KEY, subjectFilterField.getText().trim());
        settingsDAO.saveSetting(IP_KEY, ipFilterField.getText().trim());
        settingsDAO.saveSetting(REFRESH_KEY, refreshIntervalField.getText().trim());
        settingsDAO.saveSetting(COMP_NAME_KEY, computerNameKeywordField.getText().trim());
        settingsDAO.saveSetting(SERIAL_KEY, serialNumberKeywordField.getText().trim());
        settingsDAO.saveSetting(TIME_KEY, reimageTimeKeywordField.getText().trim());
        settingsDAO.saveSetting(FAILED_KEY, failedInstallsKeywordField.getText().trim());

        saved = true;
        statusLabel.setText("Settings saved successfully.");

        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1));
        delay.setOnFinished(event -> closeStage());
        delay.play();
    }

    @FXML
    private void handleTestConnection() {
        String folderName = folderNameField.getText().trim();
        if (folderName.isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "Please enter an Outlook Folder Path to test.");
            return;
        }

        statusLabel.setText("Testing connection to Outlook folder...");
        emailService.testOutlookConnection(folderName).thenAccept(result -> {
            Platform.runLater(() -> {
                if (result.startsWith("SUCCESS")) {
                    StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Connection Successful", result);
                } else {
                    StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Connection Failed", result);
                }
                statusLabel.setText("Test complete.");
            });
        });
    }

    private Stage getStage() {
        return (Stage) statusLabel.getScene().getWindow();
    }

    @FXML
    private void handleCancel() {
        closeStage();
    }

    public boolean isSaved() {
        return saved;
    }

    private void closeStage() {
        ((Stage) statusLabel.getScene().getWindow()).close();
    }
}