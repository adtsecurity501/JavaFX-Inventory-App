package assettracking.ui;

import assettracking.data.bulk.BulkDevice;
import assettracking.data.bulk.RosterEntry;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelReader {

    // The readDeviceFile method remains unchanged.
    public static List<BulkDevice> readDeviceFile(File file) throws IOException {
        List<BulkDevice> devices = new ArrayList<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = getHeaderMap(sheet.getRow(0));

            if (!headers.containsKey("serial number") || !headers.containsKey("imei/meid") || !headers.containsKey("iccid")) {
                throw new IOException("Device file is missing one or more required columns: 'Serial Number', 'IMEI/MEID', 'ICCID'");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // --- NEW: LOGIC TO SKIP REPEATED HEADER ROWS ---
                // Get the value from the first cell (or the expected 'UDID' column) to check
                String firstCellValue = getCellValueAsString(row.getCell(0)).trim();
                if ("UDID".equalsIgnoreCase(firstCellValue)) {
                    continue; // Skip this row as it's a header
                }
                // --- END NEW LOGIC ---

                String serial = getCellValueAsString(row.getCell(headers.get("serial number"))).trim().toUpperCase();
                if (serial.isEmpty()) continue;

                devices.add(new BulkDevice(
                        serial,
                        getCellValueAsString(row.getCell(headers.get("imei/meid"))),
                        getCellValueAsString(row.getCell(headers.get("iccid"))),
                        getCellValueAsString(row.getCell(headers.get("capacity"))),
                        getCellValueAsString(row.getCell(headers.get("name"))),
                        now
                ));
            }
        }
        return devices;
    }

    // --- THIS IS THE CORRECTED METHOD ---
    public static List<RosterEntry> readRosterFile(File file) throws IOException {
        List<RosterEntry> roster = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Roster file is empty or has no header row.");
            }

            Map<String, Integer> headers = getHeaderMap(headerRow);

            int employeeEmailIndex = -1;
            int emailColumnCount = 0;
            for (Cell cell : headerRow) {
                if ("email".equalsIgnoreCase(getCellValueAsString(cell).trim())) {
                    emailColumnCount++;
                    if (emailColumnCount == 2) {
                        employeeEmailIndex = cell.getColumnIndex();
                        break;
                    }
                }
            }

            // --- THIS IS THE FIX ---
            // The check for "country" has been removed from this validation block.
            if (!headers.containsKey("first name") || !headers.containsKey("last name") || employeeEmailIndex == -1 || !headers.containsKey("sn reference number")) {
                throw new IOException("Roster file is missing required columns. Ensure 'First name', 'Last name', 'SN Reference Number', and two separate 'Email' columns are present.");
            }

            // We now safely get the column index, defaulting to -1 if it's not found.
            int countryColIndex = headers.getOrDefault("country", -1);
            // --- END OF FIX ---

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String snRef = getCellValueAsString(row.getCell(headers.get("sn reference number")));
                if (snRef.isEmpty()) continue;

                // --- ANOTHER FIX ---
                // If the country column wasn't found, we use an empty string.
                String country = "";
                if (countryColIndex != -1) {
                    country = getCellValueAsString(row.getCell(countryColIndex));
                }
                // --- END OF FIX ---

                roster.add(new RosterEntry(
                        getCellValueAsString(row.getCell(headers.get("first name"))),
                        getCellValueAsString(row.getCell(headers.get("last name"))),
                        getCellValueAsString(row.getCell(employeeEmailIndex)),
                        snRef,
                        getCellValueAsString(row.getCell(headers.get("depot reference"))),
                        country // Pass the country value (which may be blank)
                ));
            }
        }
        return roster;
    }

    private static Map<String, Integer> getHeaderMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) return map;
        for (Cell cell : headerRow) {
            map.put(getCellValueAsString(cell).trim().toLowerCase(), cell.getColumnIndex());
        }
        return map;
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return formatter.formatCellValue(cell, cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator());
            } catch (Exception e) {
                return cell.toString(); // Fallback for complex formulas
            }
        }
        return formatter.formatCellValue(cell);
    }
}