package assettracking.controller;

import assettracking.dao.FlaggedDeviceDAO;
import assettracking.data.FlaggedDeviceData;
import assettracking.manager.StageManager;
import assettracking.ui.FlaggedDeviceImporter;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;

public class FlagManagementDialogController {

    private static final String NO_REMOVE_TAG = "[NOREMOVE]";
    private final FlaggedDeviceDAO flaggedDeviceDAO = new FlaggedDeviceDAO();
    private final ObservableList<FlaggedDevice> flaggedDeviceList = FXCollections.observableArrayList();

    @FXML
    private TableView<FlaggedDevice> flagsTable;
    @FXML
    private TableColumn<FlaggedDevice, String> serialCol;
    @FXML
    private TableColumn<FlaggedDevice, String> reasonCol;
    @FXML
    private TableColumn<FlaggedDevice, Boolean> preventRemovalCol;
    @FXML
    private TextField searchField;
    @FXML
    private TextField serialField;
    @FXML
    private TextField reasonField;
    @FXML
    private CheckBox preventRemovalCheck;
    @FXML
    private Button saveButton;
    @FXML
    private Button removeButton;

    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        serialCol.setCellValueFactory(cellData -> cellData.getValue().serialNumber());
        reasonCol.setCellValueFactory(cellData -> cellData.getValue().reason());
        preventRemovalCol.setCellValueFactory(cellData -> cellData.getValue().preventRemoval());

        reasonCol.setCellFactory(TextFieldTableCell.forTableColumn());
        reasonCol.setOnEditCommit(event -> {
            FlaggedDevice device = event.getRowValue();
            device.setReason(event.getNewValue());
            updateFlagInDatabase(device);
        });

        // --- THIS IS THE CRITICAL FIX ---
        // Instead of using the default CheckBoxTableCell, we create a custom one
        // to gain direct control over the click event.
        preventRemovalCol.setCellFactory(col -> new TableCell<FlaggedDevice, Boolean>() {
            private final CheckBox checkBox = new CheckBox();

            {
                // The listener is now on the checkbox's property itself.
                // This fires INSTANTLY when the box is clicked.
                checkBox.setOnAction(event -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        FlaggedDevice device = getTableRow().getItem();
                        device.setPreventRemoval(checkBox.isSelected());
                        updateFlagInDatabase(device); // Save immediately
                    }
                });
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item);
                    setGraphic(checkBox);
                }
            }
        });
        // --- END OF FIX ---

        flagsTable.setEditable(true);

        FilteredList<FlaggedDevice> filteredData = new FilteredList<>(flaggedDeviceList, p -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filteredData.setPredicate(device -> {
            if (newVal == null || newVal.isEmpty()) return true;
            String lowerCaseFilter = newVal.toLowerCase();
            return device.getSerialNumber().toLowerCase().contains(lowerCaseFilter) || device.getReason().toLowerCase().contains(lowerCaseFilter);
        }));

        flagsTable.setItems(filteredData);
        flagsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selection) -> removeButton.setDisable(selection == null));

        loadFlags();
    }

    // All other methods in this controller remain the same as the previous response.
    // (handleAddOrUpdate, handleRemove, updateFlagInDatabase, etc., are all correct)

    public void setOnSaveCallback(Runnable onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void loadFlags() {
        List<FlaggedDeviceData> flags = flaggedDeviceDAO.getAllFlags();
        flaggedDeviceList.setAll(flags.stream().map(FlaggedDevice::new).collect(Collectors.toList()));
        removeButton.setDisable(true);
    }

    private void updateFlagInDatabase(FlaggedDevice device) {
        String finalReason = device.getReason().trim();
        if (device.getPreventRemoval()) {
            finalReason = NO_REMOVE_TAG + " " + finalReason;
        }
        if (!flaggedDeviceDAO.flagDevice(device.getSerialNumber(), finalReason)) {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Update Failed", "Could not update the flag in the database.");
            loadFlags();
        }
    }

    @FXML
    private void handleAddOrUpdate() {
        String serial = serialField.getText().trim();
        String reason = reasonField.getText().trim();
        boolean preventRemoval = preventRemovalCheck.isSelected();
        if (serial.isEmpty() || reason.isEmpty()) {
            StageManager.showAlert(getStage(), Alert.AlertType.WARNING, "Input Required", "Serial Number and Reason cannot be empty.");
            return;
        }
        String finalReason = reason;
        if (preventRemoval) {
            finalReason = NO_REMOVE_TAG + " " + reason;
        }
        if (flaggedDeviceDAO.flagDevice(serial, finalReason)) {
            loadFlags();
            serialField.clear();
            reasonField.clear();
            preventRemovalCheck.setSelected(false);
            if (onSaveCallback != null) onSaveCallback.run();
        } else {
            StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Save Failed", "Could not save the flag to the database.");
        }
    }

    @FXML
    private void handleImportFromFile() {
        new FlaggedDeviceImporter().importFromFile(getStage(), this::loadFlags);
        if (onSaveCallback != null) onSaveCallback.run();
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
        ((Stage) removeButton.getScene().getWindow()).close();
    }

    private Stage getStage() {
        return (Stage) flagsTable.getScene().getWindow();
    }

    public static class FlaggedDevice {
        private final SimpleStringProperty serialNumber;
        private final SimpleStringProperty reason;
        private final SimpleBooleanProperty preventRemoval;

        public FlaggedDevice(FlaggedDeviceData data) {
            this.serialNumber = new SimpleStringProperty(data.serialNumber());
            this.preventRemoval = new SimpleBooleanProperty(data.preventRemoval());
            String displayReason = data.reason();
            if (data.preventRemoval() && displayReason != null) {
                displayReason = displayReason.substring(NO_REMOVE_TAG.length()).trim();
            }
            this.reason = new SimpleStringProperty(displayReason);
        }

        public String getSerialNumber() {
            return serialNumber.get();
        }

        public SimpleStringProperty serialNumber() {
            return serialNumber;
        }

        public String getReason() {
            return reason.get();
        }

        public void setReason(String value) {
            this.reason.set(value);
        }

        public SimpleStringProperty reason() {
            return reason;
        }

        public boolean getPreventRemoval() {
            return preventRemoval.get();
        }

        public void setPreventRemoval(boolean value) {
            this.preventRemoval.set(value);
        }

        public SimpleBooleanProperty preventRemoval() {
            return preventRemoval;
        }
    }
}