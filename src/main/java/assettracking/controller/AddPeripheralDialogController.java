package assettracking.controller;

import assettracking.data.CustomPeripheral;
import assettracking.db.DatabaseConnection;
import assettracking.data.Package;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AddPeripheralDialogController {

    @FXML private GridPane checklistGrid;
    @FXML private TableView<CustomPeripheral> customTable;
    @FXML private TableColumn<CustomPeripheral, String> customCategoryCol;
    @FXML private TableColumn<CustomPeripheral, Integer> customQtyCol;
    @FXML private TableColumn<CustomPeripheral, String> customConditionCol;
    @FXML private Button saveButton;
    @FXML private Button closeButton;
    @FXML private Label feedbackLabel;

    private Package currentPackage;
    private PackageDetailController parentController;
    private final ObservableList<CustomPeripheral> customPeripherals = FXCollections.observableArrayList();
    private final List<ChecklistItem> checklistItems = new ArrayList<>();

    // Helper inner class to hold the controls for each checklist item
    private static class ChecklistItem {
        CheckBox checkBox;
        Spinner<Integer> qtySpinner;
        ComboBox<String> conditionBox;
        TextField detailField; // Optional, for items like power adapters
    }

    public void initData(Package pkg, PackageDetailController parent) {
        this.currentPackage = pkg;
        this.parentController = parent;
    }

    @FXML
    public void initialize() {
        setupChecklist();
        setupCustomTable();
    }

    private void setupChecklist() {
        String[] items = {
                "AC Cable", "Display Port Cable", "HDMI Cable", "VGA Cable", "Printer Cable",
                "USB-C Cable", "Lightning Cable", "Ethernet Cable", "Keyboard", "Mouse", "Stylus",
                "Webcam", "USB Headset", "Power Strip", "USB Wall Charger", "USB Car Charger",
                "Docking Station", "Laptop Bag", "iPad Case", "A4 Case"
        };

        int row = 0;
        for (String itemName : items) {
            ChecklistItem item = createChecklistItem(itemName);
            checklistItems.add(item);
            checklistGrid.addRow(row++, item.checkBox, new Label("Qty:"), item.qtySpinner, new Label("Condition:"), item.conditionBox);
        }

        // Special case for Power Adapter with a details field
        ChecklistItem powerAdapter = createChecklistItem("Power Adapter");
        powerAdapter.detailField = new TextField();
        powerAdapter.detailField.setPromptText("Type/Wattage...");
        powerAdapter.detailField.setManaged(false);
        powerAdapter.detailField.setVisible(false);
        // Link the checkbox to show/hide the detail field
        powerAdapter.checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            powerAdapter.detailField.setManaged(newVal);
            powerAdapter.detailField.setVisible(newVal);
        });
        checklistItems.add(powerAdapter);
        HBox powerAdapterBox = new HBox(5, new Label("Type/Wattage:"), powerAdapter.detailField);
        powerAdapterBox.setAlignment(Pos.CENTER_LEFT);
        checklistGrid.addRow(row, powerAdapter.checkBox, new Label("Qty:"), powerAdapter.qtySpinner, new Label("Condition:"), powerAdapter.conditionBox, powerAdapterBox);
    }

    private ChecklistItem createChecklistItem(String name) {
        ChecklistItem item = new ChecklistItem();
        item.checkBox = new CheckBox(name);
        item.qtySpinner = new Spinner<>(1, 100, 1);
        item.conditionBox = new ComboBox<>(FXCollections.observableArrayList("Used", "New", "Damaged"));
        item.conditionBox.setValue("Used");

        // Hide qty and condition until the item is checked
        item.qtySpinner.setManaged(false);
        item.qtySpinner.setVisible(false);
        item.conditionBox.setManaged(false);
        item.conditionBox.setVisible(false);

        // Link the checkbox to show/hide the other controls
        item.checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            item.qtySpinner.setManaged(newVal);
            item.qtySpinner.setVisible(newVal);
            item.conditionBox.setManaged(newVal);
            item.conditionBox.setVisible(newVal);
        });
        return item;
    }

    private void setupCustomTable() {
        customCategoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        customCategoryCol.setCellFactory(TextFieldTableCell.forTableColumn());
        customCategoryCol.setOnEditCommit(event -> event.getRowValue().setCategory(event.getNewValue()));

        customQtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        customQtyCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        customQtyCol.setOnEditCommit(event -> event.getRowValue().setQuantity(event.getNewValue()));

        customConditionCol.setCellValueFactory(new PropertyValueFactory<>("condition"));
        customConditionCol.setCellFactory(ComboBoxTableCell.forTableColumn("Used", "New", "Damaged"));
        customConditionCol.setOnEditCommit(event -> event.getRowValue().setCondition(event.getNewValue()));

        customTable.setItems(customPeripherals);
        customTable.setEditable(true);

        // FIX: Add resize policy for intelligent sizing
        customTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);    }

    @FXML
    private void handleAddRow() {
        customPeripherals.add(new CustomPeripheral("New Item", 1, "Used"));
    }

    @FXML
    private void handleRemoveRow() {
        CustomPeripheral selected = customTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            customPeripherals.remove(selected);
        }
    }

    @FXML
    private void handleSave() {
        saveButton.setDisable(true);
        closeButton.setDisable(true);
        feedbackLabel.setText("Saving peripherals...");

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (Connection conn = DatabaseConnection.getInventoryConnection()) {
                    conn.setAutoCommit(false);

                    // Process checklist items
                    for (ChecklistItem item : checklistItems) {
                        if (item.checkBox.isSelected()) {
                            String description = (item.detailField != null) ? item.detailField.getText() : "";
                            savePeripheral(conn, item.checkBox.getText(), item.conditionBox.getValue(), description, item.qtySpinner.getValue());
                        }
                    }

                    // Process custom table items
                    for (CustomPeripheral custom : customPeripherals) {
                        if (!custom.getCategory().isEmpty() && custom.getQuantity() > 0) {
                            savePeripheral(conn, custom.getCategory(), custom.getCondition(), "", custom.getQuantity());
                        }
                    }
                    conn.commit();
                }
                return null;
            }
        };

        saveTask.setOnSucceeded(e -> {
            feedbackLabel.setText("Successfully saved peripherals.");
            if (parentController != null) {
                parentController.refreshData();
            }
            closeButton.setDisable(false);
            // Don't close automatically, allow user to add more if needed
        });

        saveTask.setOnFailed(e -> {
            feedbackLabel.setText("Error: " + saveTask.getException().getMessage());
            saveButton.setDisable(false);
            closeButton.setDisable(false);
        });

        new Thread(saveTask).start();
    }

    private void savePeripheral(Connection conn, String category, String condition, String description, int quantity) throws SQLException {
        String sql = "INSERT INTO Peripherals (package_id, category, condition, description, receive_date, quantity) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, currentPackage.getPackageId());
            stmt.setString(2, category);
            stmt.setString(3, condition);
            stmt.setString(4, description);
            stmt.setString(5, LocalDate.now().toString());
            stmt.setInt(6, quantity);
            stmt.executeUpdate();
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}