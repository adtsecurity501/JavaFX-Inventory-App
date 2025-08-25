package assettracking.controller;

import assettracking.data.Sku;
import assettracking.dao.SkuDAO;
import assettracking.manager.StageManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.util.Optional;

public class SkuManagementController {

    @FXML private TableView<Sku> skuTable;
    @FXML private TableColumn<Sku, String> skuNumberCol;
    @FXML private TableColumn<Sku, String> modelNumberCol;
    @FXML private TableColumn<Sku, String> categoryCol;
    @FXML private TableColumn<Sku, String> manufacturerCol;
    @FXML private TableColumn<Sku, String> descriptionCol;
    @FXML private TextField searchField;
    @FXML private TextField skuNumberField;
    @FXML private TextField modelNumberField;
    @FXML private TextField categoryField;
    @FXML private TextField manufacturerField;
    @FXML private TextField descriptionField;
    @FXML private Label statusLabel;

    private final SkuDAO skuDAO = new SkuDAO();
    private final ObservableList<Sku> skuList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();

        FilteredList<Sku> filteredData = new FilteredList<>(skuList, p -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> filteredData.setPredicate(sku -> {
            if (newValue == null || newValue.isEmpty()) {
                return true;
            }
            String lowerCaseFilter = newValue.toLowerCase();

            if (sku.getSkuNumber() != null && sku.getSkuNumber().toLowerCase().contains(lowerCaseFilter)) return true;
            if (sku.getModelNumber() != null && sku.getModelNumber().toLowerCase().contains(lowerCaseFilter)) return true;
            if (sku.getCategory() != null && sku.getCategory().toLowerCase().contains(lowerCaseFilter)) return true;
            if (sku.getManufacturer() != null && sku.getManufacturer().toLowerCase().contains(lowerCaseFilter)) return true;
            return sku.getDescription() != null && sku.getDescription().toLowerCase().contains(lowerCaseFilter);
        }));

        SortedList<Sku> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(skuTable.comparatorProperty());

        skuTable.setItems(sortedData);

        // --- MODIFIED ---
        // Reverted from the incorrect method reference back to the working lambda expression.
        skuTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> populateForm(newSelection)
        );
        refreshTable();
    }

    private void setupTableColumns() {
        skuNumberCol.setCellValueFactory(new PropertyValueFactory<>("skuNumber"));
        modelNumberCol.setCellValueFactory(new PropertyValueFactory<>("modelNumber"));
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        manufacturerCol.setCellValueFactory(new PropertyValueFactory<>("manufacturer"));
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
    }

    private void refreshTable() {
        skuList.setAll(skuDAO.getAllSkus());
        handleNew();
    }

    private void populateForm(Sku sku) {
        if (sku == null) {
            handleNew();
        } else {
            skuNumberField.setText(sku.getSkuNumber());
            modelNumberField.setText(sku.getModelNumber());
            categoryField.setText(sku.getCategory());
            manufacturerField.setText(sku.getManufacturer());
            descriptionField.setText(sku.getDescription());
            skuNumberField.setEditable(false);
        }
    }

    @FXML
    private void handleNew() {
        skuTable.getSelectionModel().clearSelection();
        skuNumberField.clear();
        modelNumberField.clear();
        categoryField.clear();
        manufacturerField.clear();
        descriptionField.clear();
        skuNumberField.setEditable(true);
        skuNumberField.requestFocus();
        statusLabel.setText("");
    }

    @FXML
    private void handleDelete() {
        Sku selectedSku = skuTable.getSelectionModel().getSelectedItem();
        if (selectedSku == null) {
            StageManager.showAlert(skuTable.getScene().getWindow(), Alert.AlertType.WARNING, "No Selection", "Please select an SKU from the table to delete.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete SKU: " + selectedSku.getSkuNumber() + "?", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            if (skuDAO.deleteSku(selectedSku.getSkuNumber())) {
                statusLabel.setText("Successfully deleted SKU: " + selectedSku.getSkuNumber());
                refreshTable();
            } else {
                statusLabel.setText("Error: Could not delete SKU.");
            }
        }
    }

    @FXML
    private void handleSave() {
        String skuNumber = skuNumberField.getText();
        if (skuNumber == null || skuNumber.trim().isEmpty()) {
            StageManager.showAlert(skuTable.getScene().getWindow(), Alert.AlertType.WARNING, "Input Error", "SKU Number cannot be empty.");
            return;
        }

        Sku sku = new Sku();
        sku.setSkuNumber(skuNumber.trim());
        sku.setModelNumber(modelNumberField.getText());
        sku.setCategory(categoryField.getText());
        sku.setManufacturer(manufacturerField.getText());
        sku.setDescription(descriptionField.getText());

        boolean success;
        if (!skuNumberField.isEditable()) {
            success = skuDAO.updateSku(sku);
        } else {
            success = skuDAO.addSku(sku);
        }

        if (success) {
            statusLabel.setText("Successfully saved SKU: " + sku.getSkuNumber());
            refreshTable();
        } else {
            statusLabel.setText("Error: Could not save SKU. It may already exist.");
        }
    }
}