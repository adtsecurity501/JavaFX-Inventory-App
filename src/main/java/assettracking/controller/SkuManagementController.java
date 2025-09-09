package assettracking.controller;

import assettracking.dao.SkuDAO;
import assettracking.data.Sku;
import assettracking.manager.StageManager;
import assettracking.ui.AutoCompletePopup;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.Optional;

public class SkuManagementController {

    private final SkuDAO skuDAO = new SkuDAO();
    private final ObservableList<Sku> skuList = FXCollections.observableArrayList();
    @FXML
    private TableView<Sku> skuTable;
    @FXML
    private TableColumn<Sku, String> skuNumberCol;
    @FXML
    private TableColumn<Sku, String> modelNumberCol;
    @FXML
    private TableColumn<Sku, String> categoryCol;
    @FXML
    private TableColumn<Sku, String> manufacturerCol;
    @FXML
    private TableColumn<Sku, String> descriptionCol;
    @FXML
    private TextField searchField;
    @FXML
    private TextField skuNumberField;
    @FXML
    private TextField modelNumberField;
    @FXML
    private TextField categoryField;
    @FXML
    private TextField manufacturerField;
    @FXML
    private TextField descriptionField;
    @FXML
    private Label statusLabel;
    // --- NEW: References to the autocomplete popups ---
    private AutoCompletePopup categoryPopup;
    private AutoCompletePopup manufacturerPopup;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupAutocomplete(); // <-- ADD THIS NEW METHOD CALL

        FilteredList<Sku> filteredData = new FilteredList<>(skuList, p -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> filteredData.setPredicate(sku -> {
            if (newValue == null || newValue.isEmpty()) return true;
            String lowerCaseFilter = newValue.toLowerCase();
            if (sku.getSkuNumber() != null && sku.getSkuNumber().toLowerCase().contains(lowerCaseFilter)) return true;
            if (sku.getModelNumber() != null && sku.getModelNumber().toLowerCase().contains(lowerCaseFilter))
                return true;
            if (sku.getCategory() != null && sku.getCategory().toLowerCase().contains(lowerCaseFilter)) return true;
            if (sku.getManufacturer() != null && sku.getManufacturer().toLowerCase().contains(lowerCaseFilter))
                return true;
            return sku.getDescription() != null && sku.getDescription().toLowerCase().contains(lowerCaseFilter);
        }));

        SortedList<Sku> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(skuTable.comparatorProperty());
        skuTable.setItems(sortedData);

        skuTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> populateForm(newSelection)
        );
        refreshTable();
    }

    // --- NEW METHOD ---
    private void setupAutocomplete() {
        categoryPopup = new AutoCompletePopup(categoryField, () -> skuDAO.findDistinctValuesLike("category", categoryField.getText()));
        manufacturerPopup = new AutoCompletePopup(manufacturerField, () -> skuDAO.findDistinctValuesLike("manufac", manufacturerField.getText()));
    }

    private void setupTableColumns() {
        skuNumberCol.setCellValueFactory(new PropertyValueFactory<>("skuNumber"));
        modelNumberCol.setCellValueFactory(new PropertyValueFactory<>("modelNumber"));
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        manufacturerCol.setCellValueFactory(new PropertyValueFactory<>("manufacturer"));
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
    }

    private void refreshTable() {
        Task<List<Sku>> loadSkusTask = new Task<>() {
            @Override
            protected List<Sku> call() {
                return skuDAO.getAllSkus();
            }
        };
        loadSkusTask.setOnSucceeded(e -> {
            skuList.setAll(loadSkusTask.getValue());
            handleNew();
        });
        loadSkusTask.setOnFailed(e -> {
            Throwable ex = loadSkusTask.getException();
            statusLabel.setText("Error: Failed to load SKU data. See log for details.");
        });
        new Thread(loadSkusTask).start();
    }

    private void populateForm(Sku sku) {
        // --- THIS IS THE FIX ---
        // The logic to clear the form is now inside this method, but crucially,
        // it does NOT call requestFocus().

        // Suppress the listeners before programmatically changing the text
        categoryPopup.suppressListener(true);
        manufacturerPopup.suppressListener(true);

        if (sku == null) {
            // A selection was cleared. Clear the form fields but do not steal focus.
            skuNumberField.clear();
            modelNumberField.clear();
            categoryField.clear();
            manufacturerField.clear();
            descriptionField.clear();
            skuNumberField.setEditable(true);
            statusLabel.setText("");
        } else {
            // A valid SKU was selected. Populate the form.
            skuNumberField.setText(sku.getSkuNumber());
            modelNumberField.setText(sku.getModelNumber());
            categoryField.setText(sku.getCategory());
            manufacturerField.setText(sku.getManufacturer());
            descriptionField.setText(sku.getDescription());
            skuNumberField.setEditable(false);
        }

        // --- AND RE-ENABLE THEM AFTERWARDS ---
        // We use Platform.runLater to ensure this happens after the text change is fully processed
        Platform.runLater(() -> {
            categoryPopup.suppressListener(false);
            manufacturerPopup.suppressListener(false);
        });
    }

    @FXML
    private void handleNew() {
        // This method is now exclusively for the "New" button's action.
        // It clears the table selection (which will trigger populateForm(null) to clear the fields)
        // and then correctly sets the focus to the first input field for a new entry.
        skuTable.getSelectionModel().clearSelection();
        skuNumberField.requestFocus();
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

    // --- THIS METHOD IS MODIFIED ---
    @FXML
    private void handleSave() {
        String modelNumber = modelNumberField.getText();
        String description = descriptionField.getText();
        String skuNumber = skuNumberField.getText().trim(); // Use trimmed version for checks

        // Validation: A record must have at least a Model Number or a Description.
        if ((modelNumber == null || modelNumber.trim().isEmpty()) && (description == null || description.trim().isEmpty())) {
            StageManager.showAlert(skuTable.getScene().getWindow(), Alert.AlertType.WARNING, "Input Error", "An entry must have at least a Model Number or a Description.");
            return;
        }

        Sku sku = new Sku();
        sku.setSkuNumber(skuNumberField.getText()); // Use untrimmed for saving to allow spaces if needed
        sku.setModelNumber(modelNumberField.getText());
        sku.setCategory(categoryField.getText());
        sku.setManufacturer(manufacturerField.getText());
        sku.setDescription(descriptionField.getText());

        boolean success;
        Sku selectedSku = skuTable.getSelectionModel().getSelectedItem();

        // --- THIS IS THE CORRECTED LOGIC ---
        // Determine if we are updating an existing SKU or adding a new one.
        boolean isUpdateOperation = !skuNumberField.isEditable() &&
                selectedSku != null &&
                selectedSku.getSkuNumber().equals(sku.getSkuNumber());

        if (isUpdateOperation) {
            // We are editing an existing item selected from the table
            success = skuDAO.updateSku(sku);
        } else {
            // We are adding a new item
            if (skuNumber.isEmpty()) {
                // Let the database handle the blank SKU for autofill entries
                success = skuDAO.addSku(sku);
            } else {
                // If SKU is not empty, check if it already exists to prevent duplicates
                if (skuDAO.findSkuByNumber(skuNumber).isPresent()) {
                    StageManager.showAlert(skuTable.getScene().getWindow(), Alert.AlertType.ERROR, "Duplicate SKU", "An SKU with the number '" + skuNumber + "' already exists. Please use a unique SKU number.");
                    return; // Stop the save process
                }
                success = skuDAO.addSku(sku);
            }
        }

        if (success) {
            statusLabel.setText("Successfully saved entry.");
            refreshTable();
        } else {
            statusLabel.setText("Error: Could not save entry. The SKU might already exist.");
        }
    }
}