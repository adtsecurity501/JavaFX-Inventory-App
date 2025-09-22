package assettracking.manager;

import assettracking.dao.bulk.iPadProvisioningDAO;
import assettracking.data.bulk.BulkDevice;
import assettracking.db.DatabaseConnection;
import com.github.pjfanning.xlsx.StreamingReader;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeviceImportService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceImportService.class);
    private final iPadProvisioningDAO dao = new iPadProvisioningDAO();

    /**
     * Imports devices from a single file, merging with existing data.
     * This method is now also used by the iPad Provisioning tab.
     */
    public int importFromFile(File file) throws IOException, SQLException {
        List<BulkDevice> devices = assettracking.ui.ExcelReader.readDeviceFile(file);
        if (devices.isEmpty()) return 0;
        List<BulkDevice> hydratedDevices = mergeWithExistingData(devices);
        dao.upsertBulkDevices(hydratedDevices);
        return hydratedDevices.size();
    }

    /**
     * Processes a single file: reads it, merges with DB data, validates, and upserts.
     * This is now public to be used by FolderImportTask.
     */
    public ImportResult processAndUpsertData(File file) throws IOException, CsvException, SQLException {
        List<BulkDevice> parsedDevices = file.getName().toLowerCase().endsWith(".csv") ? streamCsvData(file) : streamExcelData(file);
        if (parsedDevices.isEmpty()) {
            return new ImportResult(file, 0, Collections.emptyList());
        }

        List<BulkDevice> hydratedDevices = mergeWithExistingData(parsedDevices);

        DeviceValidationResult validationResult = validateDevices(hydratedDevices);
        int successfulCount = 0;
        if (!validationResult.validDevices().isEmpty()) {
            successfulCount = performDatabaseUpsert(validationResult.validDevices());
        }
        return new ImportResult(file, successfulCount, validationResult.errors());
    }

    /**
     * Finds all valid import files within a list of directories.
     * This is now public to be used by FolderImportTask.
     */
    public List<File> findAllDeviceFiles(List<String> folderPaths) throws IOException {
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

    // --- Private helper methods below are unchanged ---

    private List<BulkDevice> mergeWithExistingData(List<BulkDevice> parsedDevices) throws SQLException {
        List<BulkDevice> hydratedList = new ArrayList<>();
        for (BulkDevice parsed : parsedDevices) {
            Optional<BulkDevice> existingOpt = dao.findDeviceBySerial(parsed.getSerialNumber());
            if (existingOpt.isPresent()) {
                BulkDevice existing = existingOpt.get();
                hydratedList.add(new BulkDevice(parsed.getSerialNumber(), (parsed.getImei() != null && !parsed.getImei().isEmpty()) ? parsed.getImei() : existing.getImei(), (parsed.getIccid() != null && !parsed.getIccid().isEmpty()) ? parsed.getIccid() : existing.getIccid(), (parsed.getCapacity() != null && !parsed.getCapacity().isEmpty()) ? parsed.getCapacity() : existing.getCapacity(), (parsed.getDeviceName() != null && !parsed.getDeviceName().isEmpty()) ? parsed.getDeviceName() : existing.getDeviceName(), parsed.getLastImportDate()));
            } else {
                hydratedList.add(parsed);
            }
        }
        return hydratedList;
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
        String unassignSql = "UPDATE Bulk_Devices SET ICCID = NULL WHERE ICCID = ? AND SerialNumber <> ?";
        String upsertSql = "MERGE INTO Bulk_Devices (SerialNumber, IMEI, ICCID, Capacity, DeviceName, LastImportDate) KEY(SerialNumber) VALUES (?, ?, ?, ?, ?, ?)";
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
}