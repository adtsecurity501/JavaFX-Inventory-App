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
     * This method preserves any existing onSucceeded or onFailed handlers on the task.
     *
     * @param task The background task to monitor.
     */
    public void bindProgressBar(Task<?> task) {
        // Show the bar and bind its progress property
        Platform.runLater(() -> {
            globalProgressBar.setVisible(true);
            globalProgressBar.setManaged(true);
            globalProgressBar.progressProperty().bind(task.progressProperty());
        });

        // --- THIS IS THE FIX ---
        // Store the original event handlers that were set on the task before it was passed to this method.
        var originalOnSucceeded = task.getOnSucceeded();
        var originalOnFailed = task.getOnFailed();

        // Set our new success handler.
        task.setOnSucceeded(e -> {
            // First, do our new task: hide the progress bar.
            hideProgressBar();
            // Then, if an original handler existed, execute it.
            if (originalOnSucceeded != null) {
                originalOnSucceeded.handle(e);
            }
        });

        // Set our new failure handler.
        task.setOnFailed(e -> {
            // First, do our new task: hide the progress bar.
            hideProgressBar();
            // Then, if an original handler existed, execute it.
            if (originalOnFailed != null) {
                originalOnFailed.handle(e);
            }
        });
    }

    /**
     * Hides the global progress bar and unbinds its progress property.
     */
    public void hideProgressBar() {
        Platform.runLater(() -> {
            globalProgressBar.progressProperty().unbind();
            globalProgressBar.setProgress(0);
            globalProgressBar.setVisible(false);
            globalProgressBar.setManaged(false);
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