package assettracking.controller;

import assettracking.data.BoxIdDetail;
import assettracking.data.BoxIdSummary;
import assettracking.db.DatabaseConnection;
import assettracking.label.service.ZplPrinterService;
import assettracking.manager.StageManager;
import assettracking.manager.StatusManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Pair;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BoxIdViewerController {

    // --- FXML Fields ---
    @FXML private TextField searchField;
    @FXML private TableView<BoxIdSummary> summaryTable;
    @FXML private TableColumn<BoxIdSummary, String> boxIdCol;
    @FXML private TableColumn<BoxIdSummary, Integer> itemCountCol;
    @FXML private Label detailHeaderLabel;
    @FXML private TableView<BoxIdDetail> detailTable;
    @FXML private TableColumn<BoxIdDetail, String> serialCol;
    @FXML private TableColumn<BoxIdDetail, String> statusCol;
    @FXML private TableColumn<BoxIdDetail, String> subStatusCol;
    @FXML private Button printLabelButton;
    @FXML private Button exportCsvButton;
    @FXML private Button updateStatusButton;
    @FXML private Label statusLabel;

    // --- Data Lists ---
    private final ObservableList<BoxIdSummary> summaryList = FXCollections.observableArrayList();
    private final ObservableList<BoxIdDetail> detailList = FXCollections.observableArrayList();
    private final ZplPrinterService printerService = new ZplPrinterService();

    @FXML
    public void initialize() {
        setupTables();
        loadSummaryDataAsync();

        printLabelButton.setDisable(true);
        exportCsvButton.setDisable(true);
        updateStatusButton.setDisable(true);
        detailTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        summaryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean isBoxSelected = newSelection != null;
            printLabelButton.setDisable(!isBoxSelected);
            exportCsvButton.setDisable(!isBoxSelected);
            updateStatusButton.setDisable(!isBoxSelected);

            if (isBoxSelected) {
                loadDetailData(newSelection.boxId());
            } else {
                detailHeaderLabel.setText("Contents of Box");
                detailList.clear();
            }
        });

        FilteredList<BoxIdSummary> filteredData = new FilteredList<>(summaryList, p -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(summary -> {
                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }
                return summary.boxId().toLowerCase().contains(newVal.toLowerCase());
            });
        });
        summaryTable.setItems(filteredData);
    }

    private void setupTables() {
        boxIdCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().boxId()));
        itemCountCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().itemCount()));
        serialCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().serialNumber()));
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status()));
        subStatusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().subStatus()));
        detailTable.setItems(detailList);
    }

    @FXML
    private void handlePrintLabel() {
        BoxIdSummary selectedBox = summaryTable.getSelectionModel().getSelectedItem();
        if (selectedBox == null) return;

        List<String> printerNames = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .collect(Collectors.toList());

        if (printerNames.isEmpty()) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "No Printers Found", "There are no printers installed on this system.");
            return;
        }

        String defaultPrinter = printerNames.stream()
                .filter(n -> n.toLowerCase().contains("gx"))
                .findFirst()
                .orElse(printerNames.get(0));

        ChoiceDialog<String> dialog = new ChoiceDialog<>(defaultPrinter, printerNames);
        dialog.setTitle("Select Printer");
        dialog.setHeaderText("Choose a label printer for the box label.");
        dialog.setContentText("Printer:");

        Optional<String> result = dialog.showAndWait();

        result.ifPresent(selectedPrinter -> {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            String zpl = String.format(
                    "^XA^PW711^LL305" +
                            "^FO20,20^GB670,265,3^FS" +
                            "^FT35,70^A0N,40,40^FH\\^FDBox ID: %s^FS" +
                            "^FT35,120^A0N,30,30^FH\\^FDItems: %d^FS" +
                            "^FT35,160^A0N,30,30^FH\\^FDDate: %s^FS" +
                            "^BY2,3,80^FT35,260^BCN,,N,N^FD>:%s^FS" +
                            "^XZ",
                    selectedBox.boxId(), selectedBox.itemCount(), today, selectedBox.boxId()
            );

            if (printerService.sendZplToPrinter(selectedPrinter, zpl)) {
                statusLabel.getStyleClass().remove("status-label-error");
                statusLabel.getStyleClass().add("status-label-success");
                statusLabel.setText("Printed label for Box ID: " + selectedBox.boxId() + " to " + selectedPrinter);
            } else {
                StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Print Failed", "Failed to send label to printer: " + selectedPrinter);
            }
        });
    }

    @FXML
    private void handleExportCsv() {
        BoxIdSummary selectedBox = summaryTable.getSelectionModel().getSelectedItem();
        if (selectedBox == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Box Contents");
        fileChooser.setInitialFileName("Box_" + selectedBox.boxId() + "_Contents.csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(getOwnerWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("SerialNumber,Status,SubStatus");
                for (BoxIdDetail detail : detailList) {
                    writer.printf("\"%s\",\"%s\",\"%s\"\n", detail.serialNumber(), detail.status(), detail.subStatus());
                }
                statusLabel.getStyleClass().remove("status-label-error");
                statusLabel.getStyleClass().add("status-label-success");
                statusLabel.setText("Exported contents of " + selectedBox.boxId());
            } catch (IOException e) {
                StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Export Failed", "Could not write to file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleUpdateStatus() {
        BoxIdSummary selectedBox = summaryTable.getSelectionModel().getSelectedItem();
        if (selectedBox == null) return;

        showUpdateDialog().ifPresent(newStatus -> {
            String status = newStatus.getKey();
            String subStatus = newStatus.getValue();

            Task<Integer> updateTask = createBulkUpdateTask(selectedBox.boxId(), status, subStatus);
            updateTask.setOnSucceeded(e -> {
                int updatedCount = updateTask.getValue();
                statusLabel.getStyleClass().remove("status-label-error");
                statusLabel.getStyleClass().add("status-label-success");
                statusLabel.setText(String.format("Updated %d items in Box ID %s to %s / %s.", updatedCount, selectedBox.boxId(), status, subStatus));
                refreshAllData();
            });
            updateTask.setOnFailed(e -> StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Update Failed", "Database error during bulk update."));
            new Thread(updateTask).start();
        });
    }

    @FXML
    private void handleRemoveItems() {
        List<BoxIdDetail> selectedItems = detailTable.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "No Selection", "Please select one or more items from the contents table to remove.");
            return;
        }

        boolean confirmed = StageManager.showConfirmationDialog(getOwnerWindow(),
                "Confirm Removal",
                "Are you sure you want to remove " + selectedItems.size() + " item(s) from this box?",
                "Their status will be reverted to 'Disposed / Ready for Wipe'. This action cannot be undone."
        );

        if (confirmed) {
            List<String> serialsToRemove = selectedItems.stream().map(BoxIdDetail::serialNumber).collect(Collectors.toList());
            Task<Void> removeTask = createRemoveItemsTask(serialsToRemove);
            removeTask.setOnSucceeded(e -> {
                statusLabel.getStyleClass().remove("status-label-error");
                statusLabel.getStyleClass().add("status-label-success");
                statusLabel.setText("Removed " + serialsToRemove.size() + " item(s) from the box.");
                refreshAllData();
            });
            removeTask.setOnFailed(e -> StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Removal Failed", "A database error occurred."));
            new Thread(removeTask).start();
        }
    }

    private void refreshAllData() {
        BoxIdSummary selected = summaryTable.getSelectionModel().getSelectedItem();
        loadSummaryDataAsync();
        if (selected != null) {
            Platform.runLater(() -> {
                summaryTable.getItems().stream()
                        .filter(item -> item.boxId().equals(selected.boxId()))
                        .findFirst()
                        .ifPresent(item -> summaryTable.getSelectionModel().select(item));
            });
        } else {
            detailList.clear();
        }
    }

    private void loadSummaryDataAsync() {
        Task<List<BoxIdSummary>> loadTask = new Task<>() {
            @Override
            protected List<BoxIdSummary> call() throws Exception {
                List<BoxIdSummary> results = new ArrayList<>();
                String sql = """
                            SELECT
                                SUBSTRING(change_log, INSTR(change_log, ':') + 2, INSTR(change_log, '.') - INSTR(change_log, ':') - 2) as box_id,
                                COUNT(*) as item_count
                            FROM Device_Status
                            WHERE change_log LIKE 'Box ID:%'
                            GROUP BY box_id
                            ORDER BY box_id
                        """;
                try (Connection conn = DatabaseConnection.getInventoryConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new BoxIdSummary(rs.getString("box_id"), rs.getInt("item_count")));
                    }
                }
                return results;
            }
        };
        loadTask.setOnSucceeded(e -> summaryList.setAll(loadTask.getValue()));
        loadTask.setOnFailed(e -> e.getSource().getException().printStackTrace());
        new Thread(loadTask).start();
    }

    private void loadDetailData(String boxId) {
        detailHeaderLabel.setText("Contents of Box: " + boxId);
        Task<List<BoxIdDetail>> loadDetailsTask = new Task<>() {
            @Override
            protected List<BoxIdDetail> call() throws Exception {
                List<BoxIdDetail> results = new ArrayList<>();
                String sql = """
                    SELECT
                        re.serial_number,
                        ds.status,
                        ds.sub_status
                    FROM Device_Status ds
                    JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id
                    WHERE ds.change_log LIKE ?
                """;
                try (Connection conn = DatabaseConnection.getInventoryConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, "Box ID: " + boxId + "%");
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        results.add(new BoxIdDetail(rs.getString("serial_number"), rs.getString("status"), rs.getString("sub_status")));
                    }
                }
                return results;
            }
        };
        loadDetailsTask.setOnSucceeded(e -> detailList.setAll(loadDetailsTask.getValue()));
        loadDetailsTask.setOnFailed(e -> e.getSource().getException().printStackTrace());
        new Thread(loadDetailsTask).start();
    }

    private Optional<Pair<String, String>> showUpdateDialog() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Bulk Status Update");
        dialog.setHeaderText("Select the new status for all items in this box.");
        ButtonType updateButtonType = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(updateButtonType, ButtonType.CANCEL);
        ComboBox<String> statusCombo = new ComboBox<>(FXCollections.observableArrayList(StatusManager.getStatuses()));
        ComboBox<String> subStatusCombo = new ComboBox<>();
        statusCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            subStatusCombo.setItems(FXCollections.observableArrayList(StatusManager.getSubStatuses(n)));
            subStatusCombo.getSelectionModel().selectFirst();
        });
        statusCombo.getSelectionModel().select("Disposed");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        grid.add(new Label("New Status:"), 0, 0);
        grid.add(statusCombo, 1, 0);
        grid.add(new Label("New Sub-Status:"), 0, 1);
        grid.add(subStatusCombo, 1, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == updateButtonType) {
                return new Pair<>(statusCombo.getValue(), subStatusCombo.getValue());
            }
            return null;
        });
        return dialog.showAndWait();
    }

    private Task<Integer> createBulkUpdateTask(String boxId, String newStatus, String newSubStatus) {
        return new Task<>() {
            @Override
            protected Integer call() throws Exception {
                String sql = """
                    UPDATE Device_Status ds
                    SET status = ?, sub_status = ?, last_update = CURRENT_TIMESTAMP
                    WHERE ds.receipt_id IN (
                        SELECT re.receipt_id FROM Receipt_Events re
                        JOIN (
                            SELECT serial_number, MAX(receipt_id) as max_receipt_id
                            FROM Receipt_Events GROUP BY serial_number
                        ) latest ON re.serial_number = latest.serial_number AND re.receipt_id = latest.max_receipt_id
                        JOIN Device_Status inner_ds ON re.receipt_id = inner_ds.receipt_id
                        WHERE inner_ds.change_log LIKE ?
                    )
                """;
                try (Connection conn = DatabaseConnection.getInventoryConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, newStatus);
                    stmt.setString(2, newSubStatus);
                    stmt.setString(3, "Box ID: " + boxId + "%");
                    return stmt.executeUpdate();
                }
            }
        };
    }

    private Task<Void> createRemoveItemsTask(List<String> serials) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                String placeholders = String.join(",", Collections.nCopies(serials.size(), "?"));
                String sql = String.format("""
                    UPDATE Device_Status ds SET status = 'Disposed', sub_status = 'Ready for Wipe', change_log = 'Removed from box'
                    WHERE ds.receipt_id IN (
                        SELECT re.receipt_id FROM Receipt_Events re
                        JOIN (
                           SELECT serial_number, MAX(receipt_id) as max_receipt_id
                           FROM Receipt_Events GROUP BY serial_number
                        ) latest ON re.serial_number = latest.serial_number AND re.receipt_id = latest.max_receipt_id
                        WHERE re.serial_number IN (%s)
                    )
                """, placeholders);
                try (Connection conn = DatabaseConnection.getInventoryConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (int i = 0; i < serials.size(); i++) {
                        stmt.setString(i + 1, serials.get(i));
                    }
                    stmt.executeUpdate();
                }
                return null;
            }
        };
    }

    private Optional<String> findPrinter(String hint) {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .filter(n -> n.toLowerCase().contains(hint.toLowerCase()))
                .findFirst();
    }

    private Window getOwnerWindow() {
        return summaryTable.getScene().getWindow();
    }
}