package assettracking.controller;

import assettracking.db.DatabaseConnection;
import assettracking.data.Package;
import assettracking.dao.PackageDAO;
import assettracking.manager.StageManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PackageManagementController {

    // --- FXML Fields ---
    @FXML private Pagination pagination;
    @FXML private TextField searchField;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private ComboBox<Integer> rowsPerPageCombo;
    @FXML private TableView<Package> packageTable;
    @FXML private TableColumn<Package, String> trackingNumberCol;
    @FXML private TableColumn<Package, LocalDate> receiveDateCol;
    @FXML private TableColumn<Package, String> firstNameCol;
    @FXML private TableColumn<Package, String> lastNameCol;
    @FXML private TableColumn<Package, String> cityCol;
    @FXML private TableColumn<Package, String> stateCol;
    @FXML private TableColumn<Package, String> zipCodeCol;

    private PackageDAO packageDAO;
    private final ObservableList<Package> packageList = FXCollections.observableArrayList();
    private int rowsPerPage = 200;

    private final ChangeListener<Object> filterChangeListener = (obs, oldVal, newVal) -> resetPagination();


    @FXML
    public void initialize() {
        packageDAO = new PackageDAO();
        setupTableColumns();

        rowsPerPageCombo.getItems().addAll(50, 100, 200, 500);
        rowsPerPageCombo.setValue(rowsPerPage);
        rowsPerPageCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                rowsPerPage = newVal;
                resetPagination();
            }
        });

        pagination.currentPageIndexProperty().addListener((obs, oldIndex, newIndex) -> {
            updateTableForPage(newIndex.intValue());
        });

        searchField.textProperty().addListener(filterChangeListener);
        fromDatePicker.valueProperty().addListener(filterChangeListener);
        toDatePicker.valueProperty().addListener(filterChangeListener);

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
        // FIX: Replace the deprecated constant
        packageTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        packageTable.setPlaceholder(new Label("No packages found matching the current filters."));
    }

    private void resetPagination() {
        int totalCount = fetchPackageCount();
        int pageCount = (int) Math.ceil((double) totalCount / rowsPerPage);
        if (pageCount == 0) {
            pageCount = 1;
        }

        pagination.setPageCount(pageCount);

        if (pagination.getCurrentPageIndex() >= pageCount) {
            pagination.setCurrentPageIndex(0);
        }

        updateTableForPage(pagination.getCurrentPageIndex());
    }

    private void updateTableForPage(int pageIndex) {
        packageList.clear();
        QueryAndParams queryAndParams = buildFilteredQuery(false);

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql)) {

            int paramIndex = 1;
            for (Object param : queryAndParams.params) {
                stmt.setObject(paramIndex++, param);
            }
            stmt.setInt(paramIndex++, rowsPerPage);
            stmt.setInt(paramIndex, pageIndex * rowsPerPage);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                packageList.add(new Package(
                        rs.getInt("package_id"),
                        rs.getString("tracking_number"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("zip_code"),
                        LocalDate.parse(rs.getString("receive_date"))
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to load package data for page " + pageIndex);
        }
    }

    private int fetchPackageCount() {
        QueryAndParams queryAndParams = buildFilteredQuery(true);
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql)) {
            for (int i = 0; i < queryAndParams.params.size(); i++) {
                stmt.setObject(i + 1, queryAndParams.params.get(i));
            }
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to count packages.");
        }
        return 0;
    }

    private QueryAndParams buildFilteredQuery(boolean forCount) {
        String selectClause = forCount ? "SELECT COUNT(*) " : "SELECT * ";
        String fromClause = "FROM Packages";

        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();

        String trackingFilter = searchField.getText();
        if (trackingFilter != null && !trackingFilter.isEmpty()) {
            whereClause.append(" tracking_number LIKE ?");
            params.add("%" + trackingFilter + "%");
        }

        LocalDate fromDate = fromDatePicker.getValue();
        if (fromDate != null) {
            if (!whereClause.isEmpty()) whereClause.append(" AND");
            whereClause.append(" receive_date >= ?");
            params.add(fromDate.toString());
        }

        LocalDate toDate = toDatePicker.getValue();
        if (toDate != null) {
            if (!whereClause.isEmpty()) whereClause.append(" AND");
            whereClause.append(" receive_date <= ?");
            params.add(toDate.toString());
        }

        String fullQuery = selectClause + fromClause;
        if (!whereClause.isEmpty()) {
            fullQuery += " WHERE" + whereClause.toString();
        }

        if (!forCount) {
            fullQuery += " ORDER BY receive_date DESC LIMIT ? OFFSET ?";
        }

        return new QueryAndParams(fullQuery, params);
    }

    private record QueryAndParams(String sql, List<Object> params) {}

    @FXML
    private void handleFilter() {
        resetPagination();
    }

    @FXML
    private void handleRefresh() {
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

    @FXML
    private void handleOpenPackage() {
        Package selectedPackage = packageTable.getSelectionModel().getSelectedItem();
        if (selectedPackage != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/PackageDetail.fxml"));
                Parent root = loader.load();

                PackageDetailController controller = loader.getController();
                controller.initData(selectedPackage);

                Stage stage = StageManager.createCustomStage(getOwnerWindow(), "Package Details", root);
                stage.showAndWait();

                updateTableForPage(pagination.getCurrentPageIndex());

            } catch (IOException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Error", "Could not open the package detail window.");
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a package to open.");
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Platform.runLater(() -> StageManager.showAlert(getOwnerWindow(), alertType, title, message));
    }

    private Window getOwnerWindow() {
        return packageTable.getScene().getWindow();
    }
}