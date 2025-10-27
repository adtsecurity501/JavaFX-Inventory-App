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
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class BoxIdViewerController {

    private static final DataFormat SERIAL_NUMBERS_FORMAT = new DataFormat("application/x-serial-numbers");
    private final ObservableList<BoxIdSummary> summaryList = FXCollections.observableArrayList();
    private final ObservableList<BoxIdDetail> detailList = FXCollections.observableArrayList();
    private final ZplPrinterService printerService = new ZplPrinterService();
    @FXML
    private TextField searchField;
    @FXML
    private TextField serialSearchField;
    @FXML
    private Button serialSearchButton;
    @FXML
    private CheckBox showArchivedCheck;
    @FXML
    private TableView<BoxIdSummary> summaryTable;
    @FXML
    private TableColumn<BoxIdSummary, String> boxIdCol;
    @FXML
    private TableColumn<BoxIdSummary, Integer> itemCountCol;
    @FXML
    private Label detailHeaderLabel;
    @FXML
    private TableView<BoxIdDetail> detailTable;
    @FXML
    private TableColumn<BoxIdDetail, String> serialCol;
    @FXML
    private TableColumn<BoxIdDetail, String> statusCol;
    @FXML
    private TableColumn<BoxIdDetail, String> subStatusCol;
    @FXML
    private Button printLabelButton;
    @FXML
    private Button exportCsvButton;
    @FXML
    private Button updateStatusButton;
    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        setupTables();
        setupDragAndDrop();


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
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filteredData.setPredicate(summary -> newVal == null || newVal.isEmpty() || summary.boxId().toLowerCase().contains(newVal.toLowerCase())));
        summaryTable.setItems(filteredData);

        showArchivedCheck.selectedProperty().addListener((obs, oldVal, newVal) -> loadSummaryDataAsync());
        loadSummaryDataAsync();
    }

    @FXML
    private void handleSearchBySerial() {
        String serial = serialSearchField.getText().trim();
        if (serial.isEmpty()) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "Input Required", "Please enter a serial number to search for.");
            return;
        }

        Task<Optional<String>> findBoxTask = new Task<>() {
            @Override
            protected Optional<String> call() throws Exception {
                // This query finds the box_id for the most recent entry of a given serial number.
                String sql = """
                            SELECT ds.box_id FROM Device_Status ds
                            JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id
                            WHERE re.serial_number = ?
                            ORDER BY ds.receipt_id DESC LIMIT 1
                        """;
                try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, serial);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("box_id"));
                    }
                }
                return Optional.empty();
            }
        };

        findBoxTask.setOnSucceeded(e -> {
            Optional<String> boxIdOpt = findBoxTask.getValue();
            if (boxIdOpt.isPresent() && !boxIdOpt.get().isEmpty()) {
                String boxId = boxIdOpt.get();
                // Find the box in the summary table and select it
                summaryTable.getItems().stream().filter(summary -> boxId.equalsIgnoreCase(summary.boxId())).findFirst().ifPresent(item -> {
                    summaryTable.getSelectionModel().select(item);
                    summaryTable.scrollTo(item);
                    statusLabel.setText("Found serial '" + serial + "' in Box ID: " + boxId);
                });
            } else {
                StageManager.showAlert(getOwnerWindow(), Alert.AlertType.INFORMATION, "Not Found", "Serial number '" + serial + "' was not found in any box.");
            }
        });

        findBoxTask.setOnFailed(e -> StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Database Error", "An error occurred while searching for the serial number."));

        new Thread(findBoxTask).start();
    }

    private void setupTables() {
        boxIdCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().boxId()));
        itemCountCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().itemCount()));
        serialCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().serialNumber()));
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status()));
        subStatusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().subStatus()));
        detailTable.setItems(detailList);
    }


    private void setupDragAndDrop() {
        // --- SOURCE: The items in the detail table ---
        detailTable.setRowFactory(tv -> {
            TableRow<BoxIdDetail> row = new TableRow<>();
            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent cc = new ClipboardContent();

                    List<String> selectedSerials = detailTable.getSelectionModel().getSelectedItems().stream().map(BoxIdDetail::serialNumber).collect(Collectors.toList());

                    cc.put(SERIAL_NUMBERS_FORMAT, String.join(",", selectedSerials));
                    db.setContent(cc);
                    event.consume();
                }
            });
            return row;
        });

        // --- TARGET: The boxes in the summary table ---
        summaryTable.setRowFactory(tv -> {
            TableRow<BoxIdSummary> row = new TableRow<>();

            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIAL_NUMBERS_FORMAT)) {
                    BoxIdSummary sourceBox = summaryTable.getSelectionModel().getSelectedItem();

                    // --- THIS IS THE FIX ---
                    // The check now uses .equals() for a case-sensitive comparison,
                    // allowing you to drag between boxes that only differ by case.
                    if (sourceBox != null && !row.isEmpty() && !sourceBox.boxId().equals(row.getItem().boxId())) {
                        event.acceptTransferModes(TransferMode.MOVE);
                        row.getStyleClass().add("drag-over");
                    }
                }
                event.consume();
            });

            row.setOnDragExited(event -> row.getStyleClass().remove("drag-over"));

            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasContent(SERIAL_NUMBERS_FORMAT)) {
                    BoxIdSummary targetBox = row.getItem();
                    String serialsString = (String) db.getContent(SERIAL_NUMBERS_FORMAT);
                    List<String> serialsToMove = Arrays.asList(serialsString.split(","));

                    Task<Integer> moveTask = createMoveItemsTask(serialsToMove, targetBox.boxId());
                    moveTask.setOnSucceeded(e -> {
                        statusLabel.setText(String.format("Moved %d item(s) to Box ID '%s'.", moveTask.getValue(), targetBox.boxId()));
                        refreshAllData();
                    });
                    moveTask.setOnFailed(e -> StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Move Failed", "A database error occurred."));
                    new Thread(moveTask).start();

                    success = true;
                }
                event.setDropCompleted(success);
                event.consume();
            });

            return row;
        });
    }

    @FXML
    private void handlePrintLabel() {
        BoxIdSummary selectedBox = summaryTable.getSelectionModel().getSelectedItem();
        if (selectedBox == null) return;

        List<String> printerNames = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null)).map(PrintService::getName).collect(Collectors.toList());

        if (printerNames.isEmpty()) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "No Printers Found", "There are no printers installed on this system.");
            return;
        }

        String defaultPrinter = printerNames.stream().filter(n -> n.toLowerCase().contains("gx")).findFirst().orElse(printerNames.getFirst());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(defaultPrinter, printerNames);
        dialog.setTitle("Select Printer");
        dialog.setHeaderText("Choose a label printer for the box label.");
        dialog.setContentText("Printer:");

        Optional<String> result = dialog.showAndWait();

        result.ifPresent(selectedPrinter -> {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            String zpl = String.format("^XA^PW711^LL305" + "^FO20,20^GB670,265,3^FS" + "^FT35,70^A0N,40,40^FH\\^FDBox ID: %s^FS" + "^FT35,120^A0N,30,30^FH\\^FDItems: %d^FS" + "^FT35,160^A0N,30,30^FH\\^FDDate: %s^FS" + "^BY2,3,80^FT35,260^BCN,,N,N^FD>:%s^FS" + "^XZ", selectedBox.boxId(), selectedBox.itemCount(), today, selectedBox.boxId());

            if (printerService.sendZplToPrinter(selectedPrinter, zpl)) {
                statusLabel.getStyleClass().setAll("status-label-success"); // Clears old styles and adds the new one
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
                statusLabel.getStyleClass().setAll("status-label-success");
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
                statusLabel.getStyleClass().setAll("status-label-success"); // More robust
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

        boolean confirmed = StageManager.showConfirmationDialog(getOwnerWindow(), "Confirm Removal", "Are you sure you want to remove " + selectedItems.size() + " item(s) from this box?", "Their status will be reverted to 'Disposed / Ready for Wipe'. This action cannot be undone.");

        if (confirmed) {
            List<String> serialsToRemove = selectedItems.stream().map(BoxIdDetail::serialNumber).collect(Collectors.toList());
            Task<Void> removeTask = createRemoveItemsTask(serialsToRemove);
            removeTask.setOnSucceeded(e -> {
                statusLabel.getStyleClass().setAll("status-label-success"); // More robust
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
            Platform.runLater(() -> summaryTable.getItems().stream().filter(item -> item.boxId().equals(selected.boxId())).findFirst().ifPresent(item -> summaryTable.getSelectionModel().select(item)));
        } else {
            detailList.clear();
        }
    }

    private void loadSummaryDataAsync() {
        Task<List<BoxIdSummary>> loadTask = new Task<>() {
            @Override
            protected List<BoxIdSummary> call() throws Exception {
                List<BoxIdSummary> results = new ArrayList<>();

                // --- THIS IS THE CORRECTED QUERY ---
                // It now only counts the LATEST status for each unique device.
                String sql = """
                        SELECT
                            ds.box_id,
                            COUNT(DISTINCT re.serial_number) as item_count,
                            SUM(CASE WHEN ds.sub_status LIKE '%%Picked Up' THEN 0 ELSE 1 END) as non_archived_count
                        FROM Device_Status ds
                        JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id
                        -- This subquery join ensures we are only looking at the most recent status record for each serial number
                        JOIN (
                            SELECT serial_number, MAX(receipt_id) as max_receipt_id
                            FROM Receipt_Events
                            GROUP BY serial_number
                        ) latest ON re.serial_number = latest.serial_number AND re.receipt_id = latest.max_receipt_id
                        WHERE ds.box_id IS NOT NULL AND ds.box_id != ''
                        GROUP BY ds.box_id
                    """;

                if (!showArchivedCheck.isSelected()) {
                    sql += " HAVING non_archived_count > 0";
                }

                sql += " ORDER BY ds.box_id";
                // --- END OF CORRECTION ---

                try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new BoxIdSummary(rs.getString("box_id"), rs.getInt("item_count")));
                    }
                }
                return results;
            }
        };

        loadTask.setOnSucceeded(e -> summaryList.setAll(loadTask.getValue()));
        loadTask.setOnFailed(e -> {
            Throwable ex = e.getSource().getException();
            if (ex != null) {
                Platform.runLater(() -> StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Database Error", "Failed to load box summary data: " + ex.getMessage()));
            }
        });
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
                            WHERE ds.box_id = ?
                        """;
                try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, boxId);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        results.add(new BoxIdDetail(rs.getString("serial_number"), rs.getString("status"), rs.getString("sub_status")));
                    }
                }
                return results;
            }
        };
        loadDetailsTask.setOnSucceeded(e -> detailList.setAll(loadDetailsTask.getValue()));
        loadDetailsTask.setOnFailed(e -> {
            Throwable ex = e.getSource().getException();
            if (ex != null) {
                Platform.runLater(() -> StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Database Error", "Failed to load box contents: " + ex.getMessage()));
            }
        });
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
                try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, newStatus);
                    stmt.setString(2, newSubStatus);
                    stmt.setString(3, "Box ID: " + boxId + "%");
                    return stmt.executeUpdate();
                }
            }
        };
    }

    @FXML
    private void handleMoveItems() {
        List<BoxIdDetail> selectedItems = detailTable.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            StageManager.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, "No Selection", "Please select one or more items to move.");
            return;
        }

        BoxIdSummary currentBox = summaryTable.getSelectionModel().getSelectedItem();
        if (currentBox == null) return; // Should not happen, but a good safeguard

        // Ask the user for the destination box ID
        Optional<String> result = StageManager.showTextInputDialog(getOwnerWindow(), "Move Items", "Moving " + selectedItems.size() + " item(s) from Box ID: " + currentBox.boxId(), "Enter the destination Box ID:", "");

        result.ifPresent(newBoxIdRaw -> {
            String newBoxId = newBoxIdRaw.trim();
            if (newBoxId.isEmpty() || newBoxId.equalsIgnoreCase(currentBox.boxId())) {
                return; // Do nothing if the new box is empty or the same as the old one
            }

            List<String> serialsToMove = selectedItems.stream().map(BoxIdDetail::serialNumber).collect(Collectors.toList());
            Task<Integer> moveTask = createMoveItemsTask(serialsToMove, newBoxId);

            moveTask.setOnSucceeded(e -> {
                int updatedCount = moveTask.getValue();
                statusLabel.getStyleClass().setAll("status-label-success");
                statusLabel.setText(String.format("Moved %d item(s) to Box ID '%s'.", updatedCount, newBoxId));
                refreshAllData(); // Refresh the entire view to show updated counts
            });
            moveTask.setOnFailed(e -> StageManager.showAlert(getOwnerWindow(), Alert.AlertType.ERROR, "Move Failed", "A database error occurred while moving the items."));
            new Thread(moveTask).start();
        });
    }

    // Add this new helper method to create the background task
    private Task<Integer> createMoveItemsTask(List<String> serials, String newBoxIdRaw) {
        return new Task<>() {
            @Override
            protected Integer call() throws Exception {
                // --- THIS IS THE FIX ---
                // Standardize the Box ID to uppercase before sending it to the database.
                String newBoxId = newBoxIdRaw.trim().toUpperCase();

                String placeholders = String.join(",", Collections.nCopies(serials.size(), "?"));

                String sql = String.format("""
                            UPDATE Device_Status SET box_id = ?
                            WHERE receipt_id IN (
                                SELECT MAX(re.receipt_id)
                                FROM Receipt_Events re
                                WHERE re.serial_number IN (%s)
                                GROUP BY re.serial_number
                            )
                        """, placeholders);

                try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, newBoxId);
                    int i = 2;
                    for (String serial : serials) {
                        stmt.setString(i++, serial);
                    }
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

                String sql = """
                            UPDATE Device_Status ds SET status = 'Disposed', sub_status = 'Ready for Wipe', box_id = NULL, change_log = 'Removed from box'
                            WHERE ds.receipt_id IN ( ... )
                        """.replace("( ... )", String.format("(SELECT MAX(re.receipt_id) FROM Receipt_Events re WHERE re.serial_number IN (%s) GROUP BY re.serial_number)", placeholders));
                // --- END OF FIX ---
                try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (int i = 0; i < serials.size(); i++) {
                        stmt.setString(i + 1, serials.get(i));
                    }
                    stmt.executeUpdate();
                }
                return null;
            }
        };
    }

    private Window getOwnerWindow() {
        return summaryTable.getScene().getWindow();
    }
}