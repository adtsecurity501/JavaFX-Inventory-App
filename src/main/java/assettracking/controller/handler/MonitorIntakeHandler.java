package assettracking.controller.handler;

import assettracking.controller.AddAssetDialogController;
import assettracking.controller.MonitorDisposalDialogController;
import assettracking.data.AssetInfo;
import assettracking.manager.IntakeService;
import assettracking.manager.StageManager;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Manages all UI logic and event handling for the "Monitor Intake" panel.
 * This handler now delegates all database persistence logic to the IntakeService
 * and uses the public API of the AddAssetDialogController to interact with the UI.
 */
public class MonitorIntakeHandler {

    private final AddAssetDialogController controller;

    public MonitorIntakeHandler(AddAssetDialogController controller) {
        this.controller = controller;
    }

    public void handleFunctioningButton() {
        String serial = controller.getMonitorSerial();
        String model = controller.getMonitorModel();
        String description = controller.getMonitorDescription();
        String printer = controller.getMonitorPrinter();
        String officialSku = controller.getMonitorSelectedSku();

        if (serial.isEmpty() || model.isEmpty()) {
            showAlert("Input Required", "Serial Number and Model are required.");
            return;
        }
        if (officialSku.isEmpty()) {
            showAlert("SKU Required", "You must search for and select a Label SKU to print.");
            return;
        }
        if (printer == null) {
            showAlert("Printer Required", "Please select a printer.");
            return;
        }

        String finalLabelDescription = controller.getAssetDAO().findDescriptionBySkuNumber(officialSku).orElse(description);
        Task<Void> task = createPrintTask(serial, model, description, printer, officialSku, finalLabelDescription);
        task.setOnSucceeded(e -> {
            controller.updateMonitorFeedback("Success: " + serial + " processed and labels printed.");
            controller.clearMonitorFieldsAndFocus();
            if (controller.getParentController() != null) controller.getParentController().refreshData();
        });
        task.setOnFailed(e -> controller.updateMonitorFeedback("Error: " + e.getSource().getException().getMessage()));
        new Thread(task).start();
    }

    public void handleBrokenButton() {
        String serial, model, description;
        if (controller.isStandardMonitor()) {
            serial = controller.getMonitorSerial();
            model = controller.getMonitorModel();
            description = controller.getMonitorDescription();
            if (serial.isEmpty() || model.isEmpty() || description.isEmpty()) {
                showAlert("Input Required", "Serial, Model, and Description are required.");
                return;
            }
        } else {
            serial = controller.getManualSerial();
            description = controller.getManualDescription();
            model = "NON-STANDARD";
            if (serial.isEmpty() || description.isEmpty()) {
                showAlert("Input Required", "Serial and Manual Description are required.");
                return;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MonitorDisposalDialog.fxml"));
            Parent root = loader.load();
            MonitorDisposalDialogController dialogController = loader.getController();
            dialogController.initData(serial);
            Stage dialogStage = StageManager.createCustomStage(controller.getOwnerWindow(), "Set Disposal Status", root);
            dialogStage.showAndWait();

            dialogController.getResult().ifPresent(result -> {
                processBrokenMonitor(serial, model, description, result.status(), result.subStatus(), result.reason(), result.boxId());
            });
        } catch (IOException e) {
            System.err.println("Failed to open disposal dialog: " + e.getMessage());
            showAlert("Error", "Could not open the disposal dialog window.");
        }
    }

    private Task<Void> createPrintTask(String serial, String model, String description, String printer, String sku, String labelDesc) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                IntakeService intakeService = new IntakeService(controller.getCurrentPackage(), false);
                AssetInfo details = new AssetInfo();
                details.setMake("Dell");
                details.setModelNumber(model);
                details.setDescription(description);
                details.setCategory("Monitor");

                intakeService.processFromTextArea(new String[]{serial}, details, false, "Processed", "Ready for Deployment", null, null);

                controller.updateMonitorFeedback("Printing labels for " + serial + "...");
                boolean s1 = controller.getPrinterService().sendZplToPrinter(printer, controller.getPrinterService().getAdtLabelZpl(sku, labelDesc));
                boolean s2 = controller.getPrinterService().sendZplToPrinter(printer, controller.getPrinterService().getSerialLabelZpl(sku, serial));
                if (!s1 || !s2) throw new Exception("Failed to print one or both labels.");
                return null;
            }
        };
    }

    private void processBrokenMonitor(String serial, String model, String description, String status, String subStatus, String reason, String boxId) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                IntakeService intakeService = new IntakeService(controller.getCurrentPackage(), false);
                AssetInfo details = new AssetInfo();
                details.setMake(model.equals("NON-STANDARD") ? "Various" : "Dell");
                details.setModelNumber(model);
                details.setDescription(description);
                details.setCategory("Monitor");

                intakeService.processFromTextArea(new String[]{serial}, details, true, status, subStatus, reason, boxId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            controller.updateMonitorFeedback("Success: " + serial + " processed with status '" + status + "'.");
            controller.clearMonitorFieldsAndFocus();
            if (controller.getParentController() != null) controller.getParentController().refreshData();
        });
        task.setOnFailed(e -> controller.updateMonitorFeedback("Error: " + e.getSource().getException().getMessage()));
        new Thread(task).start();
    }

    private void showAlert(String title, String content) {
        StageManager.showAlert(controller.getOwnerWindow(), Alert.AlertType.WARNING, title, content);
    }
}