package assettracking.controller;

import assettracking.dao.AssetDAO;
import assettracking.db.DatabaseConnection;
import assettracking.manager.StageManager;
import assettracking.ui.AutoCompletePopup;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class AutofillImportDialogController {

    private static final Map<String, String> DB_FIELDS = new LinkedHashMap<>();

    static {
        DB_FIELDS.put("serial_number", "Serial Number (Required)");
        DB_FIELDS.put("part_number", "Part Number (Mfg #)");
        DB_FIELDS.put("description", "Description");
    }

    private final Map<String, ComboBox<String>> mappingCombos = new HashMap<>();
    private final AssetDAO assetDAO = new AssetDAO();
    @FXML
    private TextField filePathField;
    @FXML
    private GridPane mappingGrid;
    @FXML
    private TextField makeField;
    @FXML
    private TextField categoryField;
    @FXML
    private TextField partNumberField;
    @FXML
    private TextField descriptionField;
    @FXML
    private Button importButton;
    @FXML
    private Label statusLabel;
    private File selectedFile;

    @FXML
    public void initialize() {
        new AutoCompletePopup(makeField, () -> assetDAO.findDistinctValuesLike("make", makeField.getText()));
        new AutoCompletePopup(categoryField, () -> assetDAO.findDistinctValuesLike("category", categoryField.getText()));
    }

    @FXML
    private void handleSelectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Autofill Data Excel File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"));
        File file = fileChooser.showOpenDialog(getStage());

        if (file != null) {
            selectedFile = file;
            filePathField.setText(file.getAbsolutePath());
            try {
                List<String> headers = readHeaders(file);
                populateMappingUI(headers);
                importButton.setDisable(false);
            } catch (IOException e) {
                StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "File Read Error", "Could not read header row: " + e.getMessage());
                importButton.setDisable(true);
            }
        }
    }

    private void populateMappingUI(List<String> fileHeaders) {
        mappingGrid.getChildren().clear();
        mappingCombos.clear();
        int rowIndex = 0;

        List<String> options = new ArrayList<>();
        options.add("Ignore");
        options.addAll(fileHeaders);

        for (Map.Entry<String, String> entry : DB_FIELDS.entrySet()) {
            String dbKey = entry.getKey();
            String uiLabel = entry.getValue();
            Label label = new Label(uiLabel);
            ComboBox<String> comboBox = new ComboBox<>(FXCollections.observableArrayList(options));
            comboBox.getSelectionModel().select("Ignore");

            for (String header : fileHeaders) {
                if (isMatch(dbKey, header)) {
                    comboBox.getSelectionModel().select(header);
                    break;
                }
            }

            mappingGrid.add(label, 0, rowIndex);
            mappingGrid.add(comboBox, 1, rowIndex);
            mappingCombos.put(dbKey, comboBox);
            rowIndex++;
        }
    }

    private boolean isMatch(String dbKey, String header) {
        String lowerHeader = header.toLowerCase();
        return switch (dbKey) {
            case "serial_number" -> lowerHeader.contains("serial");
            case "part_number" -> lowerHeader.contains("mfg") || lowerHeader.contains("part");
            case "description" -> lowerHeader.contains("desc");
            default -> false;
        };
    }

    @FXML
    private void handleImport() {
        if (selectedFile == null || "Ignore".equals(mappingCombos.get("serial_number").getValue())) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Mapping Incomplete", "Please select a file and map a column to the 'Serial Number' field.");
            return;
        }

        importButton.setDisable(true);
        statusLabel.setText("Processing file...");

        Task<String> importTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return processAndUpsertFile();
            }
        };
        importTask.setOnSucceeded(e -> {
            statusLabel.setText("Import complete!");
            StageManager.showAlert(getStage(), Alert.AlertType.INFORMATION, "Import Complete", importTask.getValue());
            importButton.setDisable(false);
        });
        importTask.setOnFailed(e -> {
            statusLabel.setText("Import failed!");
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Import Failed", "An error occurred: " + e.getSource().getException().getMessage());
            importButton.setDisable(false);
        });
        new Thread(importTask).start();
    }

    private String processAndUpsertFile() throws IOException, SQLException {
        Map<String, Integer> finalMapping = new HashMap<>();
        for (Map.Entry<String, ComboBox<String>> entry : mappingCombos.entrySet()) {
            String selectedHeader = entry.getValue().getValue();
            if (!"Ignore".equals(selectedHeader)) {
                finalMapping.put(entry.getKey(), getHeaderIndex(selectedFile, selectedHeader));
            }
        }

        List<Map<String, String>> dataRows = parseExcelData(finalMapping);

        String sql = "INSERT INTO device_autofill_data (serial_number, part_number, description, make, category) VALUES (?, ?, ?, ?, ?) ON CONFLICT (serial_number) DO UPDATE SET part_number = EXCLUDED.part_number, description = EXCLUDED.description, make = EXCLUDED.make, category = EXCLUDED.category";
        int[] results;
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (Map<String, String> rowData : dataRows) {
                stmt.setString(1, rowData.get("serial_number"));
                stmt.setString(2, rowData.getOrDefault("part_number", partNumberField.getText()));
                stmt.setString(3, rowData.getOrDefault("description", descriptionField.getText()));
                stmt.setString(4, rowData.getOrDefault("make", makeField.getText()));
                stmt.setString(5, rowData.getOrDefault("category", categoryField.getText()));
                stmt.addBatch();
            }
            results = stmt.executeBatch();
            conn.commit();
        }
        long successfulCount = Arrays.stream(results).filter(i -> i >= 0).count();
        return String.format("Successfully processed %d records into the autofill table.", successfulCount);
    }

    private List<Map<String, String>> parseExcelData(Map<String, Integer> finalMapping) throws IOException {
        List<Map<String, String>> dataRows = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(selectedFile); Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String serial = getCellValueAsString(row.getCell(finalMapping.get("serial_number")));
                if (serial.isEmpty()) continue;

                Map<String, String> rowData = new HashMap<>();
                rowData.put("serial_number", serial);
                rowData.put("part_number", getCellValueAsString(row.getCell(finalMapping.getOrDefault("part_number", -1))));
                rowData.put("description", getCellValueAsString(row.getCell(finalMapping.getOrDefault("description", -1))));
                dataRows.add(rowData);
            }
        }
        return dataRows;
    }

    private int getHeaderIndex(File file, String headerName) throws IOException {
        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {
            Row headerRow = workbook.getSheetAt(0).getRow(0);
            for (Cell cell : headerRow) {
                if (headerName.equalsIgnoreCase(getCellValueAsString(cell))) {
                    return cell.getColumnIndex();
                }
            }
        }
        return -1;
    }

    private List<String> readHeaders(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {
            List<String> headers = new ArrayList<>();
            Row headerRow = workbook.getSheetAt(0).getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    headers.add(getCellValueAsString(cell));
                }
            }
            return headers;
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return new DataFormatter().formatCellValue(cell).trim();
    }

    @FXML
    private void handleClose() {
        getStage().close();
    }

    private Stage getStage() {
        return (Stage) importButton.getScene().getWindow();
    }
}