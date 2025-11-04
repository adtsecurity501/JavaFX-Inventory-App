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
import javafx.stage.Stage;

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

        skuTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> populateForm(newSelection));
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
        loadSkusTask.setOnSucceeded(e -> Platform.runLater(() -> {
            // Use clear() and addAll() to prevent rendering bugs.
            skuList.clear();
            skuList.addAll(loadSkusTask.getValue());
            handleNew();
        }));
        loadSkusTask.setOnFailed(e -> Platform.runLater(() -> statusLabel.setText("Error: Failed to load SKU data.")));
        new Thread(loadSkusTask).start();
    }

    private void populateForm(Sku sku) {
        categoryPopup.suppressListener(true);
        manufacturerPopup.suppressListener(true);

        if (sku == null) {
            skuNumberField.clear();
            modelNumberField.clear();
            categoryField.clear();
            manufacturerField.clear();
            descriptionField.clear();
            skuNumberField.setEditable(true); // Editable for new entries
            statusLabel.setText("");
        } else {
            skuNumberField.setText(sku.getSkuNumber());
            modelNumberField.setText(sku.getModelNumber());
            categoryField.setText(sku.getCategory());
            manufacturerField.setText(sku.getManufacturer());
            descriptionField.setText(sku.getDescription());

            // --- THIS IS THE KEY LOGIC CHANGE ---
            // Only lock the SKU field if a SKU number already exists.
            if (sku.getSkuNumber() != null && !sku.getSkuNumber().isBlank()) {
                skuNumberField.setEditable(false);
            } else {
                skuNumberField.setEditable(true);
                skuNumberField.requestFocus(); // Prompt user to enter a SKU
            }
            // --- END OF CHANGE ---
        }

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
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "No Selection", "Please select an SKU to delete.");
            return;
        }
        if (StageManager.showDeleteConfirmationDialog(getStage(), "SKU", selectedSku.getSkuNumber())) {
            if (skuDAO.deleteSku(selectedSku.getSkuNumber())) {
                // Modify the local list directly. Do NOT call refreshTable().
                skuList.remove(selectedSku);
                statusLabel.setText("Successfully deleted SKU: " + selectedSku.getSkuNumber());
                handleNew();
            } else {
                statusLabel.setText("Error: Could not delete SKU from the database.");
            }
        }
    }

    private Stage getStage() {
        return (Stage) skuTable.getScene().getWindow();
    }

    @FXML
    private void handleSave() {
        String skuNumber = Optional.ofNullable(skuNumberField.getText()).orElse("").trim();
        String modelNumber = Optional.ofNullable(modelNumberField.getText()).orElse("");
        String description = Optional.ofNullable(descriptionField.getText()).orElse("");
        String category = Optional.ofNullable(categoryField.getText()).orElse("");
        String manufacturer = Optional.ofNullable(manufacturerField.getText()).orElse("");

        if (modelNumber.trim().isEmpty() && description.trim().isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Error", "An entry must have at least a Model Number or a Description.");
            return;
        }

        Sku selectedSku = skuTable.getSelectionModel().getSelectedItem();

        if (selectedSku == null) {
            // --- SCENARIO 1: ADDING A COMPLETELY NEW SKU ---
            Sku newSku = new Sku();
            newSku.setSkuNumber(skuNumber);
            newSku.setModelNumber(modelNumber);
            newSku.setDescription(description);
            newSku.setCategory(category);
            newSku.setManufacturer(manufacturer);

            if (!skuNumber.isEmpty() && skuDAO.findSkuByNumber(skuNumber).isPresent()) {
                StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Duplicate SKU", "An SKU with the number '" + skuNumber + "' already exists.");
                return;
            }

            if (skuDAO.addSku(newSku)) {
                skuList.add(newSku);
                statusLabel.setText("Successfully added new SKU.");
                handleNew();
            } else {
                statusLabel.setText("Error: Could not save new SKU.");
            }
        } else {
            // --- SCENARIO 2: EDITING AN EXISTING SKU ---
            if (!skuNumberField.isEditable()) {
                // This is a standard update for an entry that already has a SKU.
                selectedSku.setModelNumber(modelNumber);
                selectedSku.setDescription(description);
                selectedSku.setCategory(category);
                selectedSku.setManufacturer(manufacturer);

                if (skuDAO.updateSku(selectedSku)) {
                    statusLabel.setText("Successfully updated SKU: " + selectedSku.getSkuNumber());
                    skuTable.refresh();
                    handleNew();
                } else {
                    statusLabel.setText("Error: Could not update SKU.");
                }
            } else {
                // The SKU field was editable, meaning the original entry was SKU-less.
                if (!skuNumber.isBlank()) {
                    // User is ASSIGNING a NEW SKU to the SKU-less entry.
                    if (skuDAO.findSkuByNumber(skuNumber).isPresent()) {
                        StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Duplicate SKU", "An SKU with the number '" + skuNumber + "' already exists.");
                        return;
                    }
                    Sku updatedSku = createSkuFromForm();
                    if (skuDAO.replaceSku(selectedSku, updatedSku)) {
                        skuList.remove(selectedSku);
                        skuList.add(updatedSku);
                        statusLabel.setText("Successfully assigned SKU and updated entry.");
                        handleNew();
                    } else {
                        statusLabel.setText("Error: Could not replace SKU entry.");
                    }
                } else {
                    // THIS IS THE NEW LOGIC PATH:
                    // User is just EDITING a SKU-less entry and leaving it SKU-less.
                    Sku updatedSku = createSkuFromForm();
                    if (skuDAO.updateSkuLessEntry(selectedSku, updatedSku)) {
                        skuList.remove(selectedSku);
                        skuList.add(updatedSku);
                        statusLabel.setText("Successfully updated SKU-less entry.");
                        handleNew();
                    } else {
                        statusLabel.setText("Error: Could not update SKU-less entry.");
                    }
                }
            }
        }
    }

    // Add this new helper method to the controller to reduce code duplication
    private Sku createSkuFromForm() {
        Sku sku = new Sku();
        sku.setSkuNumber(Optional.ofNullable(skuNumberField.getText()).orElse("").trim());
        sku.setModelNumber(Optional.ofNullable(modelNumberField.getText()).orElse(""));
        sku.setDescription(Optional.ofNullable(descriptionField.getText()).orElse(""));
        sku.setCategory(Optional.ofNullable(categoryField.getText()).orElse(""));
        sku.setManufacturer(Optional.ofNullable(manufacturerField.getText()).orElse(""));
        return sku;
    }
}