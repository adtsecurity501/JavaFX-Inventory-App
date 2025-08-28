package assettracking.controller;

import assettracking.dao.AssetDAO;
import assettracking.dao.SkuDAO;
import assettracking.data.AssetInfo;
import assettracking.label.service.ZplPrinterService;
import assettracking.ui.AutoCompletePopup;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LabelPrintingController {

    // --- (All existing services and FXML components are the same) ---
    private final SkuDAO skuDAO = new SkuDAO();
    private final AssetDAO assetDAO = new AssetDAO();
    private final ZplPrinterService printerService = new ZplPrinterService();

    @FXML private ComboBox<String> printerNameField;
    @FXML private ComboBox<String> assetPrinterNameField;
    @FXML private Label statusLabel;
    @FXML private ToggleGroup menuGroup;
    @FXML private StackPane mainStackPane;
    @FXML private Pane welcomePane;
    @FXML private ToggleButton menuDeployDevice, menuPrintSingle, menuPrintMultiple, menuPrintSerial, menuPrintAssetTag, menuPrintBarcode, menuImageLabels;
    @FXML private ScrollPane deployDevicePane, printSinglePane, printMultiplePane, printSerialPane, printAssetTagPane, printBarcodePane, imageLabelsPane;
    @FXML private TextField deploySkuSearchField, deploySkuField, deployDescriptionField, deploySerialField;
    @FXML private ListView<String> deploySkuListView;
    @FXML private TextField singleSkuSearchField, singleSkuField;
    @FXML private ListView<String> singleSkuListView;
    @FXML private TextField multiSkuSearchField, multiSkuField, multiCopiesField;
    @FXML private ListView<String> multiSkuListView;
    @FXML private TextField serialSkuSearchField, serialSkuField, serialSerialField;
    @FXML private ListView<String> serialSkuListView;
    @FXML private ToggleGroup assetTagTypeGroup;
    @FXML private RadioButton assetTagStandardRadio;
    @FXML private VBox assetStandardPane, assetCombinedPane;
    @FXML private TextField assetSerialField, assetImeiField, assetCombinedField;
    @FXML private CheckBox assetImeiCheckbox;
    @FXML private TextField genericBarcodeField;
    @FXML private TextField imageSkuField, imageDeviceSkuField, imagePrefixField, imageCopiesField;

    // --- NEW FXML Fields for the barcode options ---
    @FXML private RadioButton barcodeFullRadio;
    @FXML private RadioButton barcode14Radio;
    @FXML private RadioButton barcode20Radio;

    @FXML
    public void initialize() {
        populatePrinterComboBoxes();

        setupSearchableSkuField(deploySkuSearchField, deploySkuListView, deploySkuField, deployDescriptionField, deploySerialField);
        setupSearchableSkuField(singleSkuSearchField, singleSkuListView, singleSkuField, null, singleSkuField);
        setupSearchableSkuField(multiSkuSearchField, multiSkuListView, multiSkuField, null, multiCopiesField);
        setupSearchableSkuField(serialSkuSearchField, serialSkuListView, serialSkuField, null, serialSerialField);

        setupAutocomplete(imageSkuField);
        setupAutocomplete(imageDeviceSkuField);

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
        assetImeiCheckbox.selectedProperty().addListener((obs, wasSelected, isSelected) -> assetImeiField.setDisable(!isSelected));
    }

    /**
     * MODIFIED to implement the new truncation logic based on which radio button is selected.
     */
    @FXML
    private void handlePrintGenericBarcode() {
        String fullBarcode = genericBarcodeField.getText().trim();
        if (fullBarcode.isEmpty()) return;

        String barcodeToPrint;

        if (barcode14Radio.isSelected()) {
            if (fullBarcode.length() > 14) {
                barcodeToPrint = fullBarcode.substring(fullBarcode.length() - 14);
            } else {
                barcodeToPrint = fullBarcode;
            }
        } else if (barcode20Radio.isSelected()) {
            if (fullBarcode.length() > 20) {
                barcodeToPrint = fullBarcode.substring(fullBarcode.length() - 20);
            } else {
                barcodeToPrint = fullBarcode;
            }
        } else { // barcodeFullRadio is selected by default
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

    // --- NO CHANGES TO ANY OTHER METHODS IN THIS FILE ---

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

    private void setupAutocomplete(TextField textField) {
        new AutoCompletePopup(textField, () -> skuDAO.findSkusLike(textField.getText()))
                .setOnSuggestionSelected(selectedValue -> {
                    String sku = selectedValue.split(" - ")[0];
                    textField.setText(sku);
                    textField.positionCaret(textField.getLength());
                });
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

    @FXML private void handlePrintSingle() {
        String sku = singleSkuField.getText().trim();
        if (sku.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Missing", "Please search and select a SKU to print.");
            return;
        }

        String description = skuDAO.findSkuByNumber(sku).map(s -> s.getDescription()).orElse("Description not found");

        String zpl = ZplPrinterService.getAdtLabelZpl(sku, description);
        if (printerService.sendZplToPrinter(printerNameField.getValue(), zpl)) {
            updateStatus("Printed label for SKU: " + sku, false);
            singleSkuField.clear();
        } else {
            updateStatus("Failed to print label for SKU: " + sku, true);
        }
    }

    @FXML private void handlePrintMultiple() {
        String sku = multiSkuField.getText().trim();
        if (sku.isEmpty()){
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

        String description = skuDAO.findSkuByNumber(sku).map(s -> s.getDescription()).orElse("Description not found");
        String zpl = ZplPrinterService.getAdtLabelZpl(sku, description);

        int successCount = 0;
        for (int i = 0; i < copies; i++) {
            if (printerService.sendZplToPrinter(printerNameField.getValue(), zpl)) {
                successCount++;
            }
        }
        updateStatus("Printed " + successCount + " of " + copies + " labels for SKU: " + sku, successCount != copies);
    }

    @FXML private void handlePrintSerial() {
        String sku = serialSkuField.getText().trim();
        String serial = serialSerialField.getText().trim();
        if (sku.isEmpty() || serial.isEmpty()){
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

    @FXML private void handlePrintAssetTag() {
        String serial;
        String imei = null;

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

        String zpl = ZplPrinterService.getAssetTagZpl(serial, imei);
        if (printerService.sendZplToPrinter(assetPrinterNameField.getValue(), zpl)) {
            updateStatus("Printed asset tag for S/N: " + serial, false);
            assetSerialField.clear();
            assetImeiField.clear();
        } else {
            updateStatus("Failed to print asset tag.", true);
        }
    }

    @FXML private void handlePrintImageLabels() {
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

        String description = skuDAO.findSkuByNumber(imageSku).map(s -> s.getDescription()).orElse("Description not found");

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
        printerNameField.setValue(skuPrinter.orElse(printerNames.get(0)));

        Optional<String> assetPrinter = printerNames.stream().filter(n -> n.toLowerCase().contains("zd")).findFirst();
        assetPrinterNameField.setValue(assetPrinter.orElse(printerNames.get(0)));
    }
}