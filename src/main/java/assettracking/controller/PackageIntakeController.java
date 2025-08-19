package assettracking.controller;

import assettracking.db.DatabaseConnection;
import assettracking.data.Package;
import assettracking.dao.PackageDAO;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

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

    private PackageDAO packageDAO = new PackageDAO();

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

        // 1. Check for duplicate package
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM Packages WHERE tracking_number = ?")) {
            stmt.setString(1, currentTracking);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Package existingPkg = new Package(rs.getInt("package_id"), rs.getString("tracking_number"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("city"), rs.getString("state"), rs.getString("zip_code"), LocalDate.parse(rs.getString("receive_date")));

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Duplicate Package Found");
                alert.setHeaderText("Package with tracking number '" + currentTracking + "' already exists.");
                alert.setContentText("Do you want to open this existing package?");
                Optional<ButtonType> result = alert.showAndWait();

                if (result.isPresent() && result.get() == ButtonType.OK) {
                    openPackageDetailWindow(existingPkg);
                    clearForm();
                } else {
                    trackingErrorLabel.setText("Package already exists.");
                    startButton.setDisable(true);
                }
                return; // Stop further processing
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error checking for duplicate package: " + e.getMessage());
            return;
        }

        // 2. If not a duplicate, check for return label info
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

                // Auto-start the intake process
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
            pkg.setPackageId(packageId); // Set the newly generated ID
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

            Stage stage = new Stage();
            stage.setTitle("Package Details: " + pkg.getTrackingNumber());
            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(Application.getUserAgentStylesheet(), getClass().getResource("/style.css").toExternalForm());
            stage.setScene(scene);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(startButton.getScene().getWindow());
            stage.show(); // Use show() instead of showAndWait() to allow the form to clear immediately
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
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}