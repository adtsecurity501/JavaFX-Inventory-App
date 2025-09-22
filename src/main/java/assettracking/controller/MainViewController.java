package assettracking.controller;

import atlantafx.base.theme.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;

public class MainViewController {
    // --- THIS IS THE FIX (PART 1) ---
    private static MainViewController instance;
    // --- END OF FIX ---

    public VBox dashboard;
    public Button closeButton;
    public BorderPane iPadProvisioning;
    @FXML
    private HBox titleBar;
    @FXML
    private Button minimizeButton;
    @FXML
    private Button maximizeButton;
    @FXML
    private ComboBox<Theme> themeComboBox;
    @FXML
    private ProgressBar globalProgressBar;
    @FXML
    private HBox globalProgressContainer;
    @FXML
    private Label globalProgressLabel;
    @FXML
    private Tab iPadProvisioningTab;
    @FXML
    private iPadProvisioningController iPadProvisioningController;

    private double xOffset = 0;
    private double yOffset = 0;

    // --- THIS IS THE FIX (PART 2) ---
    public static MainViewController getInstance() {
        return instance;
    }
    // --- END OF FIX ---

    @FXML
    public void initialize() {
        // --- THIS IS THE FIX (PART 3) ---
        instance = this;
        // --- END OF FIX ---

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

        Platform.runLater(() -> {
            TabPane tabPane = iPadProvisioningTab.getTabPane();
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab == iPadProvisioningTab) {
                    iPadProvisioningController.refreshData();
                }
            });
        });
    }

    public void bindProgressBar(Task<?> task) {
        Platform.runLater(() -> {
            globalProgressContainer.setVisible(true);
            globalProgressContainer.setManaged(true);
            globalProgressBar.progressProperty().bind(task.progressProperty());

            task.progressProperty().addListener((obs, oldProgress, newProgress) -> {
                if (newProgress != null) {
                    globalProgressLabel.setText(String.format("%.0f%%", newProgress.doubleValue() * 100));
                }
            });
        });

        var originalOnSucceeded = task.getOnSucceeded();
        var originalOnFailed = task.getOnFailed();

        task.setOnSucceeded(e -> {
            hideProgressBar();
            if (originalOnSucceeded != null) {
                originalOnSucceeded.handle(e);
            }
        });

        task.setOnFailed(e -> {
            hideProgressBar();
            if (originalOnFailed != null) {
                originalOnFailed.handle(e);
            }
        });
    }

    public void hideProgressBar() {
        Platform.runLater(() -> {
            globalProgressBar.progressProperty().unbind();
            globalProgressBar.setProgress(0);
            globalProgressLabel.setText("0%");
            globalProgressContainer.setVisible(false);
            globalProgressContainer.setManaged(false);
        });
    }


    private void setupThemeComboBox() {
        themeComboBox.setItems(FXCollections.observableArrayList(new Dracula(), new CupertinoLight(), new CupertinoDark(), new NordLight(), new NordDark(), new PrimerLight(), new PrimerDark()));

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
        Platform.exit();
    }
}