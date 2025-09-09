package assettracking.controller;

import assettracking.dao.PackageDAO;
import assettracking.data.Package;
import assettracking.db.DatabaseConnection;
import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class PackageManagementController {

    // --- DAO and UI State ---
    private final PackageDAO packageDAO = new PackageDAO();
    private final ObservableList<Package> packageList = FXCollections.observableArrayList();
    // --- FXML Fields ---
    @FXML
    private Pagination pagination;
    @FXML
    private TextField searchField;
    @FXML
    private DatePicker fromDatePicker;
    @FXML
    private DatePicker toDatePicker;
    @FXML
    private ComboBox<Integer> rowsPerPageCombo;
    @FXML
    private TableView<Package> packageTable;
    @FXML
    private TableColumn<Package, String> trackingNumberCol;
    @FXML
    private TableColumn<Package, LocalDate> receiveDateCol;
    @FXML
    private TableColumn<Package, String> firstNameCol;
    @FXML
    private TableColumn<Package, String> lastNameCol;
    @FXML
    private TableColumn<Package, String> cityCol;
    @FXML
    private TableColumn<Package, String> stateCol;
    @FXML
    private TableColumn<Package, String> zipCodeCol;
    private int rowsPerPage = 200;
    private final ChangeListener<Object> filterChangeListener = (obs, oldVal, newVal) -> resetPagination();

    @FXML
    public void initialize() {
        setupTableColumns();
        setupDoubleClickHandling();
        setupFilterControls();
        resetPagination();
    }

    private void setupTableColumns() {
        trackingNumberCol.setCellValueFactory(new PropertyValueFactory<>("trackingNumber"));
        receiveDateCol.setCellValueFactory(new PropertyValueFactory<>("receiveDate"));
        firstNameCol.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        lastNameCol.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        cityCol.setCellValueFactory(new PropertyValueFactory<>("city"));
        stateCol.setCellValueFactory(new PropertyValueFactory<>("state"));
        zipCodeCol.setCellValueFactory(new PropertyValueFactory<>("zipCode"));
        packageTable.setItems(packageList);
        packageTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        packageTable.setPlaceholder(new Label("No packages found matching the current filters."));
    }

    private void setupDoubleClickHandling() {
        packageTable.setRowFactory(tv -> {
            TableRow<Package> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    openPackageDetail(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupFilterControls() {
        rowsPerPageCombo.getItems().addAll(50, 100, 200, 500);
        rowsPerPageCombo.setValue(rowsPerPage);
        rowsPerPageCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                rowsPerPage = newVal;
                resetPagination();
            }
        });

        pagination.currentPageIndexProperty().addListener((obs, oldIndex, newIndex) -> updateTableForPage(newIndex.intValue()));
        searchField.textProperty().addListener(filterChangeListener);
        fromDatePicker.valueProperty().addListener(filterChangeListener);
        toDatePicker.valueProperty().addListener(filterChangeListener);
    }

    private void resetPagination() {
        Task<Integer> countTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return packageDAO.countFilteredPackages(searchField.getText(), fromDatePicker.getValue(), toDatePicker.getValue());
            }
        };

        countTask.setOnSucceeded(e -> {
            int totalCount = countTask.getValue();
            int pageCount = (totalCount + rowsPerPage - 1) / rowsPerPage;
            pagination.setPageCount(pageCount > 0 ? pageCount : 1);
            if (pagination.getCurrentPageIndex() >= pageCount) {
                pagination.setCurrentPageIndex(0);
            }
            updateTableForPage(pagination.getCurrentPageIndex());
        });

        countTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to count packages.");
            });
        });
        new Thread(countTask).start();
    }

    private void updateTableForPage(int pageIndex) {
        String trackingFilter = searchField.getText();
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();

        Task<List<Package>> fetchTask = new Task<>() {
            @Override
            protected List<Package> call() throws Exception {
                return packageDAO.getFilteredPackagesForPage(trackingFilter, fromDate, toDate, rowsPerPage, pageIndex);
            }
        };

        fetchTask.setOnSucceeded(e -> packageList.setAll(fetchTask.getValue()));
        fetchTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to load package data.");
            });
        });
        new Thread(fetchTask).start();
    }

    @FXML
    private void handleOpenPackage() {
        Package selectedPackage = packageTable.getSelectionModel().getSelectedItem();
        if (selectedPackage != null) {
            openPackageDetail(selectedPackage);
        } else {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a package to open.");
        }
    }

    private void openPackageDetail(Package selectedPackage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/PackageDetail.fxml"));
            Parent root = loader.load();
            PackageDetailController controller = loader.getController();
            controller.initData(selectedPackage);
            Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Package Details", root);
            stage.showAndWait();
            resetPagination(); // Refresh data after the detail window is closed
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open the package detail window.");
        }
    }

    @FXML
    private void handleDeletePackage() {
        Package selectedPackage = packageTable.getSelectionModel().getSelectedItem();
        if (selectedPackage == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a package to delete.");
            return;
        }

        try {
            int assetCount = packageDAO.getAssetCountForPackage(selectedPackage.getPackageId());
            if (assetCount > 0) {
                showAlert(Alert.AlertType.ERROR, "Deletion Failed", "Cannot delete a package that contains assets. Please remove or reassign all " + assetCount + " assets from this package first.");
                return;
            }

            if (StageManager.showDeleteConfirmationDialog(getOwnerWindow(), "package", selectedPackage.getTrackingNumber())) {
                if (packageDAO.deletePackage(selectedPackage.getPackageId())) {
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Package " + selectedPackage.getTrackingNumber() + " was deleted.");
                    resetPagination(); // Refresh the table
                } else {
                    showAlert(Alert.AlertType.ERROR, "Deletion Failed", "An error occurred while deleting the package from the database.");
                }
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not check package contents: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        DatabaseConnection.refreshConnectionPool(); // <-- ADD THIS LINE
        resetPagination();
    }

    @FXML
    private void handleClearFilters() {
        searchField.textProperty().removeListener(filterChangeListener);
        fromDatePicker.valueProperty().removeListener(filterChangeListener);
        toDatePicker.valueProperty().removeListener(filterChangeListener);

        searchField.clear();
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);

        searchField.textProperty().addListener(filterChangeListener);
        fromDatePicker.valueProperty().addListener(filterChangeListener);
        toDatePicker.valueProperty().addListener(filterChangeListener);

        resetPagination();
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Platform.runLater(() -> StageManager.showAlert(getOwnerWindow(), alertType, title, message));
    }

    private Window getOwnerWindow() {
        return packageTable.getScene().getWindow();
    }
}