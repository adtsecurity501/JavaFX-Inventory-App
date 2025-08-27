package assettracking.controller;

import assettracking.db.DatabaseConnection;
import assettracking.data.Package;
import assettracking.dao.PackageDAO;
import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;

public class PackageIntakeController {

    @FXML private TextField trackingField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField zipField;
    @FXML private TextField cityField;
    @FXML private TextField stateField;
    @FXML private Button startButton;
    @FXML private Label trackingErrorLabel;
    @FXML private Label zipErrorLabel;

    private final PackageDAO packageDAO = new PackageDAO();

    @FXML
    private void initialize() {
        zipField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // Focus lost
                handleZipLookup();
            }
        });
        zipField.setOnAction(event -> handleZipLookup());
    }

    @FXML
    private void handleTrackingLookup() {
        String rawTracking = trackingField.getText().trim();
        String currentTracking = rawTracking.length() > 14 ? rawTracking.substring(rawTracking.length() - 14) : rawTracking;
        trackingField.setText(currentTracking);
        trackingErrorLabel.setText("");
        startButton.setDisable(false);

        if (currentTracking.isEmpty()) {
            trackingErrorLabel.setText("Tracking number is required.");
            return;
        }

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM Packages WHERE tracking_number = ?")) {

            stmt.setString(1, currentTracking);
            ResultSet rs = stmt.executeQuery();

            // STEP 1: Check if the query returned a result (i.e., the package exists)
            if (rs.next()) {
                Package existingPkg = new Package(rs.getInt("package_id"), rs.getString("tracking_number"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("city"), rs.getString("state"), rs.getString("zip_code"), LocalDate.parse(rs.getString("receive_date")));

                // STEP 2: Show the confirmation dialog to the user
                boolean openExisting = StageManager.showConfirmationDialog(
                        getOwnerWindow(),
                        "Duplicate Package Found",
                        "Package with tracking number '" + currentTracking + "' already exists.",
                        "Do you want to open this existing package?"
                );

                // STEP 3A: If the user chose "Yes"
                if (openExisting) {
                    // Open the detail window for the EXISTING package
                    openPackageDetailWindow(existingPkg);
                    // Clear the form for the next task
                    clearForm();
                }
                // STEP 3B: If the user chose "No"
                else {
                    // Display an error and disable the button to prevent mistakes
                    trackingErrorLabel.setText("Package already exists.");
                    startButton.setDisable(true);
                }
                return; // Stop further processing
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error checking for duplicate package: " + e.getMessage());
            return;
        }

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT contact_name, city, state, zip_code FROM Return_Labels WHERE substr(tracking_number, length(tracking_number) - 13) = ?")) {
            stmt.setString(1, currentTracking);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String contactName = rs.getString("contact_name");
                String[] nameParts = contactName.trim().split("\\s+");
                lastNameField.setText(nameParts.length > 1 ? nameParts[nameParts.length - 1] : "");
                firstNameField.setText(String.join(" ", Arrays.copyOf(nameParts, nameParts.length - 1)));
                cityField.setText(rs.getString("city"));
                stateField.setText(rs.getString("state"));
                zipField.setText(rs.getString("zip_code"));
                Platform.runLater(this::handleStartIntake);
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error looking up return label: " + e.getMessage());
        }
    }

    @FXML
    private void handleStartIntake() {
        String tracking = trackingField.getText().trim();
        if (tracking.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Required", "Tracking Number is required.");
            return;
        }

        Package pkg = new Package(0, tracking, firstNameField.getText().trim(), lastNameField.getText().trim(),
                cityField.getText().trim(), stateField.getText().trim(),
                zipField.getText().trim(), LocalDate.now());

        int packageId = packageDAO.addPackage(pkg);

        if (packageId != -1) {
            pkg.setPackageId(packageId);
            openPackageDetailWindow(pkg);
            clearForm();
        } else {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to create package. It might already exist.");
        }
    }

    private void handleZipLookup() {
        String zip = zipField.getText().trim();
        if (zip.matches("\\d{5}")) {
            try (Connection conn = DatabaseConnection.getInventoryConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT primary_city, state_code FROM ZipCodeData WHERE zip_code = ?")) {
                stmt.setString(1, zip);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    cityField.setText(rs.getString("primary_city"));
                    stateField.setText(rs.getString("state_code"));
                    zipErrorLabel.setText("");
                } else {
                    zipErrorLabel.setText("Zip not found");
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Database Error", "Error looking up zip code: " + ex.getMessage());
            }
        }
    }

    private void openPackageDetailWindow(Package pkg) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/PackageDetail.fxml"));
            Parent root = loader.load();
            PackageDetailController controller = loader.getController();
            controller.initData(pkg);

            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Package Details", root);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open package detail window.");
        }
    }

    private void clearForm() {
        trackingField.clear();
        firstNameField.clear();
        lastNameField.clear();
        zipField.clear();
        cityField.clear();
        stateField.clear();
        trackingErrorLabel.setText("");
        zipErrorLabel.setText("");
        trackingField.requestFocus();
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        StageManager.showAlert(getOwnerWindow(), alertType, title, message);
    }

    private Window getOwnerWindow() {
        return startButton.getScene().getWindow();
    }
}