package assettracking.controller;

import assettracking.db.DatabaseConnection;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ReevaluationPanelController {

    @FXML private TextField serialField;
    @FXML private Label infoLabel;

    @FXML
    private void handleSearch() {
        String serial = serialField.getText().trim();
        if (serial.isEmpty()) {
            showAlert("Input Required", "Please enter a serial number.");
            return;
        }

        infoLabel.setText("Searching...");

        String receiptQuery = "SELECT receipt_id FROM Receipt_Events WHERE serial_number = ? ORDER BY receipt_id DESC LIMIT 1";
        String dispositionQuery = "SELECT * FROM Disposition_Info WHERE receipt_id = ?";

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            int receiptId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(receiptQuery)) {
                stmt.setString(1, serial);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    receiptId = rs.getInt("receipt_id");
                } else {
                    infoLabel.setText("Serial number not found in any receipt events.");
                    return;
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(dispositionQuery)) {
                stmt.setInt(1, receiptId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Disposition Info for Receipt ID: ").append(receiptId).append("\n\n");
                    sb.append("Is Everon: ").append(rs.getBoolean("is_everon") ? "Yes" : "No").append("\n");
                    sb.append("Is Under Capacity: ").append(rs.getBoolean("is_under_capacity") ? "Yes" : "No").append("\n");
                    sb.append("Is End of Life: ").append(rs.getBoolean("is_end_of_life") ? "Yes" : "No").append("\n");
                    sb.append("Is Phone: ").append(rs.getBoolean("is_phone") ? "Yes" : "No").append("\n");
                    sb.append("Other Disqualification: ").append(rs.getString("other_disqualification") != null ? rs.getString("other_disqualification") : "None");
                    infoLabel.setText(sb.toString());
                } else {
                    infoLabel.setText("No disposition info found for the most recent receipt of this serial.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Error retrieving data: " + e.getMessage());
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}