package assettracking.controller;

import assettracking.dao.AssetDAO;
import assettracking.data.AssetInfo;
import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class AutofillManagementController {

    private final AssetDAO assetDAO = new AssetDAO();
    private final ObservableList<AssetInfo> autofillList = FXCollections.observableArrayList();

    @FXML
    private TableView<AssetInfo> autofillTable;
    // Remove skuCol from this line
    @FXML
    private TableColumn<AssetInfo, String> serialCol, makeCol, partNumberCol, descriptionCol, categoryCol;
    @FXML
    private TextField searchField;
    // Remove skuField from this line
    @FXML
    private TextField serialField, makeField, partNumberField, descriptionField, categoryField;
    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        setupTableColumns();

        FilteredList<AssetInfo> filteredData = new FilteredList<>(autofillList, p -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filteredData.setPredicate(asset -> {
            if (newVal == null || newVal.isEmpty()) return true;
            String lower = newVal.toLowerCase();
            return (asset.getSerialNumber() != null && asset.getSerialNumber().toLowerCase().contains(lower)) || (asset.getMake() != null && asset.getMake().toLowerCase().contains(lower)) || (asset.getModelNumber() != null && asset.getModelNumber().toLowerCase().contains(lower)) || (asset.getDescription() != null && asset.getDescription().toLowerCase().contains(lower))
                    // Remove the SKU check from the filter
                    || (asset.getCategory() != null && asset.getCategory().toLowerCase().contains(lower));
        }));

        SortedList<AssetInfo> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(autofillTable.comparatorProperty());
        autofillTable.setItems(sortedData);

        autofillTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newSelection) -> populateForm(newSelection));

        refreshTable();
    }

    private void setupTableColumns() {
        serialCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        makeCol.setCellValueFactory(new PropertyValueFactory<>("make"));
        partNumberCol.setCellValueFactory(new PropertyValueFactory<>("modelNumber"));
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
    }

    private void refreshTable() {
        assetDAO.getAllAutofillEntries().thenAccept(data -> Platform.runLater(() -> {
            autofillList.setAll(data);
            handleNew(); // Clears the form after refreshing the table
        }));
    }

    private void populateForm(AssetInfo asset) {
        if (asset == null) {
            handleNew();
        } else {
            serialField.setText(asset.getSerialNumber());
            makeField.setText(asset.getMake());
            partNumberField.setText(asset.getModelNumber());
            descriptionField.setText(asset.getDescription());
            categoryField.setText(asset.getCategory());
            // Remove the line that sets the skuField text
            serialField.setEditable(false);
        }
    }

    @FXML
    private void handleNew() {
        autofillTable.getSelectionModel().clearSelection();
        serialField.clear();
        makeField.clear();
        partNumberField.clear();
        descriptionField.clear();
        categoryField.clear();
        serialField.setEditable(true);
        statusLabel.setText("Ready to create a new entry.");
        serialField.requestFocus();
    }


    @FXML
    private void handleDelete() {
        AssetInfo selected = autofillTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "No Selection", "Please select an entry to delete.");
            return;
        }

        if (StageManager.showDeleteConfirmationDialog(getStage(), "autofill entry", selected.getSerialNumber())) {
            assetDAO.deleteAutofillEntry(selected.getSerialNumber()).thenAccept(success -> Platform.runLater(() -> {
                if (success) {
                    refreshTable();
                    statusLabel.setText("Entry deleted successfully.");
                } else {
                    StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Error", "Could not delete the selected entry.");
                }
            }));
        }
    }

    @FXML
    private void handleSave() {
        String serial = serialField.getText();
        if (serial == null || serial.trim().isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "Serial Number cannot be empty.");
            return;
        }

        AssetInfo asset = new AssetInfo();
        asset.setSerialNumber(serial.trim());
        asset.setMake(makeField.getText());
        asset.setModelNumber(partNumberField.getText());
        asset.setDescription(descriptionField.getText());
        asset.setCategory(categoryField.getText());
        // Remove the line that sets the SkuNumber on the asset

        if (!serialField.isEditable()) {
            assetDAO.updateAutofillEntry(asset).thenAccept(success -> Platform.runLater(() -> {
                if (success) {
                    statusLabel.setText("Successfully updated: " + asset.getSerialNumber());
                    refreshTable();
                } else {
                    statusLabel.setText("Failed to update entry.");
                }
            }));
        } else {
            assetDAO.addAutofillEntry(asset).thenAccept(success -> Platform.runLater(() -> {
                if (success) {
                    statusLabel.setText("Successfully added: " + asset.getSerialNumber());
                    refreshTable();
                } else {
                    statusLabel.setText("Failed to add entry. Serial may already exist.");
                }
            }));
        }
    }

    private Stage getStage() {
        return (Stage) autofillTable.getScene().getWindow();
    }
}