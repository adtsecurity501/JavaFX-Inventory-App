package assettracking.controller;

import assettracking.dao.PackageDAO;
import assettracking.data.Package;
import assettracking.db.DatabaseConnection;
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
import java.util.Optional;

public class PackageIntakeController {

    private final PackageDAO packageDAO = new PackageDAO();
    @FXML
    private TextField trackingField;
    @FXML
    private TextField firstNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField zipField;
    @FXML
    private TextField cityField;
    @FXML
    private TextField stateField;
    @FXML
    private Button startButton;
    @FXML
    private Label trackingErrorLabel;
    @FXML
    private Label zipErrorLabel;

    @FXML
    private void initialize() {
        zipField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
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

        Optional<Package> existingPkgOpt = packageDAO.findPackageByTracking(currentTracking);
        if (existingPkgOpt.isPresent()) {
            handleExistingPackage(existingPkgOpt.get());
            return;
        }

        String returnLabelSql = "SELECT contact_name, city, state, zip_code FROM Return_Labels WHERE RIGHT(tracking_number, 14) = ?";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(returnLabelSql)) {
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

        Optional<Package> existingPkgOpt = packageDAO.findPackageByTracking(tracking);
        if (existingPkgOpt.isPresent()) {
            handleExistingPackage(existingPkgOpt.get());
            return;
        }

        int packageId = packageDAO.addPackage(tracking, firstNameField.getText().trim(), lastNameField.getText().trim(), cityField.getText().trim(), stateField.getText().trim(), zipField.getText().trim(), LocalDate.now());
        if (packageId != -1) {
            Package pkg = new Package(packageId, tracking, firstNameField.getText().trim(), lastNameField.getText().trim(), cityField.getText().trim(), stateField.getText().trim(), zipField.getText().trim(), LocalDate.now());
            openPackageDetailWindow(pkg);
            clearForm();
        } else {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to create package in the database.");
        }
    }

    private void handleExistingPackage(Package existingPkg) {
        boolean openExisting = StageManager.showConfirmationDialog(getOwnerWindow(), "Duplicate Package Found", "A package with this tracking number already exists in the database.", "Do you want to open the existing package details?");
        if (openExisting) {
            openPackageDetailWindow(existingPkg);
            clearForm();
        } else {
            trackingErrorLabel.setText("Package already exists.");
            startButton.setDisable(true);
        }
    }

    private void handleZipLookup() {
        String zip = zipField.getText().trim();
        if (zip.matches("\\d{5}")) {
            try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT primary_city, state_code FROM ZipCodeData WHERE zip_code = ?")) {
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