package assettracking.controller;

import assettracking.dao.DeviceStatusDAO;
import assettracking.manager.StageManager;
import assettracking.manager.StatusManager;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class BulkUpdateDialogController {

    @FXML
    private TextArea serialsTextArea;
    @FXML
    private ComboBox<String> statusCombo;
    @FXML
    private ComboBox<String> subStatusCombo;
    @FXML
    private Button processButton;
    @FXML
    private Label successLabel;
    @FXML
    private ListView<String> successListView;
    @FXML
    private Label notFoundLabel;
    @FXML
    private ListView<String> notFoundListView;

    private DeviceStatusDAO deviceStatusDAO;
    private Runnable onFinishedCallback;

    @FXML
    public void initialize() {
        // We will pass the DAO in from the main controller
        setupStatusComboBoxes();
    }

    // Called from the main controller to pass necessary components
    public void initData(DeviceStatusDAO deviceStatusDAO, Runnable onFinishedCallback) {
        this.deviceStatusDAO = deviceStatusDAO;
        this.onFinishedCallback = onFinishedCallback;
    }

    private void setupStatusComboBoxes() {
        statusCombo.getItems().addAll(StatusManager.getStatuses());
        statusCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            subStatusCombo.getItems().clear();
            if (n != null) {
                subStatusCombo.getItems().addAll(StatusManager.getSubStatuses(n));
                if (!subStatusCombo.getItems().isEmpty()) {
                    subStatusCombo.getSelectionModel().select(0);
                }
            }
        });
        statusCombo.getSelectionModel().select(0);
    }

    @FXML
    private void handleProcess() {
        String text = serialsTextArea.getText();
        String newStatus = statusCombo.getValue();
        String newSubStatus = subStatusCombo.getValue();

        if (text == null || text.trim().isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "Please paste at least one serial number.");
            return;
        }
        if (newStatus == null || newSubStatus == null) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "Please select a new status and sub-status.");
            return;
        }

        // Parse and clean the input serials
        Set<String> serialsToUpdate = Arrays.stream(text.split("\\s+")) // Split by any whitespace (space, tab, newline)
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equalsIgnoreCase("Tag"))
                .collect(Collectors.toSet());

        if (serialsToUpdate.isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "No valid serial numbers were found in the input.");
            return;
        }

        // Perform the update on a background thread
        Task<DeviceStatusDAO.BulkUpdateResult> updateTask = new Task<>() {
            @Override
            protected DeviceStatusDAO.BulkUpdateResult call() throws Exception {
                // The note will be generic for this bulk operation
                String note = "Bulk status update via list.";
                return deviceStatusDAO.bulkUpdateStatusBySerial(serialsToUpdate, newStatus, newSubStatus, note);
            }
        };

        updateTask.setOnRunning(e -> {
            processButton.setDisable(true);
            successLabel.setText("Processing...");
            notFoundLabel.setText("Processing...");
            successListView.getItems().clear();
            notFoundListView.getItems().clear();
        });

        updateTask.setOnSucceeded(e -> {
            DeviceStatusDAO.BulkUpdateResult result = updateTask.getValue();
            successListView.setItems(FXCollections.observableArrayList(result.updated()));
            notFoundListView.setItems(FXCollections.observableArrayList(result.notFound()));
            successLabel.setText(String.format("Successfully Updated (%d)", result.updated().size()));
            notFoundLabel.setText(String.format("Not Found in Database (%d)", result.notFound().size()));

            // Notify the main screen to refresh its data
            if (onFinishedCallback != null) {
                onFinishedCallback.run();
            }
            processButton.setDisable(false);
        });

        updateTask.setOnFailed(e -> {
            processButton.setDisable(false);
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Update Failed", "A database error occurred: " + e.getSource().getException().getMessage());
        });

        new Thread(updateTask).start();
    }

    @FXML
    private void handleClose() {
        getStage().close();
    }

    private Stage getStage() {
        return (Stage) processButton.getScene().getWindow();
    }
}