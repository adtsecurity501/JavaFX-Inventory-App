package assettracking;

import assettracking.controller.DashboardController;
import assettracking.db.DatabaseConnection;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

public class MainApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    public static void applyTheme(Scene scene, Theme theme) {
        try {
            // Get the URL for both stylesheets
            URL themeUrl = new URL(theme.getUserAgentStylesheet());
            URL customUrl = MainApp.class.getResource("/style.css");

            if (customUrl == null) {
                // If style.css is missing, just apply the theme
                Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
                return;
            }

            // This is a special syntax that tells JavaFX to load our custom CSS
            // and to @import the theme's CSS first. This guarantees that the theme's
            // variables are defined before our custom rules use them.
            String combinedCss = String.format("@import url(\"%s\");", themeUrl.toExternalForm());

            // Apply this combined logic to the scene
            scene.getStylesheets().clear();
            scene.getStylesheets().add(customUrl.toExternalForm()); // Add your css file

            // Prepend the import to your existing css data by inserting it at the first position
            // This is a bit of a trick, but it forces the @import to be the first rule, which is required by CSS.
            String originalCss = scene.getStylesheets().get(0);
            scene.getStylesheets().set(0, combinedCss + originalCss);

            // Set the application-wide theme for any new windows
            Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());

        } catch (Exception e) {
            System.err.println("Failed to apply theme stylesheets.");
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        Application.setUserAgentStylesheet(new Dracula().getUserAgentStylesheet());

        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Shutdown signal received, closing resources...");
            DatabaseConnection.closeConnectionPool();
        });

        primaryStage.initStyle(StageStyle.UNDECORATED);

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/MainView.fxml"));
        Parent mainView = mainLoader.load();

        StackPane rootPane = new StackPane(mainView);
        Map<String, Object> namespace = mainLoader.getNamespace();
        DashboardController dashboardController = (DashboardController) namespace.get("dashboardController");

        if (dashboardController != null) {
            dashboardController.init(rootPane);
        }

        Scene scene = new Scene(rootPane, 1600, 900);

        primaryStage.setTitle("Inventory and Package Management System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}