package assettracking.controller;

import assettracking.data.BoxIdDetail;
import assettracking.data.BoxIdSummary;
import assettracking.db.DatabaseConnection;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BoxIdViewerController {

    @FXML private TextField searchField;
    @FXML private TableView<BoxIdSummary> summaryTable;
    @FXML private TableColumn<BoxIdSummary, String> boxIdCol;
    @FXML private TableColumn<BoxIdSummary, Integer> itemCountCol;
    @FXML private Label detailHeaderLabel;
    @FXML private TableView<BoxIdDetail> detailTable;
    @FXML private TableColumn<BoxIdDetail, String> serialCol;
    @FXML private TableColumn<BoxIdDetail, String> statusCol;
    @FXML private TableColumn<BoxIdDetail, String> subStatusCol;

    private final ObservableList<BoxIdSummary> summaryList = FXCollections.observableArrayList();
    private final ObservableList<BoxIdDetail> detailList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTables();
        loadSummaryDataAsync(); // <-- MODIFIED: Call the async method

        summaryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
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

    // NEW: Asynchronous data loading method
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

        loadTask.setOnSucceeded(e -> {
            summaryList.setAll(loadTask.getValue());
        });

        loadTask.setOnFailed(e -> {
            loadTask.getException().printStackTrace(); // Log the error for debugging
        });

        new Thread(loadTask).start();
    }

    private void loadDetailData(String boxId) {
        detailHeaderLabel.setText("Contents of Box: " + boxId);
        detailList.clear();
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
                detailList.add(new BoxIdDetail(rs.getString("serial_number"), rs.getString("status"), rs.getString("sub_status")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}