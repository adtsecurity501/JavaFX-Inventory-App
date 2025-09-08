// assettracking/label/service/LabelDataService.java
package assettracking.label.service;

import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LabelDataService {

    // Method to get all necessary label data from a single serial number
    public Optional<Map<String, String>> getLabelDataBySerial(String serialNumber) {
        // This query joins the tables to get SKU (model_number), description, serial, and IMEI.
        String sql = """
                    SELECT
                        re.model_number,
                        re.description,
                        re.serial_number,
                        re.IMEI
                    FROM Receipt_Events re
                    WHERE re.serial_number = ?
                    ORDER BY re.receipt_id DESC
                    LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serialNumber);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Map<String, String> data = new HashMap<>();
                data.put("sku", rs.getString("model_number"));
                data.put("description", rs.getString("description"));
                data.put("serial", rs.getString("serial_number"));
                data.put("imei", rs.getString("IMEI"));
                return Optional.of(data);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
}