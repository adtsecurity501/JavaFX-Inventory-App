package assettracking.manager;

import assettracking.dao.AssetDAO;
import assettracking.dao.ReceiptEventDAO;
import assettracking.data.AssetEntry;
import assettracking.data.AssetInfo;
import assettracking.data.Package;
import assettracking.data.ReceiptEvent;
import assettracking.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntakeService {

    private final AssetDAO assetDAO = new AssetDAO();
    private final ReceiptEventDAO receiptEventDAO = new ReceiptEventDAO();
    private final Package currentPackage;
    private final boolean isNewCondition;

    public IntakeService(Package currentPackage, boolean isNewCondition) {
        this.currentPackage = currentPackage;
        this.isNewCondition = isNewCondition;
    }

    public String processFromTextArea(String[] serialNumbers, AssetInfo details, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId) {
        if (serialNumbers.length == 0 || serialNumbers[0].isEmpty()) {
            return "Input Required: Please enter at least one serial number.";
        }

        int successCount = 0;
        Connection conn = null;

        try {
            conn = DatabaseConnection.getInventoryConnection();
            conn.setAutoCommit(false); // Start transaction

            for (String originalSerial : serialNumbers) {
                final String serial = originalSerial.trim();
                if (serial.isEmpty()) continue;

                AssetInfo finalDetails = assetDAO.findAssetBySerialNumber(conn, serial).orElse(details);
                processSingleAsset(conn, serial, finalDetails, isScrap, scrapStatus, scrapSubStatus, scrapReason, boxId);
                successCount++;
            }

            conn.commit(); // Commit the transaction
            return String.format("Successfully processed %d receipts.", successCount);

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                System.err.println("Critical Error: Failed to rollback transaction.");
            }
            System.err.println("Error in processFromTextArea: " + e.getMessage());
            return "Transaction failed and was rolled back. Error: " + e.getMessage();
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("Database error on close: " + e.getMessage());
            }
        }
    }

    public String processFromTable(List<AssetEntry> entries, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId) {
        if (entries.isEmpty()) {
            return "No devices in the table to process.";
        }

        int successCount = 0, duplicateCount = 0;
        Set<String> processedSerials = new HashSet<>();
        Connection conn = null;

        try {
            conn = DatabaseConnection.getInventoryConnection();
            conn.setAutoCommit(false); // Start transaction

            for (AssetEntry entry : entries) {
                String serial = entry.getSerialNumber().trim();
                if (serial.isEmpty() || processedSerials.contains(serial)) {
                    if (!serial.isEmpty()) duplicateCount++;
                    continue;
                }

                AssetInfo assetInfo = new AssetInfo();
                assetInfo.setMake(entry.getMake());
                assetInfo.setModelNumber(entry.getModelNumber());
                assetInfo.setDescription(entry.getDescription());
                assetInfo.setCategory(entry.getCategory());
                assetInfo.setImei(entry.getImei());

                AssetInfo finalDetails = assetDAO.findAssetBySerialNumber(conn, serial).orElse(assetInfo);
                processSingleAsset(conn, serial, finalDetails, isScrap, scrapStatus, scrapSubStatus, scrapReason, boxId);
                successCount++;
                processedSerials.add(serial);
            }

            conn.commit(); // Commit the transaction
            String result = String.format("Successfully processed %d assets.", successCount);
            if (duplicateCount > 0) result += " Skipped " + duplicateCount + " duplicate serial(s).";
            return result;

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                System.err.println("Critical Error: Failed to rollback transaction.");
            }
            System.err.println("Error in processFromTable: " + e.getMessage());
            return "Transaction failed and was rolled back. Error: " + e.getMessage();
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("Database error on close: " + e.getMessage());
            }
        }
    }

    // --- THIS IS THE FIX: Changed from 'private' to 'public' ---
    public void processSingleAsset(Connection conn, String serial, AssetInfo details, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId) throws SQLException {
        if (assetDAO.findAssetBySerialNumber(conn, serial).isEmpty()) {
            details.setSerialNumber(serial);
            assetDAO.addAsset(conn, details);
        }

        ReceiptEvent newReceipt = new ReceiptEvent(0, serial, currentPackage.getPackageId(), details.getCategory(), details.getMake(), details.getModelNumber(), details.getDescription(), details.getImei());
        int newReceiptId = receiptEventDAO.addReceiptEvent(conn, newReceipt);

        if (newReceiptId != -1) {
            createInitialStatus(conn, newReceiptId, isScrap, scrapStatus, scrapSubStatus, scrapReason, boxId);
        } else {
            throw new SQLException("Failed to create receipt for S/N: " + serial);
        }
    }

    private void createInitialStatus(Connection conn, int receiptId, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId) throws SQLException {
        String serialNumber = "";
        String getSerialSql = "SELECT serial_number FROM receipt_events WHERE receipt_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(getSerialSql)) {
            stmt.setInt(1, receiptId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                serialNumber = rs.getString("serial_number");
            }
        }

        if (!serialNumber.isEmpty()) {
            String getFlagSql = "SELECT flag_reason FROM flag_devices WHERE serial_number = ?";
            try (PreparedStatement stmt = conn.prepareStatement(getFlagSql)) {
                stmt.setString(1, serialNumber);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String flagReason = rs.getString("flag_reason");
                    String logMessage = "Flagged on intake. Reason: " + flagReason;

                    String statusSql = "INSERT INTO device_status (receipt_id, status, sub_status, last_update, change_log) VALUES (?, 'Flag!', 'Requires Review', CURRENT_TIMESTAMP, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(statusSql)) {
                        insertStmt.setInt(1, receiptId);
                        insertStmt.setString(2, logMessage);
                        insertStmt.executeUpdate();
                    }
                    return;
                }
            }
        }

        String finalStatus, finalSubStatus;
        String finalReason = null;

        if (isScrap) {
            finalStatus = scrapStatus;
            finalSubStatus = scrapSubStatus;
            if ("Disposed".equals(scrapStatus) && boxId != null && !boxId.isEmpty()) {
                finalReason = "Box ID: " + boxId + (scrapReason != null && !scrapReason.isEmpty() ? ". " + scrapReason : "");
            } else {
                finalReason = scrapReason;
            }
        } else if (isNewCondition) {
            finalStatus = "Processed";
            finalSubStatus = "Ready for Deployment";
        } else {
            finalStatus = "Intake";
            finalSubStatus = "In Evaluation";
        }

        String statusSql = "INSERT INTO device_status (receipt_id, status, sub_status, last_update) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement stmt = conn.prepareStatement(statusSql)) {
            stmt.setInt(1, receiptId);
            stmt.setString(2, finalStatus);
            stmt.setString(3, finalSubStatus);
            stmt.executeUpdate();
        }

        if (finalReason != null && !finalReason.isBlank()) {
            String dispositionSql = "INSERT INTO disposition_info (receipt_id, other_disqualification) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(dispositionSql)) {
                stmt.setInt(1, receiptId);
                stmt.setString(2, finalReason);
                stmt.executeUpdate();
            }
        }
    }
}