package assettracking.controller;

import assettracking.dao.PackageDAO;
import assettracking.data.Package;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class SelectPackageDialogController {

    private final PackageDAO packageDAO = new PackageDAO();
    private final ObservableList<Package> packageList = FXCollections.observableArrayList();
    public Button createNewButton;
    @FXML
    private TextField searchField;
    @FXML
    private TableView<Package> packageTable;
    @FXML
    private TableColumn<Package, String> trackingNumberCol;
    @FXML
    private TableColumn<Package, String> senderNameCol;
    @FXML
    private TableColumn<Package, LocalDate> receiveDateCol;
    @FXML
    private Button selectButton;
    @FXML
    private SelectionResult result;

    @FXML
    public void initialize() {
        trackingNumberCol.setCellValueFactory(new PropertyValueFactory<>("trackingNumber"));
        senderNameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFirstName() + " " + cellData.getValue().getLastName()));
        receiveDateCol.setCellValueFactory(new PropertyValueFactory<>("receiveDate"));
        packageTable.setItems(packageList);

        searchPackages("");

        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchPackages(newVal));
        packageTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> selectButton.setDisable(newVal == null));
    }

    private void searchPackages(String query) {
        try {
            List<Package> results = packageDAO.searchPackagesByTracking(query);
            packageList.setAll(results);
        } catch (Exception e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void handleSelect() {
        Package selected = packageTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            result = new SelectionResult(selected, false);
            closeStage();
        }
    }

    @FXML
    private void handleCreateNew() {
        result = new SelectionResult(null, true);
        closeStage();
    }

    public Optional<SelectionResult> getResult() {
        return Optional.ofNullable(result);
    }

    private void closeStage() {
        ((Stage) selectButton.getScene().getWindow()).close();
    }

    public record SelectionResult(Package selectedPackage, boolean createNew) {
    }
}