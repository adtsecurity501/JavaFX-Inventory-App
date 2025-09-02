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

/**
 * Handles the business logic for processing and saving new asset intake events.
 * This class is decoupled from the UI.
 */
public class IntakeService {

    private final AssetDAO assetDAO = new AssetDAO();
    private final ReceiptEventDAO receiptEventDAO = new ReceiptEventDAO();
    private final Package currentPackage;
    private final boolean isNewCondition;

    public IntakeService(Package currentPackage, boolean isNewCondition) {
        this.currentPackage = currentPackage;
        this.isNewCondition = isNewCondition;
    }

    public String processFromTextArea(String[] serialNumbers, AssetInfo details, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId) throws SQLException {
        if (serialNumbers.length == 0 || serialNumbers[0].isEmpty()) {
            return "Input Required: Please enter at least one serial number.";
        }

        int successCount = 0, returnCount = 0, newCount = 0;
        StringBuilder errors = new StringBuilder();

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            conn.setAutoCommit(false);
            for (String originalSerial : serialNumbers) {
                final String serial = originalSerial.trim();
                if (serial.isEmpty()) continue;

                boolean isReturn = assetDAO.findAssetBySerialNumber(serial).isPresent();
                AssetInfo assetInfo = assetDAO.findAssetBySerialNumber(serial).orElse(details);
                assetInfo.setSerialNumber(serial);

                if (!isReturn) {
                    assetDAO.addAsset(assetInfo);
                    newCount++;
                } else {
                    returnCount++;
                }

                ReceiptEvent newReceipt = new ReceiptEvent(0, serial, currentPackage.getPackageId(), assetInfo.getCategory(), assetInfo.getMake(), assetInfo.getModelNumber(), assetInfo.getDescription(), assetInfo.getImei());
                int newReceiptId = receiptEventDAO.addReceiptEvent(newReceipt);

                if (newReceiptId != -1) {
                    createInitialStatus(conn, newReceiptId, isScrap, scrapStatus, scrapSubStatus, scrapReason, boxId);
                    successCount++;
                } else {
                    errors.append("Failed to create receipt for S/N: ").append(serial).append("\n");
                }
            }

            if (!errors.isEmpty()) {
                conn.rollback();
                return "Errors occurred:\n" + errors;
            } else {
                conn.commit();
                return String.format("Successfully processed %d receipts (%d new, %d returns).", successCount, newCount, returnCount);
            }
        }
    }

    public String processFromTable(List<AssetEntry> entries, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId) throws SQLException {
        if (entries.isEmpty()) {
            return "No devices in the table to process.";
        }

        int successCount = 0, duplicateCount = 0;
        StringBuilder errors = new StringBuilder();
        Set<String> processedSerials = new HashSet<>();

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            conn.setAutoCommit(false);
            for (AssetEntry entry : entries) {
                String serial = entry.getSerialNumber().trim();
                if (serial.isEmpty() || processedSerials.contains(serial)) {
                    if (!serial.isEmpty()) duplicateCount++;
                    continue;
                }

                AssetInfo assetInfo = new AssetInfo();
                assetInfo.setSerialNumber(serial);
                assetInfo.setMake(entry.getMake());
                assetInfo.setModelNumber(entry.getModelNumber());
                assetInfo.setDescription(entry.getDescription());
                assetInfo.setCategory(entry.getCategory());
                assetInfo.setImei(entry.getImei());

                if (assetDAO.findAssetBySerialNumber(serial).isEmpty()) {
                    assetDAO.addAsset(assetInfo);
                }

                ReceiptEvent newReceipt = new ReceiptEvent(0, serial, currentPackage.getPackageId(), assetInfo.getCategory(), assetInfo.getMake(), assetInfo.getModelNumber(), assetInfo.getDescription(), assetInfo.getImei());
                int newReceiptId = receiptEventDAO.addReceiptEvent(newReceipt);

                if (newReceiptId != -1) {
                    createInitialStatus(conn, newReceiptId, isScrap, scrapStatus, scrapSubStatus, scrapReason, boxId);
                    successCount++;
                    processedSerials.add(serial);
                } else {
                    errors.append("Failed to create receipt for S/N: ").append(serial).append("\n");
                }
            }

            if (!errors.isEmpty()) {
                conn.rollback();
                return "Errors occurred:\n" + errors;
            } else {
                conn.commit();
                String result = String.format("Successfully processed %d assets.", successCount);
                if (duplicateCount > 0) result += " Skipped " + duplicateCount + " duplicate serial(s).";
                return result;
            }
        }
    }

    private void createInitialStatus(Connection conn, int receiptId, boolean isScrap, String scrapStatus, String scrapSubStatus, String scrapReason, String boxId) throws SQLException {
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

        // This is an "upsert" operation: update if it exists, otherwise insert.
        String upsertStatusSql = "INSERT INTO Device_Status (receipt_id, status, sub_status, last_update) VALUES (?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT(receipt_id) DO UPDATE SET status=excluded.status, sub_status=excluded.sub_status, last_update=CURRENT_TIMESTAMP;";
        try (PreparedStatement stmt = conn.prepareStatement(upsertStatusSql)) {
            stmt.setInt(1, receiptId);
            stmt.setString(2, finalStatus);
            stmt.setString(3, finalSubStatus);
            stmt.executeUpdate();
        }

        String upsertDispositionSql = "INSERT INTO Disposition_Info (receipt_id, other_disqualification) VALUES (?, ?) ON CONFLICT(receipt_id) DO UPDATE SET other_disqualification=excluded.other_disqualification;";
        try (PreparedStatement stmt = conn.prepareStatement(upsertDispositionSql)) {
            stmt.setInt(1, receiptId);
            stmt.setString(2, finalReason);
            stmt.executeUpdate();
        }
    }
}