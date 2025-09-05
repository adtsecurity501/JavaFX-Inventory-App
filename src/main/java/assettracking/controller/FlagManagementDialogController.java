package assettracking.controller;

import assettracking.dao.FlaggedDeviceDAO;
import assettracking.manager.StageManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.stream.Collectors;

public class FlagManagementDialogController {

    // A simple record to represent a row in our table
    public record FlaggedDevice(SimpleStringProperty serialNumber, SimpleStringProperty reason) {
        public String getSerialNumber() { return serialNumber.get(); }
        public String getReason() { return reason.get(); }
        public void setReason(String value) { reason.set(value); }
    }

    @FXML private TextField searchField;
    @FXML private TableView<FlaggedDevice> flagsTable;
    @FXML private TableColumn<FlaggedDevice, String> serialCol;
    @FXML private TableColumn<FlaggedDevice, String> reasonCol;
    @FXML private TextField serialField;
    @FXML private TextField reasonField;
    @FXML private Button saveButton;
    @FXML private Button removeButton;
    @FXML private Button closeButton;

    private final FlaggedDeviceDAO flaggedDeviceDAO = new FlaggedDeviceDAO();
    private final ObservableList<FlaggedDevice> flaggedDeviceList = FXCollections.observableArrayList();
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        serialCol.setCellValueFactory(cellData -> cellData.getValue().serialNumber());
        reasonCol.setCellValueFactory(cellData -> cellData.getValue().reason());

        // Make the reason column directly editable
        reasonCol.setCellFactory(TextFieldTableCell.forTableColumn());
        reasonCol.setOnEditCommit(event -> {
            FlaggedDevice device = event.getRowValue();
            String newReason = event.getNewValue();
            if (flaggedDeviceDAO.flagDevice(device.getSerialNumber(), newReason)) {
                device.setReason(newReason);
            } else {
                StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Update Failed", "Could not update the flag reason in the database.");
                flagsTable.refresh();
            }
        });

        FilteredList<FlaggedDevice> filteredData = new FilteredList<>(flaggedDeviceList, p -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(device -> {
                if (newVal == null || newVal.isEmpty()) return true;
                String lowerCaseFilter = newVal.toLowerCase();
                return device.getSerialNumber().toLowerCase().contains(lowerCaseFilter) ||
                        device.getReason().toLowerCase().contains(lowerCaseFilter);
            });
        });

        flagsTable.setItems(filteredData);

        flagsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selection) -> {
            removeButton.setDisable(selection == null);
        });

        loadFlags();
    }

    public void setOnSaveCallback(Runnable onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void loadFlags() {
        List<SimpleEntry<String, String>> flags = flaggedDeviceDAO.getAllFlags();
        flaggedDeviceList.setAll(flags.stream()
                .map(entry -> new FlaggedDevice(new SimpleStringProperty(entry.getKey()), new SimpleStringProperty(entry.getValue())))
                .collect(Collectors.toList()));
        removeButton.setDisable(true);
    }

    @FXML
    private void handleAddOrUpdate() {
        String serial = serialField.getText().trim();
        String reason = reasonField.getText().trim();
        if (serial.isEmpty() || reason.isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "Serial Number and Reason cannot be empty.");
            return;
        }
        if (flaggedDeviceDAO.flagDevice(serial, reason)) {
            loadFlags();
            serialField.clear();
            reasonField.clear();
            if (onSaveCallback != null) onSaveCallback.run();
        } else {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Save Failed", "Could not save the flag to the database.");
        }
    }

    @FXML
    private void handleRemove() {
        FlaggedDevice selected = flagsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (StageManager.showDeleteConfirmationDialog(getStage(), "flag for device", selected.getSerialNumber())) {
            if (flaggedDeviceDAO.unflagDevice(selected.getSerialNumber())) {
                loadFlags();
                if (onSaveCallback != null) onSaveCallback.run();
            } else {
                StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Delete Failed", "Could not remove the flag from the database.");
            }
        }
    }

    @FXML
    private void handleClose() {
        closeStage();
    }

    private Stage getStage() {
        return (Stage) closeButton.getScene().getWindow();
    }

    private void closeStage() {
        getStage().close();
    }
}