package assettracking.ui;

import assettracking.db.DatabaseConnection;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class FlaggedDeviceImporter {

    public void importFromFile(Stage owner, Runnable onFinished) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Flagged Devices File");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"), new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(owner);

        if (selectedFile != null) {
            Task<String> importTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return processExcelFile(selectedFile);
                }
            };

            importTask.setOnSucceeded(e -> {
                showAlert(Alert.AlertType.INFORMATION, "Import Complete", importTask.getValue());
                if (onFinished != null) {
                    onFinished.run();
                }
            });

            importTask.setOnFailed(e -> {
                Throwable ex = importTask.getException();
                ex.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Import Failed", "An error occurred during the import: " + ex.getMessage());
            });

            new Thread(importTask).start();
        }
    }

    private String processExcelFile(File file) throws IOException, SQLException {
        Map<String, String> dataToImport = new LinkedHashMap<>();
        int skippedRowCount = 0;

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) {
                return "Error: File is empty or contains only a header row.";
            }

            Row headerRow = sheet.getRow(0);
            int serialNumberIndex = -1;
            int probableCauseIndex = -1;

            for (Cell cell : headerRow) {
                String headerText = getCellValueAsString(cell).trim().toLowerCase();
                if (headerText.equals("serial number") || headerText.equals("serial") || headerText.equals("s/n")) {
                    serialNumberIndex = cell.getColumnIndex();
                } else if (headerText.equals("probable cause") || headerText.contains("description") || headerText.contains("reason")) {
                    probableCauseIndex = cell.getColumnIndex();
                }
            }

            if (serialNumberIndex == -1 || probableCauseIndex == -1) {
                return "Error: Could not find required columns. Make sure 'Serial Number' and 'Probable Cause' (or 'Description') columns exist.";
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    skippedRowCount++;
                    continue;
                }
                String serial = getCellValueAsString(row.getCell(serialNumberIndex)).trim();
                String cause = getCellValueAsString(row.getCell(probableCauseIndex)).trim();
                if (!serial.isEmpty()) {
                    dataToImport.put(serial, cause.isEmpty() ? "Unknown Issue (Imported)" : cause);
                } else {
                    skippedRowCount++;
                }
            }
        }

        if (dataToImport.isEmpty()) {
            return "No unique, valid data found to import.";
        }

        String upsertSql = "INSERT INTO flag_devices (serial_number, status, sub_status, flag_reason) " + "VALUES (?, 'Flag!', 'Requires Review', ?) " + "ON CONFLICT (serial_number) DO UPDATE SET " + "status = EXCLUDED.status, " + "sub_status = EXCLUDED.sub_status, " + "flag_reason = EXCLUDED.flag_reason";
        int successfullyProcessedCount = 0;

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {
                for (Map.Entry<String, String> entry : dataToImport.entrySet()) {
                    upsertStmt.setString(1, entry.getKey());
                    upsertStmt.setString(2, entry.getValue());
                    upsertStmt.addBatch();
                }
                int[] batchResults = upsertStmt.executeBatch();
                for (int res : batchResults) {
                    if (res >= 0) successfullyProcessedCount++;
                }
            }
            conn.commit();
        }

        StringBuilder finalMessage = new StringBuilder();
        finalMessage.append("Import processed ").append(successfullyProcessedCount).append(" unique serials from the file.\n");
        finalMessage.append("Note: Serials that already existed in the database were NOT overwritten.");
        if (skippedRowCount > 0) {
            finalMessage.append("\nSkipped ").append(skippedRowCount).append(" empty or invalid rows.");
        }
        return finalMessage.toString();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        if (cell.getCellType() == CellType.FORMULA) {
            Workbook workbook = cell.getSheet().getWorkbook();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            return formatter.formatCellValue(cell, evaluator);
        } else {
            return formatter.formatCellValue(cell);
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}