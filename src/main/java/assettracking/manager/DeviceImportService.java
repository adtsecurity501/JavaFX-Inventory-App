package assettracking.manager;

import assettracking.dao.bulk.iPadProvisioningDAO;
import assettracking.data.bulk.BulkDevice;
import assettracking.db.DatabaseConnection;
import assettracking.ui.ExcelReader;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service to handle importing device data from both Excel and CSV files.
 */
public class DeviceImportService {

    private final iPadProvisioningDAO dao = new iPadProvisioningDAO();

    public DeviceImportService() {
    }

    // --- METHOD FOR IPAD PROVISIONING TAB (UNCHANGED) ---
    public int importFromFile(File file) throws IOException, SQLException {
        List<BulkDevice> devices = ExcelReader.readDeviceFile(file);
        if (devices.isEmpty()) return 0;
        dao.upsertBulkDevices(devices);
        return devices.size();
    }

    // --- NEW METHODS FOR DASHBOARD FOLDER SCANNING ---
    public String runFolderImport(List<String> folderPathsToScan, java.util.function.Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("Scanning for device files...");
        List<File> allFiles = findAllDeviceFiles(folderPathsToScan);
        if (allFiles.isEmpty()) {
            return "No device files found to process in the configured folders.";
        }
        progressCallback.accept(String.format("Found %d files. Starting import...", allFiles.size()));

        AtomicInteger totalUpserted = new AtomicInteger(0);
        for (int i = 0; i < allFiles.size(); i++) {
            File file = allFiles.get(i);
            progressCallback.accept(String.format("Processing file %d/%d: %s", i + 1, allFiles.size(), file.getName()));
            totalUpserted.addAndGet(processAndUpsertData(file));
        }

        return String.format("Import complete. Upserted/updated %d total device records from %d files.", totalUpserted.get(), allFiles.size());
    }

    private List<File> findAllDeviceFiles(List<String> folderPaths) {
        List<File> foundFiles = new ArrayList<>();
        for (String path : folderPaths) {
            try (Stream<Path> stream = Files.walk(Paths.get(path))) {
                foundFiles.addAll(stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return (name.endsWith(".csv") || name.endsWith(".xlsx")) && !name.startsWith("~");
                        })
                        .map(Path::toFile)
                        .collect(Collectors.toList()));
            } catch (IOException e) {
                System.err.println("Warning: Could not scan directory: " + path + ". Error: " + e.getMessage());
            }
        }
        foundFiles.sort(Comparator.comparingLong(File::lastModified));
        return foundFiles;
    }

    private int processAndUpsertData(File file) throws IOException, SQLException, CsvException {
        String fileName = file.getName().toLowerCase();
        List<BulkDevice> devicesToProcess;

        // *** THIS IS THE CORE FIX: DIFFERENT LOGIC FOR DIFFERENT FILE TYPES ***
        if (fileName.endsWith(".csv")) {
            devicesToProcess = readAndCleanCsvData(file);
        } else if (fileName.endsWith(".xlsx")) {
            devicesToProcess = readAndCleanExcelData(file);
        } else {
            return 0; // Skip unsupported files
        }

        if (devicesToProcess.isEmpty()) {
            return 0;
        }

        int[] recordsAffected;
        String unassignSql = "UPDATE Bulk_Devices SET ICCID = NULL WHERE ICCID = ? AND SerialNumber <> ?";
        String upsertSql = "MERGE INTO Bulk_Devices (SerialNumber, IMEI, ICCID, Capacity, DeviceName, LastImportDate) KEY(SerialNumber) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement unassignStmt = conn.prepareStatement(unassignSql);
                 PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {
                for (BulkDevice device : devicesToProcess) {
                    if (device.getIccid() != null && !device.getIccid().isEmpty()) {
                        unassignStmt.setString(1, device.getIccid());
                        unassignStmt.setString(2, device.getSerialNumber());
                        unassignStmt.executeUpdate();
                    }
                    upsertStmt.setString(1, device.getSerialNumber());
                    upsertStmt.setString(2, device.getImei());
                    upsertStmt.setString(3, device.getIccid());
                    upsertStmt.setString(4, device.getCapacity());
                    upsertStmt.setString(5, device.getDeviceName());
                    upsertStmt.setString(6, device.getLastImportDate());
                    upsertStmt.addBatch();
                }
                recordsAffected = upsertStmt.executeBatch();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            conn.commit();
        }
        return recordsAffected.length;
    }

    private List<BulkDevice> readAndCleanExcelData(File file) throws IOException {
        List<BulkDevice> devices = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {
            for (Sheet sheet : workbook) {
                processSheet(sheet, devices);
            }
        }
        return devices;
    }

    private List<BulkDevice> readAndCleanCsvData(File file) throws IOException, CsvException {
        List<BulkDevice> devices = new ArrayList<>();
        // Simulate a single "sheet" from the CSV data
        List<String[]> allRows;
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            allRows = reader.readAll();
        }
        Sheet mockSheet = createMockSheet(allRows);
        processSheet(mockSheet, devices);
        return devices;
    }

    private void processSheet(Sheet sheet, List<BulkDevice> devices) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Integer headerRowIndex = findHeaderRow(sheet, "serial number", "serial");
        if (headerRowIndex == null) return;

        Map<String, Integer> headers = getHeaderMap(sheet.getRow(headerRowIndex));
        if (!headers.containsKey("serial number") && !headers.containsKey("serial")) return;

        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String serial = getCleanValue(row, headers, "serial number", "serial");
            if (serial == null || serial.isEmpty()) continue;

            String iccid = getCleanValue(row, headers, "iccid", "sim");
            if (iccid != null && (iccid.length() < 18 || iccid.length() > 20)) {
                iccid = null;
            }

            devices.add(new BulkDevice(
                    serial.toUpperCase(),
                    getCleanValue(row, headers, "imei/meid", "imei"),
                    iccid,
                    getCleanValue(row, headers, "capacity"),
                    getCleanValue(row, headers, "name", "device name"),
                    now
            ));
        }
    }

    // Helper methods for parsing
    private Integer findHeaderRow(Sheet sheet, String... possibleHeaders) {
        for (int i = 0; i < Math.min(10, sheet.getLastRowNum() + 1); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                String cellValue = getCellValueAsString(cell).trim().toLowerCase();
                for (String header : possibleHeaders) {
                    if (cellValue.equals(header)) return i;
                }
            }
        }
        return null;
    }

    private Map<String, Integer> getHeaderMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                map.put(getCellValueAsString(cell).trim().toLowerCase(), cell.getColumnIndex());
            }
        }
        return map;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private String getCleanValue(Row row, Map<String, Integer> headers, String... possibleNames) {
        for (String name : possibleNames) {
            if (headers.containsKey(name)) {
                String val = getCellValueAsString(row.getCell(headers.get(name)));
                if (name.equals("iccid") || name.equals("sim") || name.equals("imei")) {
                    if (val.endsWith(".0")) val = val.substring(0, val.length() - 2);
                    val = val.replaceAll("[^0-9]", "");
                }
                return val.isEmpty() ? null : val;
            }
        }
        return null;
    }

    // This is a bit of a hack to reuse the sheet processing logic for CSVs
    private Sheet createMockSheet(List<String[]> csvData) throws IOException {
        Workbook workbook = WorkbookFactory.create(true);
        Sheet sheet = workbook.createSheet();
        for (int i = 0; i < csvData.size(); i++) {
            Row row = sheet.createRow(i);
            String[] rowData = csvData.get(i);
            for (int j = 0; j < rowData.length; j++) {
                row.createCell(j).setCellValue(rowData[j]);
            }
        }
        return sheet;
    }
}