package assettracking.controller;

import assettracking.db.DatabaseConnection;
import assettracking.data.DeviceStatusView;
import assettracking.data.Package;
import assettracking.ui.StageManager;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PackageDetailController {

    @FXML private Label headerLabel;
    @FXML private TableView<DeviceStatusView> assetsTable;
    @FXML private TableColumn<DeviceStatusView, String> assetSerialCol;
    @FXML private TableColumn<DeviceStatusView, String> assetCategoryCol;
    @FXML private TableColumn<DeviceStatusView, String> assetStatusCol;
    @FXML private TableColumn<DeviceStatusView, String> assetSubStatusCol;

    private Package currentPackage;
    private final ObservableList<DeviceStatusView> assetsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        assetSerialCol.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        assetCategoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        assetStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        assetSubStatusCol.setCellValueFactory(new PropertyValueFactory<>("subStatus"));
        assetsTable.setItems(assetsList);
        assetsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    public void initData(Package selectedPackage) {
        this.currentPackage = selectedPackage;
        headerLabel.setText("Package: " + currentPackage.getTrackingNumber() + " | " + currentPackage.getFirstName() + " " + currentPackage.getLastName());
        loadData();
    }

    public void refreshData() {
        loadData();
    }

    private void loadData() {
        assetsList.clear();
        if (currentPackage == null) return;

        String assetsSQL = "SELECT p.receive_date, re.serial_number, re.category, re.make, re.description, ds.status, ds.sub_status, ds.change_log " +
                "FROM Receipt_Events re " +
                "JOIN Packages p ON re.package_id = p.package_id " +
                "LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id " +
                "WHERE re.package_id = ?";

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(assetsSQL)) {
                stmt.setInt(1, currentPackage.getPackageId());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    assetsList.add(new DeviceStatusView(
                            0, rs.getString("serial_number"), rs.getString("category"),
                            rs.getString("make"), rs.getString("description"), rs.getString("status"),
                            rs.getString("sub_status"), null, rs.getString("receive_date"),
                            rs.getString("change_log"), false
                    ));
                }
            }
        } catch (SQLException e) {
            // REFACTORED: Replaced printStackTrace with a call to our new showAlert method
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Database Error", "Failed to load package details.");
        }
    }

    @FXML
    private void handleAddAsset() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/AddAssetDialog.fxml"));
            Parent root = loader.load();

            AddAssetDialogController controller = loader.getController();
            controller.initData(this.currentPackage, this);

            // REFACTORED: Using StageManager to create the window
            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Receive Asset(s) for Package " + currentPackage.getTrackingNumber(), root);
            stage.showAndWait();

        } catch (IOException e) {
            // REFACTORED: Replaced printStackTrace and local showAlert with StageManager
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Could not open the 'Add Asset' window.");
        }
    }

    @FXML
    private void handleFinish() {
        Stage stage = (Stage) headerLabel.getScene().getWindow();
        stage.close();
    }

    // REFACTORED: Removed local showAlert method in favor of StageManager.

    private Window getOwnerWindow() {
        return headerLabel.getScene().getWindow();
    }
}