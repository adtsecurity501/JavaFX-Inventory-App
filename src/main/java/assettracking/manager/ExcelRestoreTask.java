package assettracking.manager;

import assettracking.dao.AssetDAO;
import assettracking.dao.FlaggedDeviceDAO;
import assettracking.dao.PackageDAO;
import assettracking.dao.ReceiptEventDAO;
import assettracking.data.AssetInfo;
import assettracking.data.Package;
import assettracking.data.ReceiptEvent;
import assettracking.db.DatabaseConnection;
import javafx.concurrent.Task;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class ExcelRestoreTask extends Task<String> {

    private final File file;
    private final PackageDAO packageDAO = new PackageDAO();
    private final ReceiptEventDAO receiptEventDAO = new ReceiptEventDAO();
    private final AssetDAO assetDAO = new AssetDAO();
    private final FlaggedDeviceDAO flagDAO = new FlaggedDeviceDAO();

    public ExcelRestoreTask(File file) {
        this.file = file;
    }

    @Override
    protected String call() throws Exception {
        // This method's logic is unchanged, as it just orchestrates the calls.
        // The changes are in the helper methods it calls.
        updateMessage("Parsing Excel file...");
        List<RestoredDeviceData> devices = parseExcelFile(file);
        if (devices.isEmpty()) {
            return "No valid device data found in the selected Excel file.";
        }

        int newIntakes = 0;
        int statusUpdates = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();
        int totalRows = devices.size();

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInventoryConnection();
            conn.setAutoCommit(false);

            for (int i = 0; i < totalRows; i++) {
                RestoredDeviceData device = devices.get(i);

                updateProgress(i + 1, totalRows);
                updateMessage(String.format("Processing %d of %d: S/N %s", i + 1, totalRows, device.serialNumber()));

                try {
                    if ("Flag!".equalsIgnoreCase(device.status())) {
                        String flagReason = device.subStatus();
                        flagDAO.flagDevice(conn, device.serialNumber(), flagReason);
                        device = new RestoredDeviceData(device.trackingNumber(), device.firstName(), device.lastName(), device.city(), device.state(), device.zip(), device.receiveDate(), device.category(), device.description(), device.imei(), device.serialNumber(), device.statusChangeDate(), "Flag!", "Requires Review", device.boxId(), device.rowNum());
                    }

                    validateStatus(device.status(), device.subStatus());
                    Optional<Map<String, Object>> latestDbRecordOpt = findMostRecentDeviceRecord(conn, device.serialNumber());

                    if (latestDbRecordOpt.isEmpty()) {
                        if (device.trackingNumber() == null || device.trackingNumber().isBlank()) {
                            throw new ExcelRestoreException("New device is missing a Tracking Number.");
                        }
                        int packageId = getOrCreatePackage(conn, device);
                        performNewIntake(conn, device, packageId);
                        newIntakes++;
                    } else {
                        Map<String, Object> latestDbRecord = latestDbRecordOpt.get();
                        if ("Deleted (Mistake)".equals(latestDbRecord.get("sub_status"))) {
                            skippedCount++;
                            continue;
                        }
                        LocalDate dbReceiveDate = (LocalDate) latestDbRecord.get("receive_date");
                        LocalDateTime dbStatusDate = (LocalDateTime) latestDbRecord.get("last_update");
                        String dbTrackingNumber = (String) latestDbRecord.get("tracking_number");
                        int dbReceiptId = (int) latestDbRecord.get("receipt_id");

                        boolean isNewerDate = device.receiveDate().isAfter(dbReceiveDate);
                        boolean isSameDayReIntake = device.receiveDate().isEqual(dbReceiveDate) && !device.trackingNumber().equals(dbTrackingNumber);

                        if (isNewerDate || isSameDayReIntake) {
                            if (device.trackingNumber() == null || device.trackingNumber().isBlank()) {
                                throw new ExcelRestoreException("Re-intake of a device is missing a Tracking Number.");
                            }
                            int packageId = getOrCreatePackage(conn, device);
                            performNewIntake(conn, device, packageId);
                            newIntakes++;
                        } else if (device.statusChangeDate() != null && device.statusChangeDate().isAfter(dbStatusDate)) {
                            updateDeviceStatus(conn, dbReceiptId, device);
                            statusUpdates++;
                        } else {
                            skippedCount++;
                        }
                    }
                } catch (Exception e) {
                    errors.add(String.format("Row %d (S/N: %s): %s", device.rowNum(), device.serialNumber(), e.getMessage()));
                }
            }
            conn.commit();

            StringBuilder summary = new StringBuilder();
            summary.append(String.format("Restore complete!\n\n- New Intakes: %d\n- Status Updates: %d\n- Skipped (already up-to-date): %d", newIntakes, statusUpdates, skippedCount));
            if (!errors.isEmpty()) {
                summary.append(String.format("\n\n- Errors (skipped rows): %d\n", errors.size()));
                errors.stream().limit(10).forEach(err -> summary.append("- ").append(err).append("\n"));
                if (errors.size() > 10) summary.append("...and more.\n");
            }
            return summary.toString();

        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw new RuntimeException("A critical database error occurred and the entire restore was rolled back.", e);
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    private List<RestoredDeviceData> parseExcelFile(File file) throws IOException, ExcelRestoreException {
        List<RestoredDeviceData> devices = new ArrayList<>();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = getHeaderMap(sheet.getRow(0));

            List<String> requiredHeaders = List.of("serial number", "receive date", "status change date", "status", "sub status", "tracking number");
            List<String> missingHeaders = requiredHeaders.stream().filter(h -> !headers.containsKey(h)).collect(Collectors.toList());
            if (!missingHeaders.isEmpty()) {
                throw new ExcelRestoreException("The Excel file is missing required columns: " + String.join(", ", missingHeaders));
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String serial = getSafeCellValue(row, headers, "serial number");
                if (serial.isEmpty()) continue;

                try {
                    // MODIFIED: Read the Box ID from one of two possible column names
                    String boxId = getSafeCellValue(row, headers, "box id", "boxid");

                    devices.add(new RestoredDeviceData(getSafeCellValue(row, headers, "tracking number"), getSafeCellValue(row, headers, "first name"), getSafeCellValue(row, headers, "last name"), getSafeCellValue(row, headers, "city"), getSafeCellValue(row, headers, "state"), getSafeCellValue(row, headers, "zip"), LocalDate.parse(getSafeCellValue(row, headers, "receive date")), getSafeCellValue(row, headers, "category"), getSafeCellValue(row, headers, "description"), getSafeCellValue(row, headers, "imei"), serial.toUpperCase(), LocalDateTime.parse(getSafeCellValue(row, headers, "status change date"), dateTimeFormatter), getSafeCellValue(row, headers, "status"), getSafeCellValue(row, headers, "sub status"), boxId, // Pass the new Box ID
                            i + 1));
                } catch (DateTimeParseException e) {
                    System.err.println("Skipping Excel row " + (i + 1) + " due to invalid date format: " + e.getMessage());
                }
            }
        }
        return devices;
    }

    // MODIFIED: This method now includes the box_id in the INSERT statement
    private void performNewIntake(Connection conn, RestoredDeviceData device, int packageId) throws SQLException {
        AssetInfo assetInfo = new AssetInfo();
        assetInfo.setSerialNumber(device.serialNumber());
        assetInfo.setImei(device.imei());
        assetInfo.setCategory(device.category());
        assetInfo.setDescription(device.description());
        assetDAO.updateAsset(assetInfo);

        ReceiptEvent receipt = new ReceiptEvent(0, device.serialNumber(), packageId, device.category(), null, null, device.description(), device.imei());
        int newReceiptId = receiptEventDAO.addReceiptEvent(conn, receipt);

        String statusSql = "INSERT INTO Device_Status (receipt_id, status, sub_status, last_update, box_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(statusSql)) {
            stmt.setInt(1, newReceiptId);
            stmt.setString(2, device.status());
            stmt.setString(3, device.subStatus());
            stmt.setObject(4, device.statusChangeDate());
            stmt.setString(5, device.boxId().isEmpty() ? null : device.boxId()); // Use the Box ID
            stmt.executeUpdate();
        }
    }

    // MODIFIED: This method now includes the box_id in the UPDATE statement
    private void updateDeviceStatus(Connection conn, int receiptId, RestoredDeviceData device) throws SQLException {
        String sql = "UPDATE Device_Status SET status = ?, sub_status = ?, last_update = ?, box_id = ? WHERE receipt_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, device.status());
            stmt.setString(2, device.subStatus());
            stmt.setObject(3, device.statusChangeDate());
            stmt.setString(4, device.boxId().isEmpty() ? null : device.boxId()); // Use the Box ID
            stmt.setInt(5, receiptId);
            stmt.executeUpdate();
        }
    }

    // --- All other helper methods are unchanged ---
    private String getSafeCellValue(Row row, Map<String, Integer> headers, String... possibleNames) {
        for (String name : possibleNames) {
            Integer index = headers.get(name.toLowerCase());
            if (index != null) {
                Cell cell = row.getCell(index);
                if (cell != null) {
                    return new DataFormatter().formatCellValue(cell).trim();
                }
            }
        }
        return "";
    }

    private Map<String, Integer> getHeaderMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                map.put(new DataFormatter().formatCellValue(cell).trim().toLowerCase(), cell.getColumnIndex());
            }
        }
        return map;
    }

    private Optional<Map<String, Object>> findMostRecentDeviceRecord(Connection conn, String serialNumber) throws SQLException {
        String sql = """
                    SELECT ds.receipt_id, ds.status, ds.sub_status, ds.last_update, p.receive_date, p.tracking_number
                    FROM Device_Status ds
                    JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id
                    JOIN Packages p ON re.package_id = p.package_id
                    WHERE re.serial_number = ?
                    ORDER BY p.receive_date DESC, ds.last_update DESC
                    LIMIT 1
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialNumber.toUpperCase());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                record.put("receipt_id", rs.getInt("receipt_id"));
                record.put("status", rs.getString("status"));
                record.put("sub_status", rs.getString("sub_status"));
                record.put("last_update", rs.getObject("last_update", LocalDateTime.class));
                record.put("receive_date", rs.getObject("receive_date", LocalDate.class));
                record.put("tracking_number", rs.getString("tracking_number"));
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    private int getOrCreatePackage(Connection conn, RestoredDeviceData device) throws SQLException {
        String tracking = device.trackingNumber() != null ? device.trackingNumber().toUpperCase() : "";
        Optional<Package> pkgOpt = packageDAO.findPackageByTracking(conn, tracking);
        if (pkgOpt.isPresent()) {
            return pkgOpt.get().getPackageId();
        } else {
            String firstName = (device.firstName() == null || device.firstName().isBlank()) ? "SYSTEM" : device.firstName();
            String lastName = (device.lastName() == null || device.lastName().isBlank()) ? "RESTORE" : device.lastName();
            String city = (device.city() == null || device.city().isBlank()) ? "UNKNOWN" : device.city();
            String state = (device.state() == null || device.state().isBlank()) ? "XX" : device.state();
            String zip = (device.zip() == null || device.zip().isBlank()) ? "00000" : device.zip();

            return packageDAO.addPackage(conn, tracking, firstName, lastName, city, state, zip, device.receiveDate());
        }
    }

    private void validateStatus(String status, String subStatus) throws ExcelRestoreException {
        if (status == null || status.isBlank()) throw new ExcelRestoreException("Status field is blank.");
        if (subStatus == null || subStatus.isBlank()) throw new ExcelRestoreException("Sub Status field is blank.");
        if (!StatusManager.getStatuses().contains(status))
            throw new ExcelRestoreException("Invalid Status: '" + status + "' is not a recognized status.");
        if (!StatusManager.getSubStatuses(status).contains(subStatus))
            throw new ExcelRestoreException("Invalid Sub-Status: '" + subStatus + "' is not a valid option for '" + status + "'.");
    }

    // MODIFIED: Added boxId to the record
    private record RestoredDeviceData(String trackingNumber, String firstName, String lastName, String city,
                                      String state, String zip, LocalDate receiveDate, String category,
                                      String description, String imei, String serialNumber,
                                      LocalDateTime statusChangeDate, String status, String subStatus, String boxId,
                                      int rowNum) {
    }
}