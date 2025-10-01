package assettracking.ui;

import assettracking.data.bulk.StagedDevice;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ExcelWriter {

    public static void writeTemplate(InputStream templateInputStream, File outputFile, List<StagedDevice> data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(templateInputStream); FileOutputStream fos = new FileOutputStream(outputFile)) {

            Sheet sheet = workbook.getSheetAt(0);
            int startRowIndex = 4;
            CellStyle textStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            textStyle.setDataFormat(format.getFormat("@"));

            for (int i = 0; i < data.size(); i++) {
                StagedDevice device = data.get(i);
                Row row = sheet.getRow(startRowIndex + i);
                if (row == null) {
                    row = sheet.createRow(startRowIndex + i);
                }

                getCell(row, 0).setCellValue(i + 1);
                getCell(row, 1).setCellValue(device.getCarrier());
                getCell(row, 2).setCellValue(device.getCarrierAccountNumber());
                getCell(row, 3).setCellValue(561);
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
                getCell(row, 12).setCellValue("");
                getCell(row, 13).setCellValue(device.getEmployeeEmail());
                getCell(row, 14).setCellValue(device.getSnReferenceNumber());
            }

            workbook.write(fos);
        }
    }

    private static Cell getCell(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            cell = row.createCell(cellIndex);
        }
        return cell;
    }
}