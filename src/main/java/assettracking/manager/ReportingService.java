package assettracking.manager;

import assettracking.db.DatabaseConnection;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Window;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFPivotTable;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A dedicated service for generating and exporting data reports (CSV, XLSX).
 * This class is decoupled from any specific UI controller.
 */
public class ReportingService {
    private static final Logger logger = LoggerFactory.getLogger(ReportingService.class);

    public void exportToXLSX(File file, Window owner) {
        String[] headers = {"Tracking Number", "First Name", "Last Name", "City", "State", "Zip", "Receive Date", "Category", "Description", "IMEI", "Serial Number", "Status Change Date", "Status", "Sub Status", "Days in Current Status", "Total Days to Process"};

        String query = "SELECT p.tracking_number, p.first_name, p.last_name, p.city, p.state, p.zip_code, p.receive_date, " + "re.category, re.description, re.imei, re.serial_number, ds.last_update AS status_change_date, " + "ds.status, ds.sub_status " + "FROM Packages p JOIN Receipt_Events re ON p.package_id = re.package_id LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id " + "ORDER BY ds.last_update DESC NULLS LAST, p.receive_date DESC";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(query); ResultSet rs = stmt.executeQuery(); XSSFWorkbook workbook = new XSSFWorkbook()) {

            XSSFSheet dataSheet = workbook.createSheet("Full Data Report");
            XSSFSheet summarySheet = workbook.createSheet("Summary Dashboard");
            CreationHelper createHelper = workbook.getCreationHelper();
            Map<String, Integer> statusCounts = new HashMap<>();

            // Cell Styles
            CellStyle timestampCellStyle = workbook.createCellStyle();
            timestampCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            CellStyle dateCellStyle = workbook.createCellStyle();
            dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle warningStyle = workbook.createCellStyle();
            warningStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            warningStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle dangerStyle = workbook.createCellStyle();
            dangerStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            dangerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            while (rs.next()) {
                Row row = dataSheet.createRow(rowNum++);
                // Populate row data (this logic is unchanged)
                row.createCell(0).setCellValue(rs.getString("tracking_number"));
                row.createCell(1).setCellValue(rs.getString("first_name"));
                row.createCell(2).setCellValue(rs.getString("last_name"));
                row.createCell(3).setCellValue(rs.getString("city"));
                row.createCell(4).setCellValue(rs.getString("state"));
                row.createCell(5).setCellValue(rs.getString("zip_code"));
                Cell receiveDateCell = row.createCell(6);
                LocalDate receiveDate = rs.getObject("receive_date", LocalDate.class);
                if (receiveDate != null) {
                    receiveDateCell.setCellValue(receiveDate);
                    receiveDateCell.setCellStyle(dateCellStyle);
                }
                row.createCell(7).setCellValue(rs.getString("category"));
                row.createCell(8).setCellValue(rs.getString("description"));
                row.createCell(9).setCellValue(rs.getString("imei"));
                row.createCell(10).setCellValue(rs.getString("serial_number"));
                Cell statusDateCell = row.createCell(11);
                LocalDateTime statusTimestamp = rs.getObject("status_change_date", LocalDateTime.class);
                if (statusTimestamp != null) {
                    statusDateCell.setCellValue(statusTimestamp);
                    statusDateCell.setCellStyle(timestampCellStyle);
                }
                String status = rs.getString("status");
                row.createCell(12).setCellValue(status);
                row.createCell(13).setCellValue(rs.getString("sub_status"));
                statusCounts.merge(status != null ? status : "Not Set", 1, Integer::sum);
                if (statusTimestamp != null) {
                    long daysInStatus = ChronoUnit.DAYS.between(statusTimestamp.toLocalDate(), LocalDate.now());
                    Cell daysInStatusCell = row.createCell(14);
                    daysInStatusCell.setCellValue(daysInStatus);
                    if (daysInStatus > 30) daysInStatusCell.setCellStyle(dangerStyle);
                    else if (daysInStatus > 14) daysInStatusCell.setCellStyle(warningStyle);
                }
                if (receiveDate != null && statusTimestamp != null) {
                    long daysToProcess = ChronoUnit.DAYS.between(receiveDate, statusTimestamp.toLocalDate());
                    row.createCell(15).setCellValue(daysToProcess);
                }
            }

            if (rowNum > 1) {
                AreaReference tableArea = workbook.getCreationHelper().createAreaReference(new CellReference(0, 0), new CellReference(rowNum - 1, headers.length - 1));
                XSSFTable table = dataSheet.createTable(tableArea);
                table.setDisplayName("AssetData");
                table.setName("AssetData");
                CTTable cttable = table.getCTTable();
                cttable.addNewAutoFilter().setRef(tableArea.formatAsString());
                CTTableStyleInfo styleInfo = cttable.addNewTableStyleInfo();
                styleInfo.setName("TableStyleMedium2");
                styleInfo.setShowRowStripes(true);
                cttable.setTableStyleInfo(styleInfo);
            }

            for (int i = 0; i < headers.length; i++) dataSheet.autoSizeColumn(i);

            buildSummarySheet(summarySheet, rowNum - 1, headerStyle, statusCounts);
            if (rowNum > 1) {
                buildPivotTableSheet(workbook.createSheet("Pivot Table Analysis"), dataSheet, rowNum, headers.length);
            }

            workbook.setActiveSheet(0);

            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }
            Platform.runLater(() -> StageManager.showAlert(owner, Alert.AlertType.INFORMATION, "Success", "Export successful: " + file.getAbsolutePath()));

        } catch (Exception e) {
            System.err.println("Service error: " + e.getMessage());
            Platform.runLater(() -> StageManager.showAlert(owner, Alert.AlertType.ERROR, "Export Error", "An error occurred: " + e.getMessage()));
        }
    }

    public void exportToCSV(File file, Window owner) {
        String header = "Tracking Number,First Name,Last Name,City,State,Zip,Receive Date,Category,Description,IMEI,Serial Number,Status Change Date,Status,Sub Status";
        String query = "SELECT p.tracking_number, p.first_name, p.last_name, p.city, p.state, p.zip_code, p.receive_date, re.category, re.description, re.imei, re.serial_number, ds.last_update AS status_change_date, ds.status, ds.sub_status FROM Packages p JOIN Receipt_Events re ON p.package_id = re.package_id LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id ORDER BY ds.last_update DESC NULLS LAST, p.receive_date DESC";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(query); ResultSet rs = stmt.executeQuery(); PrintWriter writer = new PrintWriter(file)) {
            writer.println(header);
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(escapeCSV(rs.getString("tracking_number")));
                row.add(escapeCSV(rs.getString("first_name")));
                row.add(escapeCSV(rs.getString("last_name")));
                row.add(escapeCSV(rs.getString("city")));
                row.add(escapeCSV(rs.getString("state")));
                row.add(escapeCSV(rs.getString("zip_code")));
                row.add(escapeCSV(rs.getString("receive_date")));
                row.add(escapeCSV(rs.getString("category")));
                row.add(escapeCSV(rs.getString("description")));
                row.add(escapeCSV(rs.getString("imei")));
                row.add(escapeCSV(rs.getString("serial_number")));
                row.add(escapeCSV(rs.getString("status_change_date")));
                row.add(escapeCSV(rs.getString("status")));
                row.add(escapeCSV(rs.getString("sub_status")));
                writer.println(String.join(",", row));
            }
            Platform.runLater(() -> StageManager.showAlert(owner, Alert.AlertType.INFORMATION, "Success", "Export successful: " + file.getAbsolutePath()));
        } catch (SQLException e) {
            Platform.runLater(() -> StageManager.showAlert(owner, Alert.AlertType.ERROR, "Database Error", "Failed to query data for export: " + e.getMessage()));
        } catch (IOException e) {
            Platform.runLater(() -> StageManager.showAlert(owner, Alert.AlertType.ERROR, "Export Error", "Failed to write to file: " + e.getMessage()));
        }
    }

    private void buildSummarySheet(XSSFSheet sheet, int totalDevices, CellStyle headerStyle, Map<String, Integer> statusCounts) {
        sheet.createRow(0).createCell(0).setCellValue("Inventory & Performance Summary");
        sheet.getRow(0).getCell(0).setCellStyle(headerStyle);
        sheet.createRow(2).createCell(0).setCellValue("Total Receipt Events in Report:");
        sheet.getRow(2).createCell(1).setCellValue(totalDevices);
        sheet.createRow(4).createCell(0).setCellValue("Inventory by Status (Live Count):");
        sheet.getRow(4).getCell(0).setCellStyle(headerStyle);

        Map<String, Integer> sortedStatusCounts = statusCounts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        int summaryRowNum = 5;
        for (Map.Entry<String, Integer> entry : sortedStatusCounts.entrySet()) {
            Row row = sheet.createRow(summaryRowNum++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        summaryRowNum++;
        sheet.createRow(summaryRowNum).createCell(0).setCellValue("Performance Metrics (Based on this Report):");
        sheet.getRow(summaryRowNum).getCell(0).setCellStyle(headerStyle);
        sheet.createRow(summaryRowNum + 1).createCell(0).setCellValue("Average Days in Current Status");
        sheet.getRow(summaryRowNum + 1).createCell(1).setCellFormula("AVERAGE('Full Data Report'!O:O)");
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void buildPivotTableSheet(XSSFSheet pivotSheet, XSSFSheet dataSheet, int totalRows, int totalCols) {
        AreaReference source = new AreaReference(new CellReference(0, 0), new CellReference(totalRows - 1, totalCols - 1), SpreadsheetVersion.EXCEL2007);
        XSSFPivotTable pivotTable = pivotSheet.createPivotTable(source, new CellReference("A4"), dataSheet);
        pivotTable.addRowLabel(12);
        pivotTable.addRowLabel(7);
        pivotTable.addColumnLabel(DataConsolidateFunction.COUNT, 10, "Count of Devices");
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}