package assettracking.controller;

import assettracking.manager.StageManager;
import assettracking.manager.StatusManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.Optional;

public class MonitorDisposalDialogController {

    @FXML private Label headerLabel;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> subStatusCombo;
    @FXML private TextField reasonField;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;

    private DisposalResult result;

    public record DisposalResult(String status, String subStatus, String reason) {}

    @FXML
    public void initialize() {
        statusCombo.setItems(FXCollections.observableArrayList(StatusManager.getStatuses()));

        statusCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            subStatusCombo.getItems().clear();
            if (newVal != null) {
                subStatusCombo.getItems().addAll(StatusManager.getSubStatuses(newVal));
                if (!subStatusCombo.getItems().isEmpty()) {
                    subStatusCombo.getSelectionModel().selectFirst();
                }
                // --- NEW UI CUE ---
                if ("Disposed".equals(newVal)) {
                    reasonField.setPromptText("Box ID is required");
                } else {
                    reasonField.setPromptText("");
                }
            }
        });

        statusCombo.setValue("Disposed");
        subStatusCombo.setValue("Can-Am, Pending Pickup");
        reasonField.setText("Received Broken");

        confirmButton.setOnAction(e -> handleConfirm());
        cancelButton.setOnAction(e -> handleCancel());
    }

    public void initData(String serialNumber) {
        headerLabel.setText("Set Status for Monitor: " + serialNumber);
    }

    private void handleConfirm() {
        // --- NEW VALIDATION ---
        if ("Disposed".equals(statusCombo.getValue()) && reasonField.getText().trim().isEmpty()) {
            StageManager.showAlert(
                    (Stage) confirmButton.getScene().getWindow(),
                    Alert.AlertType.WARNING,
                    "Input Required",
                    "A Box ID must be entered in the 'Reason/Note' field when the status is 'Disposed'."
            );
            return; // Stop the process
        }

        String finalReason = reasonField.getText().trim();
        // --- NEW FORMATTING ---
        if ("Disposed".equals(statusCombo.getValue())) {
            finalReason = "Box ID: " + finalReason;
        }

        result = new DisposalResult(statusCombo.getValue(), subStatusCombo.getValue(), finalReason);
        closeStage();
    }

    private void handleCancel() {
        result = null;
        closeStage();
    }

    public Optional<DisposalResult> getResult() {
        return Optional.ofNullable(result);
    }

    private void closeStage() {
        ((Stage) confirmButton.getScene().getWindow()).close();
    }
}