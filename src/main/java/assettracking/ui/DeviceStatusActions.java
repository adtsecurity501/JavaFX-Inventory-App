package assettracking.ui;

import assettracking.controller.DeviceHistoryController;
import assettracking.controller.DeviceStatusTrackingController;
import assettracking.controller.ScanUpdateController;
import assettracking.data.DeviceStatusView;
import assettracking.db.DatabaseConnection;
import assettracking.manager.StageManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
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
import java.util.ArrayList;
import java.util.List;


public class DeviceStatusActions {

    private final DeviceStatusTrackingController controller;

    public DeviceStatusActions(DeviceStatusTrackingController controller) {
        this.controller = controller;
    }

    /**
     * Opens the history window for the currently selected device in the table.
     * Shows a custom alert if no device is selected.
     */
    public void openDeviceHistoryWindow() {
        DeviceStatusView selectedDevice = controller.statusTable.getSelectionModel().getSelectedItem();
        if (selectedDevice != null) {
            openDeviceHistoryWindow(selectedDevice.getSerialNumber());
        } else {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "No Selection", "Please select a device to view its history.");
        }
    }

    /**
     * Creates and displays a custom window showing the historical events for a given serial number.
     *
     * @param serialNumber The serial number to look up.
     */
    public void openDeviceHistoryWindow(String serialNumber) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/DeviceHistory.fxml"));
            Parent root = loader.load();
            DeviceHistoryController historyController = loader.getController();
            historyController.initData(serialNumber);

            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Device History for " + serialNumber, root);
            stage.showAndWait();
        } catch (IOException e) {
            System.err.println("Service error: " + e.getMessage());
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Could not open the device history window.");
        }
    }

    /**
     * Creates and displays the custom window for scan-based status updates.
     */
    public void openScanUpdateWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ScanUpdate.fxml"));
            Parent root = loader.load();
            ScanUpdateController scanController = loader.getController();
            scanController.setParentController(controller);

            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Scan-Based Status Update", root);
            stage.showAndWait();
        } catch (IOException e) {
            System.err.println("Service error: " + e.getMessage());
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Error", "Could not open the Scan Update window.");
        }
    }

    /**
     * Initiates the file import process for flagged devices.
     */
    public void importFlags() {
        FlaggedDeviceImporter importer = new FlaggedDeviceImporter();
        importer.importFromFile((Stage) getOwnerWindow(), controller::refreshData);
    }


    public void exportToXLSX(File file) {
        String[] headers = {"Tracking Number", "First Name", "Last Name", "City", "State", "Zip", "Receive Date", "Category", "Description", "IMEI", "Serial Number", "Status Change Date", "Status", "Sub Status", "Days in Current Status", "Total Days to Process"};

        String query = "SELECT p.tracking_number, p.first_name, p.last_name, p.city, p.state, p.zip_code, p.receive_date, " + "re.category, re.description, re.imei, re.serial_number, ds.last_update AS status_change_date, " + "ds.status, ds.sub_status " + "FROM Packages p JOIN Receipt_Events re ON p.package_id = re.package_id LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id " + "ORDER BY ds.last_update DESC NULLS LAST, p.receive_date DESC";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(query); ResultSet rs = stmt.executeQuery(); XSSFWorkbook workbook = new XSSFWorkbook()) {

            XSSFSheet dataSheet = workbook.createSheet("Full Data Report");
            XSSFSheet summarySheet = workbook.createSheet("Summary Dashboard");

            CreationHelper createHelper = workbook.getCreationHelper();

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
                row.createCell(0).setCellValue(rs.getString("tracking_number"));
                row.createCell(1).setCellValue(rs.getString("first_name"));
                row.createCell(2).setCellValue(rs.getString("last_name"));
                row.createCell(3).setCellValue(rs.getString("city"));
                row.createCell(4).setCellValue(rs.getString("state"));
                row.createCell(5).setCellValue(rs.getString("zip_code"));

                // --- FIX: Use getObject() to get modern date/time types directly ---
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

                // --- FIX: Use getObject() for LocalDateTime as well ---
                Cell statusDateCell = row.createCell(11);
                LocalDateTime statusTimestamp = rs.getObject("status_change_date", LocalDateTime.class);
                if (statusTimestamp != null) {
                    statusDateCell.setCellValue(statusTimestamp);
                    statusDateCell.setCellStyle(timestampCellStyle);
                }

                row.createCell(12).setCellValue(rs.getString("status"));
                row.createCell(13).setCellValue(rs.getString("sub_status"));

                // --- Updated calculation logic to use the new types ---
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

            buildSummarySheet(summarySheet, rowNum - 1, headerStyle);

            if (rowNum > 1) {
                buildPivotTableSheet(workbook.createSheet("Pivot Table Analysis"), dataSheet, rowNum, headers.length);
            }

            workbook.setActiveSheet(0);

            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.INFORMATION, "Success", "Export successful: " + file.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Service error: " + e.getMessage());
            e.printStackTrace();
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Export Error", "An error occurred: " + e.getMessage());
        }
    }

    // --- NEW HELPER METHOD FOR SUMMARY SHEET ---
    private void buildSummarySheet(XSSFSheet sheet, int totalDevices, CellStyle headerStyle) {
        sheet.createRow(0).createCell(0).setCellValue("Inventory & Performance Summary");
        sheet.getRow(0).getCell(0).setCellStyle(headerStyle);

        sheet.createRow(2).createCell(0).setCellValue("Total Devices in Report:");
        sheet.getRow(2).createCell(1).setCellValue(totalDevices);

        sheet.createRow(4).createCell(0).setCellValue("Inventory by Status:");
        sheet.getRow(4).getCell(0).setCellStyle(headerStyle);
        // Formula to count devices in "Triage & Repair" status from the data sheet
        sheet.createRow(5).createCell(0).setCellValue("Triage & Repair");
        sheet.getRow(5).createCell(1).setCellFormula("COUNTIF('Full Data Report'!M:M, \"Triage & Repair\")");

        sheet.createRow(6).createCell(0).setCellValue("Ready for Deployment");
        sheet.getRow(6).createCell(1).setCellFormula("COUNTIF('Full Data Report'!N:N, \"Ready for Deployment\")");

        sheet.createRow(8).createCell(0).setCellValue("Performance Metrics:");
        sheet.getRow(8).getCell(0).setCellStyle(headerStyle);
        sheet.createRow(9).createCell(0).setCellValue("Average Days in Current Status");
        sheet.getRow(9).createCell(1).setCellFormula("AVERAGE('Full Data Report'!O:O)");

        // Auto-size columns
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    // --- NEW HELPER METHOD FOR PIVOT TABLE ---
    private void buildPivotTableSheet(XSSFSheet pivotSheet, XSSFSheet dataSheet, int totalRows, int totalCols) {
        AreaReference source = new AreaReference(new CellReference(0, 0), new CellReference(totalRows - 1, totalCols - 1), SpreadsheetVersion.EXCEL2007);

        // We now explicitly pass 'dataSheet' as the source of the data for the pivot table.
        XSSFPivotTable pivotTable = pivotSheet.createPivotTable(source, new CellReference("A4"), dataSheet);

        // Add ROW labels (what you group by)
        pivotTable.addRowLabel(12); // Status (Column M)
        pivotTable.addRowLabel(7);  // Category (Column H)

        // Add VALUES (what you count/sum)
        pivotTable.addColumnLabel(DataConsolidateFunction.COUNT, 10, "Count of Devices"); // Count of Serial Number (Column K)
    }

    /**
     * Exports the current device inventory to a CSV file.
     */
    public void exportToCSV(File file) {
        String header = "Tracking Number,First Name,Last Name,City,State,Zip,Receive Date,Category,Description,IMEI,Serial Number,Status Change Date,Status,Sub Status";
        String query = "SELECT p.tracking_number, p.first_name, p.last_name, p.city, p.state, p.zip_code, p.receive_date, " + "re.category, re.description, re.imei, re.serial_number, ds.last_update AS status_change_date, " + "ds.status, ds.sub_status " + "FROM Packages p JOIN Receipt_Events re ON p.package_id = re.package_id LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id " + "ORDER BY p.tracking_number, re.category";

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
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.INFORMATION, "Success", "Export successful: " + file.getAbsolutePath());
        } catch (SQLException e) {
            System.err.println("Service error: " + e.getMessage());
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Database Error", "Failed to query data for export: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Service error: " + e.getMessage());
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Export Error", "Failed to write to file: " + e.getMessage());
        }
    }

    /**
     * Helper method to escape characters in a string for CSV format.
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Helper method to get the parent window for new stages and dialogs.
     */
    private Window getOwnerWindow() {
        return controller.statusTable.getScene().getWindow();
    }

    // This record is used by the DAO but defined here for historical reasons.
    public record QueryAndParams(String sql, List<Object> params) {
    }
}