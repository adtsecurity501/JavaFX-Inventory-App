package assettracking.controller.handler;

import assettracking.controller.AddAssetDialogController;
import assettracking.data.AssetInfo;
import assettracking.manager.IntakeService;
import assettracking.manager.StageManager;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Manages all UI logic and event handling for the "Standard Intake" panel.
 */
public class StandardIntakeHandler {

    private final AddAssetDialogController controller;

    public StandardIntakeHandler(AddAssetDialogController controller) {
        this.controller = controller;
    }

    public void handleLookupSerial() {
        String serialToLookup = controller.getSerial();
        if (serialToLookup.isEmpty()) return;

        controller.setFormAssetDetails(new AssetInfo()); // Clears the form fields
        controller.setProbableCause("");
        controller.setMelAction("");

        controller.getAssetDAO().findAssetBySerialNumber(serialToLookup).ifPresent(asset -> {
            controller.setFormAssetDetails(asset);
            applyMelRule(asset.getModelNumber(), asset.getDescription());
        });

        try (Connection conn = controller.getDbConnection();
             PreparedStatement flagStmt = conn.prepareStatement("SELECT flag_reason FROM Flag_Devices WHERE serial_number = ?")) { // Query no longer needs sub_status
            flagStmt.setString(1, serialToLookup);
            ResultSet rs = flagStmt.executeQuery();
            if (rs.next()) {
                final String reason = rs.getString("flag_reason");
                // The reason is no longer needed here, as the status is now fixed
                controller.setFlaggedDeviceFields();
                controller.setProbableCause("Flagged Reason: " + reason);
            }
        } catch (SQLException e) {
            controller.setProbableCause("DB Error checking flag.");
        }
    }

    public void applyMelRule(String modelNumber, String description) {
        controller.getAssetDAO().findMelRule(modelNumber, description).ifPresent(rule -> {
            controller.setMelAction("MEL Action: " + rule.action());
            if ("Dispose".equalsIgnoreCase(rule.action())) {
                controller.setDispositionFieldsForDispose();
            }
        });
    }

    public void handleSave() {
        if (controller.isSellScrap() && "Disposed".equals(controller.getScrapStatus())) {
            if (!"Ready for Wipe".equals(controller.getScrapSubStatus()) && controller.getBoxId().isEmpty()) {
                StageManager.showAlert(controller.getOwnerWindow(), Alert.AlertType.WARNING, "Box ID Required", "A Box ID is required for this disposed status.");
                return;
            }
        }
        controller.disableSaveButton(true);
        controller.updateStandardIntakeFeedback("Processing...");
        Task<String> saveTask = createSaveTask();
        saveTask.setOnSucceeded(event -> {
            String result = saveTask.getValue();
            if (controller.getParentController() != null) controller.getParentController().refreshData();
            if (result.toLowerCase().contains("error")) {
                controller.updateStandardIntakeFeedback(result);
                controller.disableSaveButton(false);
            } else {
                controller.handleClose();
            }
        });
        saveTask.setOnFailed(event -> {
            Throwable ex = saveTask.getException();
            String errorMessage = "Critical Error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            controller.updateStandardIntakeFeedback(errorMessage);
            StageManager.showAlert(controller.getOwnerWindow(), Alert.AlertType.ERROR, "Save Failed", errorMessage);
            controller.disableSaveButton(false);
        });
        new Thread(saveTask).start();
    }

    private Task<String> createSaveTask() {
        final IntakeService intakeService = new IntakeService(controller.getCurrentPackage(), controller.isNewCondition());

        return new Task<>() {
            @Override
            protected String call() {
                if (controller.isBulkAddMode()) {
                    return intakeService.processFromTable(new ArrayList<>(controller.getAssetEntries()), controller.isSellScrap(), controller.getScrapStatus(), controller.getScrapSubStatus(), controller.getScrapReason(), controller.getBoxId());
                } else {
                    AssetInfo details = controller.getAssetDetailsFromForm();
                    String[] serials = controller.isMultiSerialMode() ? controller.getSerialsFromArea() : new String[]{controller.getSerial()};
                    return intakeService.processFromTextArea(serials, details, controller.isSellScrap(), controller.getScrapStatus(), controller.getScrapSubStatus(), controller.getScrapReason(), controller.getBoxId());
                }
            }
        };
    }
}