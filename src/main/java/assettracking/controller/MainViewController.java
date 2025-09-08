package assettracking.controller;

import atlantafx.base.theme.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;

public class MainViewController {

    @FXML
    private HBox titleBar;
    @FXML
    private Button minimizeButton;
    @FXML
    private Button maximizeButton;
    @FXML
    private Button closeButton;
    @FXML
    private ComboBox<Theme> themeComboBox;

    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
        // Window dragging logic
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        titleBar.setOnMouseDragged(event -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });

        setupThemeComboBox();
    }

    private void setupThemeComboBox() {
        themeComboBox.setItems(FXCollections.observableArrayList(
                new Dracula(),
                new CupertinoLight(),
                new CupertinoDark(),
                new NordLight(),
                new NordDark(),
                new PrimerLight(),
                new PrimerDark()
        ));

        Callback<ListView<Theme>, ListCell<Theme>> cellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(Theme theme, boolean empty) {
                super.updateItem(theme, empty);
                setText(empty ? "" : theme.getName());
            }
        };

        themeComboBox.setCellFactory(cellFactory);
        themeComboBox.setButtonCell(cellFactory.call(null));
        themeComboBox.getSelectionModel().selectFirst();

        themeComboBox.valueProperty().addListener((obs, oldTheme, newTheme) -> {
            if (newTheme != null) {
                Application.setUserAgentStylesheet(newTheme.getUserAgentStylesheet());
            }
        });
    }

    // --- MODIFIED ---
    // These methods now handle the primary stage specifically.
    @FXML
    private void handleMinimize() {
        Stage stage = (Stage) minimizeButton.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize() {
        Stage stage = (Stage) maximizeButton.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void handleClose() {
        // This command should only be in the main window controller.
        Platform.exit();
    }
}