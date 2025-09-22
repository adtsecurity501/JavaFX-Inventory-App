package assettracking.controller;

import atlantafx.base.theme.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;

public class MainViewController {
    private static MainViewController instance;

    public VBox dashboard;
    public Button closeButton;
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

    private double xOffset = 0;
    private double yOffset = 0;

    public static MainViewController getInstance() {
        return instance;
    }

    @FXML
    public void initialize() {
        instance = this;
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

    /**
     * Binds a background task's progress to the global progress bar and makes it visible.
     * Automatically handles unbinding and hiding when the task finishes.
     *
     * @param task The background task to monitor.
     */
    public void bindProgressBar(Task<?> task) {
        Platform.runLater(() -> {
            // Show the container and bind properties
            globalProgressContainer.setVisible(true);
            globalProgressContainer.setManaged(true);
            globalProgressBar.progressProperty().bind(task.progressProperty());

            // --- NEW: Bind the label to show the percentage ---
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

    /**
     * Hides the global progress bar and unbinds its properties.
     */
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