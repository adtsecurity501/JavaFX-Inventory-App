package assettracking.controller;

import assettracking.dao.PackageDAO;
import assettracking.dao.ReceiptEventDAO;
import assettracking.dao.SkuDAO;
import assettracking.data.AssetInfo;
import assettracking.data.Package;
import assettracking.db.DatabaseConnection;
import assettracking.manager.IntakeService;
import assettracking.manager.StageManager;
import assettracking.ui.AutoCompletePopup;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class BulkIntakeDialogController {

    private final PackageDAO packageDAO = new PackageDAO();
    private final SkuDAO skuDAO = new SkuDAO();
    private AutoCompletePopup categoryPopup;
    private AutoCompletePopup makePopup;
    private AutoCompletePopup modelPopup;
    private AutoCompletePopup descriptionPopup;

    @FXML
    private ComboBox<Package> packageComboBox;
    @FXML
    private TextArea serialsTextArea;
    @FXML
    private TextField categoryField;
    @FXML
    private TextField makeField;
    @FXML
    private TextField modelField;
    @FXML
    private TextField descriptionField;
    @FXML
    private Button processButton;
    @FXML
    private Label successLabel;
    @FXML
    private ListView<String> successListView;
    @FXML
    private Label failedLabel;
    @FXML
    private ListView<String> failedListView;
    private DeviceStatusTrackingController parentController;

    public void init(DeviceStatusTrackingController parent) {
        this.parentController = parent;
    }

    @FXML
    public void initialize() {
        setupPackageComboBox();
        setupAutocomplete();
    }

    private void setupPackageComboBox() {
        // This converter now handles both displaying the Package and finding it from text
        packageComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Package object) {
                if (object == null) {
                    return "";
                }
                return object.getTrackingNumber() + " - " + object.getFirstName() + " " + object.getLastName();
            }

            @Override
            public Package fromString(String string) {
                // This is the crucial new logic. It finds the matching package from the items in the dropdown.
                return packageComboBox.getItems().stream().filter(item -> toString(item).equals(string)).findFirst().orElse(null);
            }
        });

        // This listener is still needed to perform the search as the user types
        packageComboBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                packageComboBox.getItems().clear();
                return;
            }

            // This ensures we don't search if the user just selected an item
            Package selected = packageComboBox.getSelectionModel().getSelectedItem();
            if (selected != null && newVal.equals(packageComboBox.getConverter().toString(selected))) {
                return;
            }

            Task<List<Package>> searchTask = new Task<>() {
                @Override
                protected List<Package> call() throws Exception {
                    return packageDAO.searchPackagesByTracking(newVal);
                }
            };

            searchTask.setOnSucceeded(e -> {
                List<Package> results = searchTask.getValue();
                packageComboBox.getItems().setAll(results);
                if (!results.isEmpty()) {
                    packageComboBox.show();
                } else {
                    packageComboBox.hide();
                }
            });

            searchTask.setOnFailed(e -> System.err.println("Database error searching for packages: " + e.getSource().getException().getMessage()));

            new Thread(searchTask).start();
        });
    }

    @FXML
    private void handleNewPackage() {
        String trackingNumber = "BULK_INTAKE_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        int packageId = packageDAO.addPackage(trackingNumber, "SYSTEM", "BULK", "DEPOT", "UT", "84660", LocalDate.now());

        if (packageId != -1) {
            Package newPackage = new Package(packageId, trackingNumber, "SYSTEM", "BULK", "DEPOT", "UT", "84660", LocalDate.now());
            packageComboBox.getItems().addFirst(newPackage);
            packageComboBox.getSelectionModel().select(newPackage);
            StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Package Created", "New package created with tracking number:\n" + trackingNumber);
        } else {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Error", "Could not create a new package.");
        }
    }

    @FXML
    private void handleProcessIntake() {
        Package selectedPackage = packageComboBox.getSelectionModel().getSelectedItem();
        String serialsText = serialsTextArea.getText();

        if (selectedPackage == null) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "You must select or create a package.");
            return;
        }
        if (serialsText == null || serialsText.trim().isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "Please paste at least one serial number.");
            return;
        }

        List<String> serials = Arrays.stream(serialsText.split("\\r?\\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        AssetInfo commonDetails = new AssetInfo();
        commonDetails.setCategory(categoryField.getText());
        commonDetails.setMake(makeField.getText());
        commonDetails.setModelNumber(modelField.getText());
        commonDetails.setDescription(descriptionField.getText());

        processButton.setDisable(true);
        successListView.getItems().clear();
        failedListView.getItems().clear();
        successLabel.setText("Processing...");
        failedLabel.setText("Processing...");

        Task<Void> intakeTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<String> success = new ArrayList<>();
                List<String> failed = new ArrayList<>();
                IntakeService intakeService = new IntakeService(selectedPackage, false); // Assuming refurbished

                try (Connection conn = DatabaseConnection.getInventoryConnection()) {
                    conn.setAutoCommit(false);
                    ReceiptEventDAO receiptDAO = new ReceiptEventDAO();
                    for (String serial : serials) {
                        // Check if serial already exists
                        if (receiptDAO.findMostRecentReceiptId(serial).isPresent()) {
                            failed.add(serial + " (Already exists in database)");
                        } else {
                            intakeService.processSingleAsset(conn, serial, commonDetails, false, null, null, null, null);
                            success.add(serial);
                        }
                    }
                    conn.commit();
                }

                Platform.runLater(() -> {
                    successListView.setItems(FXCollections.observableArrayList(success));
                    failedListView.setItems(FXCollections.observableArrayList(failed));
                    successLabel.setText(String.format("Successfully Intaken (%d)", success.size()));
                    failedLabel.setText(String.format("Failed / Skipped (%d)", failed.size()));
                });
                return null;
            }
        };

        intakeTask.setOnSucceeded(e -> {
            processButton.setDisable(false);
            if (parentController != null) {
                parentController.refreshData();
            }
        });
        intakeTask.setOnFailed(e -> {
            processButton.setDisable(false);
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Critical Error", "The bulk intake process failed: " + e.getSource().getException().getMessage());
        });
        new Thread(intakeTask).start();
    }

    @FXML
    private void handleClose() {
        getStage().close();
    }

    private void setupAutocomplete() {
        // Basic autocomplete for category and make
        categoryPopup = new AutoCompletePopup(categoryField, () -> skuDAO.findDistinctValuesLike("category", categoryField.getText()));
        makePopup = new AutoCompletePopup(makeField, () -> skuDAO.findDistinctValuesLike("manufac", makeField.getText()));

        // Advanced autocomplete for model and description that cross-populates other fields
        modelPopup = new AutoCompletePopup(modelField, () -> skuDAO.findModelNumbersLike(modelField.getText())).setOnSuggestionSelected(selectedValue -> skuDAO.findSkuDetails(selectedValue, "model_number").ifPresent(this::populateFieldsFromSku));

        descriptionPopup = new AutoCompletePopup(descriptionField, () -> skuDAO.findDescriptionsLike(descriptionField.getText())).setOnSuggestionSelected(selectedValue -> skuDAO.findSkuDetails(selectedValue, "description").ifPresent(this::populateFieldsFromSku));
    }

    private void populateFieldsFromSku(AssetInfo sku) {
        Platform.runLater(() -> {
            // Suppress listeners to prevent infinite loops while we programmatically set text
            modelPopup.suppressListener(true);
            descriptionPopup.suppressListener(true);
            categoryPopup.suppressListener(true);
            makePopup.suppressListener(true);

            // Set the text in all the fields
            modelField.setText(sku.getModelNumber());
            descriptionField.setText(sku.getDescription());
            categoryField.setText(sku.getCategory());
            makeField.setText(sku.getMake());

            // Re-enable the listeners
            modelPopup.suppressListener(false);
            descriptionPopup.suppressListener(false);
            categoryPopup.suppressListener(false);
            makePopup.suppressListener(false);
        });
    }

    private Stage getStage() {
        return (Stage) processButton.getScene().getWindow();
    }
}