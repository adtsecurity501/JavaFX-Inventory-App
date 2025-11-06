package assettracking.controller.handler;

import assettracking.controller.AddAssetDialogController;
import assettracking.controller.MachineRemovalSelectionController;
import assettracking.dao.FlaggedDeviceDAO;
import assettracking.data.AssetInfo;
import assettracking.manager.IntakeService;
import assettracking.manager.MachineRemovalService;
import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StandardIntakeHandler {

    private final AddAssetDialogController controller;
    private final MachineRemovalService removalService = new MachineRemovalService();
    private final FlaggedDeviceDAO flaggedDeviceDAO = new FlaggedDeviceDAO();

    public StandardIntakeHandler(AddAssetDialogController controller) {
        this.controller = controller;
    }

    public void handleLookupSerial() {
        String serialToLookup = controller.getSerial();
        if (serialToLookup.isEmpty()) return;

        controller.setFormAssetDetails(new AssetInfo());
        controller.setProbableCause("");
        controller.setMelAction("");

        controller.getAssetDAO().findAssetBySerialNumber(serialToLookup).ifPresent(asset -> {
            controller.setFormAssetDetails(asset);
            applyMelRule(asset.getModelNumber(), asset.getDescription());
        });

        flaggedDeviceDAO.getFlagBySerial(serialToLookup).ifPresent(flagData -> {
            controller.setFlaggedDeviceFields();
            String reason = flagData.reason();
            String displayReason = reason;

            if (flagData.preventRemoval()) {
                // Remove the tag for display purposes
                if (reason != null) {
                    displayReason = reason.replace("[NOREMOVE]", "").trim();
                }
                // Set the main flag reason in its original location
                controller.setProbableCause("Flagged: " + displayReason);
                // SET THE NEW WARNING in the disposition area
                controller.setDispositionWarning("WARNING: This device will NOT be automatically removed from AD/SCCM on intake.");
            } else {
                // If not prevented, clear any lingering warning and set the normal reason
                controller.setDispositionWarning(null);
                controller.setProbableCause("Flagged Reason: " + reason);
            }
        });
    }

    private void runAutomatedMachineRemoval(String serialNumber) {
        controller.updateStandardIntakeFeedback("Searching AD/SCCM for " + serialNumber + "...");

        if (flaggedDeviceDAO.isAutoRemovalPrevented(serialNumber)) {
            Platform.runLater(() -> controller.updateStandardIntakeFeedback("Asset processed. Auto-removal from AD/SCCM was skipped as per flag."));
            System.out.println("Skipping auto-removal for " + serialNumber + " due to [NOREMOVE] flag.");
            return;
        }

        removalService.search(List.of(serialNumber)).thenAccept(results -> {
            Map<String, List<MachineRemovalService.SearchResult>> groupedResults = results.stream().filter(r -> "OK".equalsIgnoreCase(r.status())).collect(Collectors.groupingBy(MachineRemovalService.SearchResult::source));

            List<MachineRemovalService.SearchResult> adResults = groupedResults.getOrDefault("AD", Collections.emptyList());
            List<MachineRemovalService.SearchResult> sccmResults = groupedResults.getOrDefault("SCCM", Collections.emptyList());

            if (adResults.size() > 1 || sccmResults.size() > 1) {
                Platform.runLater(() -> showManualSelectionDialog(serialNumber, results));
            } else {
                List<String> namesToRemove = results.stream().map(MachineRemovalService.SearchResult::computerName).distinct().collect(Collectors.toList());

                if (!namesToRemove.isEmpty()) {
                    removalService.remove(namesToRemove).thenAccept(log -> {
                        String logMessage = "Auto-Removal for S/N " + serialNumber + ": " + String.join(". ", log);
                        System.out.println(logMessage);
                        Platform.runLater(() -> controller.updateStandardIntakeFeedback("Asset processed. Auto-removed from AD/SCCM."));
                    });
                } else {
                    Platform.runLater(() -> controller.updateStandardIntakeFeedback("Asset processed. Not found in AD/SCCM."));
                }
            }
        });
    }

    // ... handleSave() and other existing methods ...
    public void handleSave() {
        if (controller.isBulkAddMode()) {
            if (controller.getAssetEntries().isEmpty() || controller.getAssetEntries().stream().allMatch(e -> e.getSerialNumber().trim().isEmpty())) {
                StageManager.showAlert(controller.getOwnerWindow(), Alert.AlertType.WARNING, "Input Required", "At least one device with a Serial Number is required in the table.");
                return;
            }
        } else if (controller.isMultiSerialMode()) {
            if (controller.getSerialsFromArea().length == 0) {
                StageManager.showAlert(controller.getOwnerWindow(), Alert.AlertType.WARNING, "Input Required", "Please enter at least one Serial Number in the text area.");
                return;
            }
        } else {
            if (controller.getSerial().trim().isEmpty()) {
                StageManager.showAlert(controller.getOwnerWindow(), Alert.AlertType.WARNING, "Input Required", "A Serial Number is required.");
                return;
            }
        }

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

            AssetInfo details = controller.getAssetDetailsFromForm();
            String category = details.getCategory();

            if (!controller.isBulkAddMode() && !controller.isMultiSerialMode()) {
                String serial = controller.getSerial();
                if (("Laptop".equalsIgnoreCase(category) || "Desktop".equalsIgnoreCase(category) || "Getac".equalsIgnoreCase(category)) && !serial.isEmpty()) {
                    runAutomatedMachineRemoval(serial);
                }
            }

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

    public void applyMelRule(String modelNumber, String description) {
        controller.getAssetDAO().findMelRule(modelNumber, description).ifPresent(rule -> {
            controller.setMelAction("MEL Action: " + rule.action());
            if ("Dispose".equalsIgnoreCase(rule.action())) {
                controller.setDispositionFieldsForDispose();
            }
        });
    }

    private void showManualSelectionDialog(String serialNumber, List<MachineRemovalService.SearchResult> results) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MachineRemovalSelectionDialog.fxml"));
            Parent root = loader.load();
            MachineRemovalSelectionController dialogController = loader.getController();
            dialogController.initData(serialNumber, results);

            Stage stage = StageManager.createCustomStage(controller.getOwnerWindow(), "Multiple Machines Found", root);
            stage.show();

        } catch (IOException e) {
            System.err.println("Failed to open machine removal selection dialog: " + e.getMessage());
        }
    }

    private Task<String> createSaveTask() {
        final IntakeService intakeService = new IntakeService(controller.getCurrentPackage(), controller.isNewCondition());

        return new Task<>() {
            @Override
            protected String call() {
                if (controller.isBulkAddMode()) {
                    return intakeService.processFromTable(new java.util.ArrayList<>(controller.getAssetEntries()), controller.isSellScrap(), controller.getScrapStatus(), controller.getScrapSubStatus(), controller.getScrapReason(), controller.getBoxId());
                } else {
                    AssetInfo details = controller.getAssetDetailsFromForm();
                    String[] serials = controller.isMultiSerialMode() ? controller.getSerialsFromArea() : new String[]{controller.getSerial()};
                    return intakeService.processFromTextArea(serials, details, controller.isSellScrap(), controller.getScrapStatus(), controller.getScrapSubStatus(), controller.getScrapReason(), controller.getBoxId());
                }
            }
        };
    }
}