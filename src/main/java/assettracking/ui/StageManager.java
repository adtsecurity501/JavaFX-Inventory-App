package assettracking.ui;

import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

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

        // --- Create Custom Title Bar ---
        Label titleLabel = new Label(" " + title);
        titleLabel.getStyleClass().add("title-bar-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

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

        // --- Assemble the Scene ---
        BorderPane root = new BorderPane();
        root.setTop(titleBar);
        root.setCenter(content);

        // --- MODIFIED ---
        // This is the definitive fix. It applies the theme's main background color
        // to the root of the new window, ensuring it's always in sync with the current theme.
        root.setStyle("-fx-background-color: -color-bg-default;");

        Scene scene = new Scene(root);
        scene.getStylesheets().addAll(Application.getUserAgentStylesheet(), StageManager.class.getResource("/style.css").toExternalForm());

        stage.setScene(scene);
        return stage;
    }

    public static void showAlert(Window owner, Alert.AlertType alertType, String title, String contentText) {
        Label contentLabel = new Label(contentText);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(450);

        Button okButton = new Button("OK");
        okButton.getStyleClass().add("accent");

        VBox layout = new VBox(20, contentLabel, okButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));

        Stage dialogStage = createCustomStage(owner, title, layout);
        okButton.setOnAction(e -> dialogStage.close());

        dialogStage.showAndWait();
    }

    private static Button createWindowButton(String svgContent, Stage stage, String action) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgContent);
        icon.getStyleClass().add("window-icon");

        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("window-button");

        switch (action) {
            case "minimize" -> button.setOnAction(e -> stage.setIconified(true));
            case "maximize" -> button.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
            case "close" -> button.setOnAction(e -> stage.close());
        }

        return button;
    }
}