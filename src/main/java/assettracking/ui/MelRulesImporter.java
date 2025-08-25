package assettracking.ui;

import assettracking.db.DatabaseConnection;
import assettracking.manager.StageManager;
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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class MelRulesImporter {

    // A simple record to hold data from each row of the Excel file
    private record MelRuleData(String model, String description, String action, String notes, String mfg, String threshold) {}

    // A record to hold the results of parsing, including skipped rows for better feedback
    private record ParseResult(List<MelRuleData> rules, int skippedRowCount) {}

    public void importFromFile(Stage owner) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select MEL Rules Excel File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"));
        File selectedFile = fileChooser.showOpenDialog(owner);

        if (selectedFile != null) {
            Task<String> importTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    // This method now handles the entire transaction from parsing to DB update
                    return processAndImportFile(selectedFile);
                }
            };

            importTask.setOnSucceeded(e -> StageManager.showAlert(owner, Alert.AlertType.INFORMATION, "Import Complete", importTask.getValue()));

            // The setOnFailed handler now provides much more specific feedback
            importTask.setOnFailed(e -> {
                Throwable ex = e.getSource().getException();
                String title = "Import Failed";
                String message;
                if (ex instanceof MelRulesImportException) {
                    // This is our custom, user-friendly error
                    message = ex.getMessage();
                } else {
                    // This is for unexpected errors (e.g., database connection lost mid-import)
                    message = "An unexpected error occurred: " + ex.getMessage();
                    ex.printStackTrace(); // Log the full trace for unexpected errors
                }
                StageManager.showAlert(owner, Alert.AlertType.ERROR, title, message);
            });

            new Thread(importTask).start();
        }
    }

    private String processAndImportFile(File file) throws SQLException, IOException, MelRulesImportException {
        ParseResult parseResult = parseExcelFile(file);

        String insertSql = "INSERT INTO Mel_Rules (model_number, description, action, special_notes, manufac, redeploy_threshold) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            conn.setAutoCommit(false); // Start transaction
            try (Statement stmt = conn.createStatement();
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                // 1. Clear the existing rules
                stmt.execute("DELETE FROM Mel_Rules");

                // 2. Insert the new rules
                for (MelRuleData rule : parseResult.rules()) {
                    insertStmt.setString(1, rule.model());
                    insertStmt.setString(2, rule.description());
                    insertStmt.setString(3, rule.action());
                    insertStmt.setString(4, rule.notes());
                    insertStmt.setString(5, rule.mfg());
                    insertStmt.setString(6, rule.threshold());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();

                // 3. If everything was successful, commit the changes
                conn.commit();

                String successMessage = String.format("Successfully imported %d MEL rules.", parseResult.rules().size());
                if (parseResult.skippedRowCount() > 0) {
                    successMessage += String.format(" (Skipped %d rows due to missing model numbers).", parseResult.skippedRowCount());
                }
                return successMessage;

            } catch (SQLException e) {
                conn.rollback(); // If any database error occurs, undo everything
                throw e;
            }
        }
    }

    private ParseResult parseExcelFile(File file) throws IOException, MelRulesImportException {
        List<MelRuleData> rules = new ArrayList<>();
        int skippedRowCount = 0;

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new MelRulesImportException("The selected file is empty or has no header row.");
            }
            Map<String, Integer> headers = getHeaderMap(headerRow);

            // --- Enhanced Header Validation ---
            List<String> missingHeaders = new ArrayList<>();
            if (!headers.containsKey("model")) missingHeaders.add("Model");
            if (!headers.containsKey("action")) missingHeaders.add("Action");

            if (!missingHeaders.isEmpty()) {
                String error = "The import failed due to missing columns.\n\n" +
                        "Required columns: `Model`, `Action`.\n" +
                        "Optional columns: `Description`, `SPECIAL NOTES`, `Mfg`, `Redeploy Threshold`.";
                throw new MelRulesImportException(error);
            }

            Function<String[], Integer> findColumn = (possibleNames) -> {
                for (String name : possibleNames) {
                    if (headers.containsKey(name.toLowerCase())) return headers.get(name.toLowerCase());
                }
                return -1;
            };

            int descCol = findColumn.apply(new String[]{"description"});
            int notesCol = findColumn.apply(new String[]{"special notes"});
            int mfgCol = findColumn.apply(new String[]{"mfg"});
            int thresholdCol = findColumn.apply(new String[]{"redeploy threshold", "redeployoowthreshold"});

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String model = getCellValueAsString(row.getCell(headers.get("model")));
                if (model.isEmpty()) {
                    skippedRowCount++;
                    continue;
                }

                rules.add(new MelRuleData(
                        model,
                        getCellValueAsString(row.getCell(descCol)),
                        getCellValueAsString(row.getCell(headers.get("action"))),
                        getCellValueAsString(row.getCell(notesCol)),
                        getCellValueAsString(row.getCell(mfgCol)),
                        getCellValueAsString(row.getCell(thresholdCol))
                ));
            }
        }
        if (rules.isEmpty()) {
            throw new MelRulesImportException("No valid data rows with a 'Model' were found in the file.");
        }
        return new ParseResult(rules, skippedRowCount);
    }

    private Map<String, Integer> getHeaderMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) return map;
        for (Cell cell : headerRow) {
            map.put(getCellValueAsString(cell).trim().toLowerCase(), cell.getColumnIndex());
        }
        return map;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null || cell.getColumnIndex() < 0) {
            return "";
        }
        return new DataFormatter().formatCellValue(cell).trim();
    }
}