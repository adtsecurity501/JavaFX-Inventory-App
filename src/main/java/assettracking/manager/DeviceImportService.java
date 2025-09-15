package assettracking.manager;

import assettracking.dao.bulk.iPadProvisioningDAO;
import assettracking.data.bulk.BulkDevice;
import assettracking.db.DatabaseConnection;
import assettracking.ui.ExcelReader;
import com.github.pjfanning.xlsx.StreamingReader;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeviceImportService {

    private final iPadProvisioningDAO dao = new iPadProvisioningDAO();

    // --- THIS IS THE METHOD THAT WAS MISSING ---

    /**
     * Imports device data from a single Excel file for the iPad Provisioning workflow.
     *
     * @param file The Excel file to import.
     * @return The number of devices successfully upserted.
     * @throws IOException  If the file cannot be read.
     * @throws SQLException If a database error occurs.
     */
    public int importFromFile(File file) throws IOException, SQLException {
        List<BulkDevice> devices = ExcelReader.readDeviceFile(file);
        if (devices.isEmpty()) return 0;
        dao.upsertBulkDevices(devices);
        return devices.size();
    }
    // --- END OF MISSING METHOD ---


    public List<ImportResult> runFolderImport(List<String> folderPathsToScan, Consumer<String> progressCallback) throws IOException {
        progressCallback.accept("Scanning for device files...");
        List<File> allFiles = findAllDeviceFiles(folderPathsToScan);
        if (allFiles.isEmpty()) {
            progressCallback.accept("No new device files found to process.");
            return Collections.emptyList();
        }

        List<ImportResult> results = new ArrayList<>();
        for (int i = 0; i < allFiles.size(); i++) {
            File file = allFiles.get(i);
            progressCallback.accept(String.format("Processing file %d/%d: %s", i + 1, allFiles.size(), file.getName()));
            try {
                results.add(processAndUpsertData(file));
            } catch (Exception e) {
                results.add(new ImportResult(file, 0, List.of("Critical error processing file: " + e.getMessage())));
            }
        }
        return results;
    }

    private ImportResult processAndUpsertData(File file) throws IOException, CsvException, SQLException {
        List<BulkDevice> parsedDevices = file.getName().toLowerCase().endsWith(".csv") ? streamCsvData(file) : streamExcelData(file);

        if (parsedDevices.isEmpty()) {
            return new ImportResult(file, 0, Collections.emptyList());
        }

        DeviceValidationResult validationResult = validateDevices(parsedDevices);
        int successfulCount = 0;
        if (!validationResult.validDevices().isEmpty()) {
            successfulCount = performDatabaseUpsert(validationResult.validDevices());
        }

        return new ImportResult(file, successfulCount, validationResult.errors());
    }

    private List<BulkDevice> streamExcelData(File file) throws IOException {
        List<BulkDevice> devices = new ArrayList<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(fis)) {

            for (Sheet sheet : workbook) {
                Map<String, Integer> headerMap = null;
                for (Row row : sheet) {
                    if (headerMap == null) {
                        headerMap = getHeaderMap(row);
                        if (!headerMap.containsKey("serial number") && !headerMap.containsKey("serial")) {
                            break;
                        }
                        continue;
                    }
                    processRow(row, headerMap, now).ifPresent(devices::add);
                }
            }
        }
        return devices;
    }

    private List<BulkDevice> streamCsvData(File file) throws IOException, CsvException {
        List<BulkDevice> devices = new ArrayList<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            String[] headerArray = reader.readNext();
            if (headerArray == null) return devices;

            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headerArray.length; i++) {
                headerMap.put(headerArray[i].trim().toLowerCase(), i);
            }

            if (!headerMap.containsKey("serial number") && !headerMap.containsKey("serial")) {
                return devices;
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                processCsvRow(line, headerMap, now).ifPresent(devices::add);
            }
        }
        return devices;
    }

    private Optional<BulkDevice> processRow(Row row, Map<String, Integer> headers, String now) {
        String serial = getCellValue(row, headers, "serial number", "serial");
        if (serial == null || serial.isEmpty()) return Optional.empty();

        String iccid = getCleanedNumericValue(row, headers, "iccid", "sim");
        if (iccid != null && (iccid.length() < 18 || iccid.length() > 20)) {
            iccid = null;
        }

        return Optional.of(new BulkDevice(serial.toUpperCase(), getCleanedNumericValue(row, headers, "imei/meid", "imei"), iccid, getCellValue(row, headers, "capacity"), getCellValue(row, headers, "name", "device name"), now));
    }

    private Optional<BulkDevice> processCsvRow(String[] line, Map<String, Integer> headers, String now) {
        String serial = getCsvValue(line, headers, "serial number", "serial");
        if (serial == null || serial.isEmpty()) return Optional.empty();

        String iccid = getCleanedNumericCsvValue(line, headers, "iccid", "sim");
        if (iccid != null && (iccid.length() < 18 || iccid.length() > 20)) {
            iccid = null;
        }

        return Optional.of(new BulkDevice(serial.toUpperCase(), getCleanedNumericCsvValue(line, headers, "imei/meid", "imei"), iccid, getCsvValue(line, headers, "capacity"), getCsvValue(line, headers, "name", "device name"), now));
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

    private String getCellValue(Row row, Map<String, Integer> headers, String... possibleNames) {
        for (String name : possibleNames) {
            if (headers.containsKey(name)) {
                Cell cell = row.getCell(headers.get(name));
                if (cell != null) {
                    return new DataFormatter().formatCellValue(cell).trim();
                }
            }
        }
        return null;
    }

    private String getCleanedNumericValue(Row row, Map<String, Integer> headers, String... possibleNames) {
        String val = getCellValue(row, headers, possibleNames);
        if (val == null) return null;
        if (val.endsWith(".0")) val = val.substring(0, val.length() - 2);
        return val.replaceAll("[^0-9]", "");
    }

    private String getCsvValue(String[] line, Map<String, Integer> headers, String... possibleNames) {
        for (String name : possibleNames) {
            if (headers.containsKey(name)) {
                int index = headers.get(name);
                if (index < line.length) {
                    return line[index].trim();
                }
            }
        }
        return null;
    }

    private String getCleanedNumericCsvValue(String[] line, Map<String, Integer> headers, String... possibleNames) {
        String val = getCsvValue(line, headers, possibleNames);
        if (val == null) return null;
        if (val.endsWith(".0")) val = val.substring(0, val.length() - 2);
        return val.replaceAll("[^0-9]", "");
    }

    private DeviceValidationResult validateDevices(List<BulkDevice> devices) {
        List<String> errors = new ArrayList<>();
        Map<String, BulkDevice> uniqueDevicesBySerial = new LinkedHashMap<>();
        for (BulkDevice device : devices) {
            uniqueDevicesBySerial.putIfAbsent(device.getSerialNumber(), device);
        }
        List<BulkDevice> deDuplicatedList = new ArrayList<>(uniqueDevicesBySerial.values());
        Map<String, List<BulkDevice>> devicesByImei = deDuplicatedList.stream().filter(d -> d.getImei() != null && !d.getImei().isEmpty()).collect(Collectors.groupingBy(BulkDevice::getImei));

        devicesByImei.entrySet().stream().filter(entry -> entry.getValue().size() > 1).forEach(entry -> {
            String imei = entry.getKey();
            String serials = entry.getValue().stream().map(BulkDevice::getSerialNumber).collect(Collectors.joining(", "));
            errors.add(String.format("Rejected: Duplicate IMEI [%s] found for serials: %s.", imei, serials));
        });

        Set<String> invalidImeis = devicesByImei.entrySet().stream().filter(entry -> entry.getValue().size() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
        List<BulkDevice> validDevices = deDuplicatedList.stream().filter(d -> d.getImei() == null || d.getImei().isEmpty() || !invalidImeis.contains(d.getImei())).collect(Collectors.toList());

        return new DeviceValidationResult(validDevices, errors);
    }

    private int performDatabaseUpsert(List<BulkDevice> devices) throws SQLException {
        String unassignSql = "UPDATE bulk_devices SET iccid = NULL WHERE iccid = ? AND serial_number <> ?";
        String upsertSql = "INSERT INTO bulk_devices (serial_number, imei, iccid, capacity, device_name, last_import_date) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (serial_number) DO UPDATE SET imei = EXCLUDED.imei, iccid = EXCLUDED.iccid, capacity = EXCLUDED.capacity, device_name = EXCLUDED.device_name, last_import_date = EXCLUDED.last_import_date";
        int[] upsertResults;

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement unassignStmt = conn.prepareStatement(unassignSql); PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {

                for (BulkDevice device : devices) {
                    if (device.getIccid() != null && !device.getIccid().isEmpty()) {
                        unassignStmt.setString(1, device.getIccid());
                        unassignStmt.setString(2, device.getSerialNumber());
                        unassignStmt.addBatch();
                    }
                    upsertStmt.setString(1, device.getSerialNumber());
                    upsertStmt.setString(2, device.getImei());
                    upsertStmt.setString(3, device.getIccid());
                    upsertStmt.setString(4, device.getCapacity());
                    upsertStmt.setString(5, device.getDeviceName());
                    upsertStmt.setString(6, device.getLastImportDate());
                    upsertStmt.addBatch();
                }
                unassignStmt.executeBatch();
                upsertResults = upsertStmt.executeBatch();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            conn.commit();
        }
        return (int) Arrays.stream(upsertResults).filter(i -> i >= 0).count();
    }

    private List<File> findAllDeviceFiles(List<String> folderPaths) throws IOException {
        List<File> foundFiles = new ArrayList<>();
        for (String path : folderPaths) {
            try (Stream<Path> stream = Files.walk(Paths.get(path))) {
                foundFiles.addAll(stream.filter(Files::isRegularFile).filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return (name.endsWith(".csv") || name.endsWith(".xlsx")) && !name.startsWith("~");
                }).map(Path::toFile).toList());
            } catch (IOException e) {
                throw new IOException("Could not scan directory: " + path, e);
            }
        }
        foundFiles.sort(Comparator.comparingLong(File::lastModified));
        return foundFiles;
    }
}