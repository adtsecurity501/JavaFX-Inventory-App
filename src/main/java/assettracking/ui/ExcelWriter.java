package assettracking.ui;

import assettracking.data.bulk.StagedDevice;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ExcelWriter {

    public static void writeTemplate(File templateFile, File outputFile, List<StagedDevice> data) throws IOException {
        try (FileInputStream fis = new FileInputStream(templateFile);
             Workbook workbook = new XSSFWorkbook(fis);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            Sheet sheet = workbook.getSheetAt(0);

            // The template's data headers are on row 4, so data starts on row 5.
            // In Apache POI, this is 0-indexed, so we start writing at index 4.
            int startRowIndex = 4;

            // Create a cell style to ensure IMEI and SIM are treated as Text, not numbers.
            CellStyle textStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            textStyle.setDataFormat(format.getFormat("@"));

            for (int i = 0; i < data.size(); i++) {
                StagedDevice device = data.get(i);
                // Get the existing row or create it if it doesn't exist (robustness).
                Row row = sheet.getRow(startRowIndex + i);
                if (row == null) {
                    row = sheet.createRow(startRowIndex + i);
                }

                // Populate cells by column index, getting the cell first to preserve style.
                getCell(row, 0).setCellValue(i + 1); // #
                getCell(row, 1).setCellValue("Verizon");
                getCell(row, 2).setCellValue("VER-942x");
                getCell(row, 3).setCellValue(561); // Using numeric type
                getCell(row, 4).setCellValue("New activation");
                getCell(row, 5).setCellValue("iPad");

                getCell(row, 6).setCellValue(device.getSerialNumber());

                Cell imeiCell = getCell(row, 7);
                imeiCell.setCellValue(device.getImei());
                imeiCell.setCellStyle(textStyle);

                Cell simCell = getCell(row, 8);
                simCell.setCellValue(device.getSim());
                simCell.setCellStyle(textStyle);

                getCell(row, 9).setCellValue("No");
                getCell(row, 10).setCellValue(device.getFirstName());
                getCell(row, 11).setCellValue(device.getLastName());
                getCell(row, 12).setCellValue(""); // Employee ID
                getCell(row, 13).setCellValue(device.getEmployeeEmail());
                getCell(row, 14).setCellValue(device.getSnReferenceNumber());
            }

            workbook.write(fos);
        }
    }

    /**
     * Helper method to get a cell from a row, creating it if it doesn't exist.
     * This preserves the rest of the row's formatting.
     */
    private static Cell getCell(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            cell = row.createCell(cellIndex);
        }
        return cell;
    }
}