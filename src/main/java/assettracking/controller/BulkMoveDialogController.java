package assettracking.controller;

import assettracking.dao.DeviceStatusDAO;
import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class BulkMoveDialogController {

    private final DeviceStatusDAO deviceStatusDAO = new DeviceStatusDAO(null, null);
    private Runnable onFinishedCallback;

    @FXML
    private TextArea serialListArea;
    @FXML
    private TextField sourceBoxField, destinationBoxField;
    @FXML
    private Button moveButton;
    @FXML
    private Label statusLabel, successLabel, failedLabel;
    @FXML
    private ListView<String> successListView, failedListView;

    public void setOnFinishedCallback(Runnable onFinishedCallback) {
        this.onFinishedCallback = onFinishedCallback;
    }

    @FXML
    public void initialize() {
        // Enable the "Move" button only when there is text in the serial list area.
        serialListArea.textProperty().addListener((obs, oldVal, newVal) -> moveButton.setDisable(newVal == null || newVal.isBlank()));
    }

    @FXML
    private void handleMove() {
        if (sourceBoxField.getText().isBlank() || destinationBoxField.getText().isBlank()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "Please enter both source and destination Box IDs.");
            return;
        }

        String sourceBox = sourceBoxField.getText().trim();
        String destinationBox = destinationBoxField.getText().trim();

        if (sourceBox.equalsIgnoreCase(destinationBox)) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Error", "Source and Destination Box IDs cannot be the same.");
            return;
        }

        moveButton.setDisable(true);
        statusLabel.setText("Processing and updating database...");

        Task<DeviceStatusDAO.BulkMoveResult> moveTask = new Task<>() {
            @Override
            protected DeviceStatusDAO.BulkMoveResult call() throws Exception {
                Set<String> serials = parseSerialsFromTextArea(serialListArea.getText());
                if (serials.isEmpty()) {
                    throw new IOException("No valid serial numbers were found in the pasted list.");
                }
                return deviceStatusDAO.bulkMoveBySerialList(sourceBox, destinationBox, serials);
            }
        };

        moveTask.setOnSucceeded(e -> {
            DeviceStatusDAO.BulkMoveResult result = moveTask.getValue();
            Platform.runLater(() -> {
                successListView.setItems(FXCollections.observableArrayList(result.movedSerials()));
                failedListView.setItems(FXCollections.observableArrayList(result.notFoundOrFailedSerials()));
                successLabel.setText(String.format("Successfully Moved (%d)", result.movedSerials().size()));
                failedLabel.setText(String.format("Not Found in Source Box (%d)", result.notFoundOrFailedSerials().size()));
                statusLabel.setText("Operation complete.");
                if (onFinishedCallback != null) {
                    onFinishedCallback.run();
                }
            });
            moveButton.setDisable(false);
        });

        moveTask.setOnFailed(e -> {
            statusLabel.setText("Operation failed!");
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Move Failed", "An error occurred: " + e.getSource().getException().getMessage());
            moveButton.setDisable(false);
        });

        new Thread(moveTask).start();
    }

    private Set<String> parseSerialsFromTextArea(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        return text.lines().map(String::trim).map(String::toUpperCase).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    @FXML
    private void handleClose() {
        getStage().close();
    }

    private Stage getStage() {
        return (Stage) moveButton.getScene().getWindow();
    }
}