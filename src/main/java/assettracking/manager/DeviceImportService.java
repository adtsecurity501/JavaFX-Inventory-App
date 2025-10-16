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

    public int importFromFile(File file) throws IOException, SQLException {
        List<BulkDevice> devicesFromFile = streamExcelData(file);
        if (devicesFromFile.isEmpty()) {
            return 0;
        }
        List<BulkDevice> hydratedDevices = mergeWithExistingData(devicesFromFile);
        dao.upsertBulkDevices(hydratedDevices);
        return hydratedDevices.size();
    }

    public ImportResult processAndUpsertData(File file) throws IOException, CsvException, SQLException {
        logger.info("--- Starting Import Process for File: {} ---", file.getName());
        List<BulkDevice> parsedDevices = file.getName().toLowerCase().endsWith(".csv") ? streamCsvData(file) : streamExcelData(file);
        if (parsedDevices.isEmpty()) {
            logger.warn("No devices were parsed from file: {}.", file.getName());
            return new ImportResult(file, 0, Collections.emptyList());
        }
        logger.info("Parsed {} devices from {}. Merging with existing database data...", parsedDevices.size(), file.getName());
        List<BulkDevice> hydratedDevices = mergeWithExistingData(parsedDevices);
        logger.info("Validating {} merged devices...", hydratedDevices.size());
        DeviceValidationResult validationResult = validateDevices(hydratedDevices);
        int successfulCount = 0;
        if (!validationResult.validDevices().isEmpty()) {
            logger.info("Upserting {} valid devices to the database...", validationResult.validDevices().size());
            successfulCount = performDatabaseUpsert(validationResult.validDevices());
        }
        logger.info("--- Finished Import Process for File: {} ---", file.getName());
        return new ImportResult(file, successfulCount, validationResult.errors());
    }

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
        logger.info("Found {} files to process. Order (oldest to newest):", foundFiles.size());
        for (File f : foundFiles) {
            logger.info("  - {}", f.getName());
        }
        return foundFiles;
    }

    private List<BulkDevice> mergeWithExistingData(List<BulkDevice> parsedDevices) throws SQLException {
        List<String> serialsToFetch = parsedDevices.stream().map(BulkDevice::getSerialNumber).collect(Collectors.toList());

        Map<String, BulkDevice> existingDevicesMap = dao.findDevicesBySerials(serialsToFetch);
        logger.info("Found {} existing devices in the database out of {} parsed from the file.", existingDevicesMap.size(), parsedDevices.size());

        List<BulkDevice> hydratedList = new ArrayList<>();
        for (BulkDevice parsed : parsedDevices) {
            BulkDevice existing = existingDevicesMap.get(parsed.getSerialNumber());
            if (existing != null) {
                String mergedImei = (parsed.getImei() != null && !parsed.getImei().isEmpty()) ? parsed.getImei() : existing.getImei();
                String mergedIccid = (parsed.getIccid() != null && !parsed.getIccid().isEmpty()) ? parsed.getIccid() : existing.getIccid();
                String mergedCapacity = (parsed.getCapacity() != null && !parsed.getCapacity().isEmpty()) ? parsed.getCapacity() : existing.getCapacity();
                String mergedDeviceName = (parsed.getDeviceName() != null && !parsed.getDeviceName().isEmpty()) ? parsed.getDeviceName() : existing.getDeviceName();
                hydratedList.add(new BulkDevice(parsed.getSerialNumber(), mergedImei, mergedIccid, mergedCapacity, mergedDeviceName, parsed.getLastImportDate()));
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
            if (workbook.getNumberOfSheets() == 0) {
                logger.warn("Excel file '{}' is empty and has no sheets.", file.getName());
                return devices;
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headerMap = null;
            for (Row row : sheet) {
                if (headerMap == null) {
                    Map<String, Integer> potentialHeaders = getHeaderMap(row);
                    if (potentialHeaders.containsKey("serial") || potentialHeaders.containsKey("serial number")) {
                        headerMap = potentialHeaders;
                        continue;
                    } else {
                        continue;
                    }
                }
                processRow(row, headerMap, now).ifPresent(devices::add);
            }
            if (headerMap == null) {
                logger.error("Required 'Serial Number' or 'Serial' column not found in the first sheet of file {}. Aborting file read.", file.getName());
            }
        }
        return devices;
    }

    private Optional<BulkDevice> processRow(Row row, Map<String, Integer> headers, String now) {
        String serial = getCellValue(row, headers, "serial number", "serial");
        if (serial == null || serial.isEmpty()) return Optional.empty();

        String rawImei = getCleanedNumericValue(row, headers, "imei/meid", "imei");
        String rawSim = getCleanedNumericValue(row, headers, "iccid", "sim");
        String finalImei = null;
        String finalSim = null;

        if (rawImei != null && !rawImei.isEmpty()) {
            if (rawImei.length() == 15) {
                finalImei = rawImei;
            } else if (rawImei.length() >= 18 && rawImei.length() <= 20) {
                finalSim = rawImei;
            }
        }

        if (rawSim != null && !rawSim.isEmpty()) {
            if (rawSim.length() == 15) {
                if (finalImei == null) finalImei = rawSim;
            } else if (rawSim.length() >= 18 && rawSim.length() <= 20) {
                if (finalSim == null) finalSim = rawSim;
            }
        }

        logger.debug("Final values for serial {} -> IMEI: [{}], SIM: [{}]", serial, finalImei, finalSim);

        return Optional.of(new BulkDevice(serial.toUpperCase(), finalImei, finalSim, getCellValue(row, headers, "capacity"), getCellValue(row, headers, "name", "device name"), now));
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
            Integer index = headers.get(name);
            if (index != null) {
                Cell cell = row.getCell(index);
                if (cell != null && cell.getCellType() != CellType.BLANK) {
                    return switch (cell.getCellType()) {
                        case NUMERIC -> new java.math.BigDecimal(cell.getNumericCellValue()).toPlainString();
                        case STRING -> cell.getStringCellValue().trim();
                        default -> new DataFormatter().formatCellValue(cell).trim();
                    };
                }
            }
        }
        return null;
    }

    private String getCleanedNumericValue(Row row, Map<String, Integer> headers, String... possibleNames) {
        String val = getCellValue(row, headers, possibleNames);
        if (val == null) return null;
        return val.replaceAll("[^0-9]", "");
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

    private Optional<BulkDevice> processCsvRow(String[] line, Map<String, Integer> headers, String now) {
        String serial = getCsvValue(line, headers, "serial number", "serial");
        if (serial == null || serial.isEmpty()) return Optional.empty();

        String rawImei = getCleanedNumericCsvValue(line, headers, "imei/meid", "imei");
        String rawSim = getCleanedNumericCsvValue(line, headers, "iccid", "sim");
        String finalImei = null;
        String finalSim = null;

        if (rawImei != null && !rawImei.isEmpty()) {
            if (rawImei.length() == 15) {
                finalImei = rawImei;
            } else if (rawImei.length() >= 18 && rawImei.length() <= 20) {
                finalSim = rawImei;
            }
        }

        if (rawSim != null && !rawSim.isEmpty()) {
            if (rawSim.length() == 15) {
                if (finalImei == null) finalImei = rawSim;
            } else if (rawSim.length() >= 18 && rawSim.length() <= 20) {
                if (finalSim == null) finalSim = rawSim;
            }
        }

        return Optional.of(new BulkDevice(serial.toUpperCase(), finalImei, finalSim, getCsvValue(line, headers, "capacity"), getCsvValue(line, headers, "name", "device name"), now));
    }

    private String getCsvValue(String[] line, Map<String, Integer> headers, String... possibleNames) {
        for (String name : possibleNames) {
            Integer index = headers.get(name);
            if (index != null && index < line.length && line[index] != null && !line[index].trim().isEmpty()) {
                return line[index].trim();
            }
        }
        return null;
    }

    private String getCleanedNumericCsvValue(String[] line, Map<String, Integer> headers, String... possibleNames) {
        String val = getCsvValue(line, headers, possibleNames);
        if (val == null) return null;
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