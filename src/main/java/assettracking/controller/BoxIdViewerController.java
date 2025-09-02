package assettracking.controller;

import assettracking.data.BoxIdDetail;
import assettracking.data.BoxIdSummary;
import assettracking.db.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
        loadSummaryData();

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
        // --- CORRECTED CODE BLOCK ---
        boxIdCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().boxId()));
        itemCountCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().itemCount()));

        serialCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().serialNumber()));
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status()));
        subStatusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().subStatus()));
        // --- END OF CORRECTION ---

        detailTable.setItems(detailList);
    }

    private void loadSummaryData() {
        summaryList.clear();
        String sql = """
            SELECT
                SUBSTR(change_log, INSTR(change_log, ':') + 2, INSTR(change_log, '.') - INSTR(change_log, ':') - 2) as box_id,
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
                summaryList.add(new BoxIdSummary(rs.getString("box_id"), rs.getInt("item_count")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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