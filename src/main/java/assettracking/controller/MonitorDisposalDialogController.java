package assettracking.controller;

import assettracking.manager.StageManager;
import assettracking.manager.StatusManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.Optional;

public class MonitorDisposalDialogController {

    @FXML
    private Label headerLabel;
    @FXML
    private ComboBox<String> statusCombo;
    @FXML
    private ComboBox<String> subStatusCombo;
    @FXML
    private TextField reasonField;
    @FXML
    private Label boxIdLabel;
    @FXML
    private TextField boxIdField;
    @FXML
    private Button confirmButton;
    @FXML
    private Button cancelButton;

    private DisposalResult result;

    @FXML
    public void initialize() {
        statusCombo.setItems(FXCollections.observableArrayList(StatusManager.getStatuses()));

        statusCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            subStatusCombo.getItems().clear();
            if (newVal != null) {
                subStatusCombo.getItems().addAll(StatusManager.getSubStatuses(newVal));
                if (!subStatusCombo.getItems().isEmpty()) {
                    subStatusCombo.getSelectionModel().select(0);
                }

                boolean isDisposed = "Disposed".equals(newVal);
                boxIdLabel.setVisible(isDisposed);
                boxIdLabel.setManaged(isDisposed);
                boxIdField.setVisible(isDisposed);
                boxIdField.setManaged(isDisposed);

                // --- CORRECTED LOGIC ---
                confirmButton.getStyleClass().remove("success");
                confirmButton.getStyleClass().remove("danger");
                confirmButton.getStyleClass().add(isDisposed ? "danger" : "success");
                // --- END CORRECTION ---

                confirmButton.setText(isDisposed ? "Confirm Disposal" : "Confirm Status");
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
        if ("Disposed".equals(statusCombo.getValue())) {
            String subStatus = subStatusCombo.getValue();
            if (!"Ready for Wipe".equals(subStatus) && boxIdField.getText().trim().isEmpty()) {
                StageManager.showAlert(
                        confirmButton.getScene().getWindow(),
                        Alert.AlertType.WARNING,
                        "Input Required",
                        "A Box ID is required for this disposed status. It is optional only for 'Ready for Wipe'."
                );
                return;
            }
        }

        result = new DisposalResult(statusCombo.getValue(), subStatusCombo.getValue(), reasonField.getText(), boxIdField.getText());
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

    public record DisposalResult(String status, String subStatus, String reason, String boxId) {
    }
}