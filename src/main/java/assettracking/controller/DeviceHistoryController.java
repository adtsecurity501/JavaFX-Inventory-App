package assettracking.controller;

import assettracking.db.DatabaseConnection;
import assettracking.data.DeviceStatusView;
import assettracking.manager.StageManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeviceHistoryController {

    @FXML private Label headerLabel;
    @FXML private TableView<DeviceStatusView> historyTable;
    @FXML private TableColumn<DeviceStatusView, String> receiveDateCol;
    @FXML private TableColumn<DeviceStatusView, String> statusCol;
    @FXML private TableColumn<DeviceStatusView, String> subStatusCol;
    @FXML private TableColumn<DeviceStatusView, String> lastUpdateCol;
    @FXML private TableColumn<DeviceStatusView, String> notesCol;

    private final ObservableList<DeviceStatusView> historyList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        receiveDateCol.setCellValueFactory(new PropertyValueFactory<>("receiveDate"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        subStatusCol.setCellValueFactory(new PropertyValueFactory<>("subStatus"));
        lastUpdateCol.setCellValueFactory(new PropertyValueFactory<>("lastUpdate"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("changeNote"));

        historyTable.setItems(historyList);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        historyTable.setPlaceholder(new Label("No historical events found for this device."));
    }

    public void initData(String serialNumber) {
        headerLabel.setText("History for Serial Number: " + serialNumber);

        String sql = "SELECT p.receive_date, ds.status, ds.sub_status, ds.last_update, ds.change_log " +
                "FROM Receipt_Events re " +
                "JOIN Packages p ON re.package_id = p.package_id " +
                "LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id " +
                "WHERE re.serial_number = ? " +
                "ORDER BY re.receipt_id DESC";

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serialNumber);
            ResultSet rs = stmt.executeQuery();

            historyList.clear();
            while (rs.next()) {
                historyList.add(new DeviceStatusView(
                        0,
                        serialNumber,
                        null, null, null,
                        rs.getString("status"),
                        rs.getString("sub_status"),
                        rs.getTimestamp("last_update") != null ? rs.getTimestamp("last_update").toString().substring(0, 19) : "",
                        rs.getString("receive_date"),
                        rs.getString("change_log"),
                        false
                ));
            }
        } catch (SQLException e) {
            // REFACTORED: Replaced printStackTrace with a user-facing alert
            StageManager.showAlert(headerLabel.getScene().getWindow(), Alert.AlertType.ERROR, "Database Error", "Failed to load device history: " + e.getMessage());
        }
    }
}