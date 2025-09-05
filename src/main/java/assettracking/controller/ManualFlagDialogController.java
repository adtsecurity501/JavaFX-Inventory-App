package assettracking.controller;

import assettracking.dao.FlaggedDeviceDAO;
import assettracking.manager.StageManager;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ManualFlagDialogController {

    @FXML private TextField serialField;
    @FXML private TextArea reasonArea;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final FlaggedDeviceDAO flaggedDeviceDAO = new FlaggedDeviceDAO();
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        saveButton.setOnAction(e -> handleSave());
        cancelButton.setOnAction(e -> closeStage());
    }

    public void setOnSaveCallback(Runnable onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void handleSave() {
        String serial = serialField.getText().trim();
        String reason = reasonArea.getText().trim();

        if (serial.isEmpty() || reason.isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "Serial Number and Reason for Flag cannot be empty.");
            return;
        }

        boolean success = flaggedDeviceDAO.flagDevice(serial, reason);

        if (success) {
            if (onSaveCallback != null) {
                onSaveCallback.run(); // This will trigger the refresh
            }
            closeStage();
        } else {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Error", "Could not save the flag to the database.");
        }
    }

    private Stage getStage() {
        return (Stage) saveButton.getScene().getWindow();
    }

    private void closeStage() {
        getStage().close();
    }
}