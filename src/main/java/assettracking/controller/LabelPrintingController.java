package assettracking.controller;

import assettracking.dao.SkuDAO;
import assettracking.data.Sku;
import assettracking.label.service.ZplPrinterService;
import assettracking.ui.AutoCompletePopup;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LabelPrintingController {

    // --- Services and DAO ---
    private final SkuDAO skuDAO = new SkuDAO();
    private final ZplPrinterService printerService = new ZplPrinterService();

    // --- FXML Components ---
    @FXML
    private ComboBox<String> printerNameField, assetPrinterNameField;
    @FXML
    private Label statusLabel;
    @FXML
    private ToggleGroup menuGroup;
    @FXML
    private StackPane mainStackPane;
    @FXML
    private Pane welcomePane;
    @FXML
    private ToggleButton menuDeployDevice, menuPrintSingle, menuPrintMultiple, menuPrintSerial, menuPrintAssetTag, menuPrintBarcode, menuImageLabels;
    @FXML
    private ScrollPane deployDevicePane, printSinglePane, printMultiplePane, printSerialPane, printAssetTagPane, printBarcodePane, imageLabelsPane;
    @FXML
    private TextField deploySkuSearchField, deploySkuField, deployDescriptionField, deploySerialField;
    @FXML
    private ListView<String> deploySkuListView;
    @FXML
    private TextField singleSkuSearchField, singleSkuField;
    @FXML
    private ListView<String> singleSkuListView;
    @FXML
    private TextField multiSkuSearchField, multiSkuField, multiCopiesField;
    @FXML
    private ListView<String> multiSkuListView;
    @FXML
    private TextField serialSkuSearchField, serialSkuField, serialSerialField;
    @FXML
    private ListView<String> serialSkuListView;
    @FXML
    private RadioButton assetTagStandardRadio;
    @FXML
    private TextField assetSerialField, assetImeiField;
    @FXML
    private CheckBox assetImeiCheckbox;
    @FXML
    private TextField genericBarcodeField;
    @FXML
    private TextField imageSkuField, imageDeviceSkuField, imagePrefixField, imageCopiesField;
    @FXML
    private RadioButton barcode14Radio, barcode20Radio;

    // --- NEW: References to popups ---
    private AutoCompletePopup imageSkuPopup;
    private AutoCompletePopup imageDeviceSkuPopup;

    @FXML
    public void initialize() {
        populatePrinterComboBoxes();
        setupSearchableSkuFields();
        setupAutocomplete();
        setupMenuToggles();

        // --- THIS IS THE CORRECTED AND ROBUST WORKFLOW LOGIC ---

        // This listener enables/disables the IMEI field.
        assetImeiCheckbox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            assetImeiField.setDisable(!isSelected);
            // Always return focus to the serial field when the mode changes.
            Platform.runLater(() -> assetSerialField.requestFocus());
        });

        // Event handler for when "Enter" is pressed in the serial field.
        assetSerialField.setOnAction(event -> {
            if (assetImeiCheckbox.isSelected()) {
                // If IMEI is needed, reliably move focus to the IMEI field.
                Platform.runLater(() -> assetImeiField.requestFocus());
            } else {
                // If IMEI is not needed, print immediately.
                handlePrintAssetTag();
            }
        });

        // Event handler for when "Enter" is pressed in the IMEI field.
        // NOTE THE FIX: The handler is on 'assetImeiField', not 'imeiField'.
        assetImeiField.setOnAction(event -> {
            // Always print after the IMEI has been entered.
            handlePrintAssetTag();
        });
    }

    private void setupMenuToggles() {
        menuDeployDevice.setUserData(deployDevicePane);
        menuPrintSingle.setUserData(printSinglePane);
        menuPrintMultiple.setUserData(printMultiplePane);
        menuPrintSerial.setUserData(printSerialPane);
        menuPrintAssetTag.setUserData(printAssetTagPane);
        menuPrintBarcode.setUserData(printBarcodePane);
        menuImageLabels.setUserData(imageLabelsPane);

        menuGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            mainStackPane.getChildren().forEach(child -> {
                if (child instanceof ScrollPane) child.setVisible(false);
            });
            welcomePane.setVisible(false);
            if (newToggle == null) {
                welcomePane.setVisible(true);
            } else {
                Node paneToShow = (Node) newToggle.getUserData();
                if (paneToShow != null) paneToShow.setVisible(true);
            }
        });
        menuDeployDevice.setSelected(true);
    }

    // --- UPDATED: Now creates and stores popups ---
    private void setupAutocomplete() {
        imageSkuPopup = new AutoCompletePopup(imageSkuField, () -> skuDAO.findSkusLike(imageSkuField.getText())).setOnSuggestionSelected(selectedValue -> {
            String sku = selectedValue.split(" - ")[0];
            // --- THIS IS THE NEW HELPER METHOD CALL ---
            selectAndSetText(imageSkuPopup, imageSkuField, sku);
        });

        imageDeviceSkuPopup = new AutoCompletePopup(imageDeviceSkuField, () -> skuDAO.findSkusLike(imageDeviceSkuField.getText())).setOnSuggestionSelected(selectedValue -> {
            String sku = selectedValue.split(" - ")[0];
            // --- THIS IS THE NEW HELPER METHOD CALL ---
            selectAndSetText(imageDeviceSkuPopup, imageDeviceSkuField, sku);
        });
    }


    // --- NEW: Helper method to handle suppression logic ---
    private void selectAndSetText(AutoCompletePopup popup, TextField field, String value) {
        Platform.runLater(() -> {
            popup.suppressListener(true);
            field.setText(value);
            field.positionCaret(field.getLength());
            popup.suppressListener(false);
        });
    }

    // --- All other methods remain the same ---
    private void setupSearchableSkuFields() {
        setupSearchableSkuField(deploySkuSearchField, deploySkuListView, deploySkuField, deployDescriptionField, deploySerialField);
        setupSearchableSkuField(singleSkuSearchField, singleSkuListView, singleSkuField, null, singleSkuField);
        setupSearchableSkuField(multiSkuSearchField, multiSkuListView, multiSkuField, null, multiCopiesField);
        setupSearchableSkuField(serialSkuSearchField, serialSkuListView, serialSkuField, null, serialSerialField);
    }

    private void setupSearchableSkuField(TextField searchField, ListView<String> listView, TextField targetSkuField, TextField targetDescriptionField, Control nextFocusTarget) {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                listView.getItems().clear();
                return;
            }
            List<String> suggestions = skuDAO.findSkusLike(newVal);
            listView.setItems(FXCollections.observableArrayList(suggestions));
        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String[] parts = newSelection.split(" - ", 2);
                String sku = parts[0];
                String description = (parts.length > 1) ? parts[1] : "";
                Platform.runLater(() -> {
                    targetSkuField.setText(sku);
                    if (targetDescriptionField != null) {
                        targetDescriptionField.setText(description);
                    }
                    searchField.clear();
                    listView.getItems().clear();
                    if (nextFocusTarget != null) {
                        nextFocusTarget.requestFocus();
                    }
                });
            }
        });
    }

    @FXML
    private void handlePrintGenericBarcode() {
        String fullBarcode = genericBarcodeField.getText().trim();
        if (fullBarcode.isEmpty()) return;
        String barcodeToPrint;
        if (barcode14Radio.isSelected()) {
            barcodeToPrint = fullBarcode.length() > 14 ? fullBarcode.substring(fullBarcode.length() - 14) : fullBarcode;
        } else if (barcode20Radio.isSelected()) {
            barcodeToPrint = fullBarcode.length() > 20 ? fullBarcode.substring(fullBarcode.length() - 20) : fullBarcode;
        } else {
            barcodeToPrint = fullBarcode;
        }
        String zpl = ZplPrinterService.getGenericBarcodeZpl(barcodeToPrint);
        if (printerService.sendZplToPrinter(printerNameField.getValue(), zpl)) {
            updateStatus("Printed barcode: " + barcodeToPrint, false);
            genericBarcodeField.clear();
        } else {
            updateStatus("Failed to send barcode to printer.", true);
        }
    }

    private void updateStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText("Status: " + message);
            statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleDeploySerialScan() {
        String sku = deploySkuField.getText().trim();
        String serial = deploySerialField.getText().trim();
        if (sku.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "SKU Missing", "Please select a SKU before scanning serials.");
            deploySkuSearchField.requestFocus();
            return;
        }
        if (serial.isEmpty()) return;
        String description = deployDescriptionField.getText();
        String adtZpl = ZplPrinterService.getAdtLabelZpl(sku, description);
        String serialZpl = ZplPrinterService.getSerialLabelZpl(sku, serial);
        boolean s1 = printerService.sendZplToPrinter(printerNameField.getValue(), adtZpl);
        boolean s2 = printerService.sendZplToPrinter(printerNameField.getValue(), serialZpl);
        if (s1 && s2) {
            updateStatus("Printed labels for S/N: " + serial, false);
            Platform.runLater(() -> {
                deploySerialField.clear();
                deploySerialField.requestFocus();
            });
        } else {
            updateStatus("Print failed for S/N: " + serial, true);
        }
    }

    @FXML
    private void handlePrintSingle() {
        String sku = singleSkuField.getText().trim();
        if (sku.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Missing", "Please search and select a SKU to print.");
            return;
        }
        String description = skuDAO.findSkuByNumber(sku).map(Sku::getDescription).orElse("Description not found");
        String zpl = ZplPrinterService.getAdtLabelZpl(sku, description);
        if (printerService.sendZplToPrinter(printerNameField.getValue(), zpl)) {
            updateStatus("Printed label for SKU: " + sku, false);
            singleSkuField.clear();
        } else {
            updateStatus("Failed to print label for SKU: " + sku, true);
        }
    }

    @FXML
    private void handlePrintMultiple() {
        String sku = multiSkuField.getText().trim();
        if (sku.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Missing", "Please select a SKU.");
            return;
        }
        int copies;
        try {
            copies = Integer.parseInt(multiCopiesField.getText());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid Input", "Number of copies must be a positive number.");
            return;
        }
        String description = skuDAO.findSkuByNumber(sku).map(Sku::getDescription).orElse("Description not found");
        String zpl = ZplPrinterService.getAdtLabelZpl(sku, description);
        int successCount = 0;
        for (int i = 0; i < copies; i++) {
            if (printerService.sendZplToPrinter(printerNameField.getValue(), zpl)) {
                successCount++;
            }
        }
        updateStatus("Printed " + successCount + " of " + copies + " labels for SKU: " + sku, successCount != copies);
    }

    @FXML
    private void handlePrintSerial() {
        String sku = serialSkuField.getText().trim();
        String serial = serialSerialField.getText().trim();
        if (sku.isEmpty() || serial.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Missing", "Please select a SKU and scan a serial.");
            return;
        }
        String zpl = ZplPrinterService.getSerialLabelZpl(sku, serial);
        if (printerService.sendZplToPrinter(printerNameField.getValue(), zpl)) {
            updateStatus("Printed serial label for S/N: " + serial, false);
            serialSerialField.clear();
            serialSerialField.requestFocus();
        } else {
            updateStatus("Failed to print serial label.", true);
        }
    }

    @FXML
    private void handlePrintAssetTag() {
        String serial;
        String imei = null;

        // The logic to get the data remains the same
        if (assetTagStandardRadio.isSelected()) {
            serial = assetSerialField.getText().trim();
            if (assetImeiCheckbox.isSelected()) {
                imei = assetImeiField.getText().trim();
            }
        } else {
            showAlert(Alert.AlertType.INFORMATION, "Not Implemented", "Combined iPad/Samsung format not yet implemented in this view.");
            return;
        }

        if (serial.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Missing", "Serial Number is required.");
            return;
        }

        // This is a new check: if IMEI is required but empty, stop.
        if (assetImeiCheckbox.isSelected() && (imei == null || imei.isEmpty())) {
            showAlert(Alert.AlertType.WARNING, "Input Missing", "IMEI is required when the checkbox is selected.");
            return;
        }

        String zpl = ZplPrinterService.getAssetTagZpl(serial, imei);

        if (printerService.sendZplToPrinter(assetPrinterNameField.getValue(), zpl)) {
            updateStatus("Printed asset tag for S/N: " + serial, false);

            // --- THIS IS THE NEW RESET LOGIC ---
            // Use Platform.runLater to ensure the UI updates happen smoothly after printing.
            Platform.runLater(() -> {
                assetSerialField.clear();
                assetImeiField.clear();
                assetSerialField.requestFocus(); // Move focus back to the start
            });
            // --- END OF RESET LOGIC ---

        } else {
            updateStatus("Failed to print asset tag.", true);
        }
    }

    @FXML
    private void handlePrintImageLabels() {
        String imageSku = imageSkuField.getText().trim();
        String deviceSku = imageDeviceSkuField.getText().trim();
        if (imageSku.isEmpty() || deviceSku.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Missing", "Image SKU and Device SKU are required.");
            return;
        }
        int copies;
        try {
            copies = Integer.parseInt(imageCopiesField.getText());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid Input", "Copies must be a positive number.");
            return;
        }
        String description = skuDAO.findSkuByNumber(imageSku).map(Sku::getDescription).orElse("Description not found");
        String zpl = ZplPrinterService.getImageLabelZpl(description, deviceSku, imagePrefixField.getText().trim());
        int successCount = 0;
        for (int i = 0; i < copies; i++) {
            if (printerService.sendZplToPrinter(printerNameField.getValue(), zpl)) successCount++;
        }
        updateStatus("Printed " + successCount + " of " + copies + " image labels.", successCount != copies);
    }

    private void populatePrinterComboBoxes() {
        List<String> printerNames = new ArrayList<>();
        for (PrintService printService : PrintServiceLookup.lookupPrintServices(null, null)) {
            printerNames.add(printService.getName());
        }
        if (printerNames.isEmpty()) {
            updateStatus("No printers found on this system.", true);
            return;
        }
        printerNameField.setItems(FXCollections.observableArrayList(printerNames));
        assetPrinterNameField.setItems(FXCollections.observableArrayList(printerNames));
        Optional<String> skuPrinter = printerNames.stream().filter(n -> n.toLowerCase().contains("gx")).findFirst();
        printerNameField.setValue(skuPrinter.orElse(printerNames.getFirst()));
        Optional<String> assetPrinter = printerNames.stream().filter(n -> n.toLowerCase().contains("zd")).findFirst();
        assetPrinterNameField.setValue(assetPrinter.orElse(printerNames.getFirst()));
    }
}