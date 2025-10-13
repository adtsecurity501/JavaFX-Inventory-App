package assettracking.manager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Objects;
import java.util.Optional;

public final class StageManager {

    private static double xOffset = 0;
    private static double yOffset = 0;

    public static Stage createCustomStage(Window owner, String title, Node content) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }

        Label titleLabel = new Label(" " + title);
        titleLabel.getStyleClass().add("title-bar-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // --- THE LOGIC IS NOW HANDLED INSIDE THIS METHOD ---
        Button minimizeButton = createWindowButton("M 0 5 H 10", stage, "minimize");
        Button maximizeButton = createWindowButton("M 0 1 H 9 V 10 H 0 Z", stage, "maximize");
        Button closeButton = createWindowButton("M 0 0 L 10 10 M 10 0 L 0 10", stage, "close");
        closeButton.getStyleClass().add("window-close-button");

        HBox titleBar = new HBox(10, titleLabel, spacer, minimizeButton, maximizeButton, closeButton);
        titleBar.getStyleClass().add("title-bar");
        titleBar.setPadding(new Insets(0, 0, 0, 10));
        titleBar.setAlignment(Pos.CENTER_LEFT);

        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });

        BorderPane root = new BorderPane();
        root.setTop(titleBar);
        root.setCenter(content);
        root.setStyle("-fx-background-color: -color-bg-default;");

        Scene scene = new Scene(root);
        scene.getStylesheets().addAll(Application.getUserAgentStylesheet(), Objects.requireNonNull(StageManager.class.getResource("/style.css")).toExternalForm());

        stage.setScene(scene);
        stage.sizeToScene();

        if (owner != null) {
            Platform.runLater(() -> {
                double ownerX = owner.getX();
                double ownerY = owner.getY();
                double ownerWidth = owner.getWidth();
                double ownerHeight = owner.getHeight();
                double stageWidth = stage.getWidth();
                double stageHeight = stage.getHeight();
                stage.setX(ownerX + (ownerWidth - stageWidth) / 2);
                stage.setY(ownerY + (ownerHeight - stageHeight) / 2);
            });
        }

//        Platform.runLater(content::requestFocus);
        return stage;
    }

//    public static void showProgressDialog(Window owner, String title, Task<?> task) {
//        ProgressIndicator progressIndicator = new ProgressIndicator();
//        progressIndicator.progressProperty().bind(task.progressProperty());
//
//        Label titleLabel = new Label(title);
//        titleLabel.getStyleClass().add("h4");
//
//        VBox box = new VBox(20, titleLabel, progressIndicator);
//        box.setAlignment(Pos.CENTER);
//        box.setPadding(new Insets(30));
//
//        Stage dialogStage = createCustomStage(owner, "Working...", box);
//        dialogStage.initModality(Modality.APPLICATION_MODAL); // Block interaction with parent
//
//        task.setOnRunning(e -> dialogStage.show());
//        task.setOnSucceeded(e -> dialogStage.close());
//        task.setOnFailed(e -> dialogStage.close());
//    }

    public static boolean showDeleteConfirmationDialog(Window owner, String objectType, String objectName) {
        // Use a boolean array to hold the result, as it needs to be effectively final for the lambda
        final boolean[] result = {false};

        // 1. Create the UI components for the dialog
        Label headerLabel = new Label("Are you absolutely sure you want to delete this " + objectType + "?");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");

        Label contentLabel = new Label("This action cannot be undone. To confirm, please type DELETE into the box below.\n\n" + objectName);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(450);

        TextField inputField = new TextField();
        inputField.setPromptText("DELETE");

        Button okButton = new Button("Confirm Deletion");
        okButton.getStyleClass().add("danger"); // Use danger style for a delete button
        okButton.setDefaultButton(true);
        okButton.setDisable(true); // Start disabled

        Button cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);

        // 2. Add listener to enable the OK button only when "DELETE" is typed
        inputField.textProperty().addListener((observable, oldValue, newValue) -> okButton.setDisable(!newValue.trim().equals("DELETE")));

        // 3. Arrange components in the layout
        HBox buttonBar = new HBox(10, cancelButton, okButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        VBox layout = new VBox(20, headerLabel, new Separator(), contentLabel, inputField, buttonBar);
        layout.setPadding(new Insets(20));

        // 4. Create a STAGE, which we can fully style, instead of a Dialog
        Stage dialogStage = createCustomStage(owner, "Confirm Deletion", layout);

        // 5. Set the actions for the buttons
        okButton.setOnAction(e -> {
            result[0] = true;
            dialogStage.close();
        });
        cancelButton.setOnAction(e -> {
            result[0] = false;
            dialogStage.close();
        });

        // Set focus to the input field after the stage is shown
        Platform.runLater(inputField::requestFocus);

        // 6. Show the stage and wait for it to be closed
        dialogStage.showAndWait();

        return result[0];
    }

    // --- THIS IS THE CORRECTED METHOD ---
    private static Button createWindowButton(String svgContent, Stage stage, String action) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgContent);
        icon.getStyleClass().add("window-icon");

        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("window-button");

        // This switch block sets the action for each button. This was the missing piece.
        switch (action) {
            case "minimize":
                button.setOnAction(e -> stage.setIconified(true));
                break;
            case "maximize":
                button.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
                break;
            case "close":
                button.setOnAction(e -> stage.close());
                break;
        }
        return button;
    }

    // --- The helper methods below are unchanged and correct ---

    public static boolean showConfirmationDialog(Window owner, String title, String headerText, String contentText) {
        final boolean[] result = {false};
        Label headerLabel = new Label(headerText);
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
        Label contentLabel = new Label(contentText);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(450);
        Button okButton = new Button("OK");
        okButton.getStyleClass().add("success");
        okButton.setDefaultButton(true);
        Button cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        HBox buttonBar = new HBox(10, cancelButton, okButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        VBox layout = new VBox(20, headerLabel, new Separator(), contentLabel, buttonBar);
        layout.setPadding(new Insets(20));
        Stage dialogStage = createCustomStage(owner, title, layout);
        okButton.setOnAction(e -> {
            result[0] = true;
            dialogStage.close();
        });
        cancelButton.setOnAction(e -> {
            result[0] = false;
            dialogStage.close();
        });
        dialogStage.showAndWait();
        return result[0];
    }

    public static Optional<String> showTextInputDialog(Window owner, String title, String headerText, String contentText, String initialValue) {
        final String[] result = {null};
        Label headerLabel = new Label(headerText);
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
        Label contentLabel = new Label(contentText);
        TextField inputField = new TextField(initialValue);
        Button okButton = new Button("OK");
        okButton.getStyleClass().add("success");
        okButton.setDefaultButton(true);
        Button cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        HBox buttonBar = new HBox(10, cancelButton, okButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        VBox layout = new VBox(15, headerLabel, contentLabel, inputField, buttonBar);
        layout.setPadding(new Insets(20));
        Stage dialogStage = createCustomStage(owner, title, layout);
        okButton.setOnAction(e -> {
            result[0] = inputField.getText();
            dialogStage.close();
        });
        cancelButton.setOnAction(e -> dialogStage.close());
        dialogStage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    public static void showAlert(Window owner, Alert.AlertType alertType, String title, String contentText) {
        Label contentLabel = new Label(contentText);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(450);
        Button okButton = new Button("OK");
        okButton.getStyleClass().add("accent");
        okButton.setDefaultButton(true);
        VBox layout = new VBox(20, contentLabel, okButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        Stage dialogStage = createCustomStage(owner, title, layout);
        okButton.setOnAction(e -> dialogStage.close());
        dialogStage.showAndWait();
    }
}