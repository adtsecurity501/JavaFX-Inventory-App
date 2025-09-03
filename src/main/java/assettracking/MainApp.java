package assettracking;// In assettracking/MainApp.java

import atlantafx.base.theme.Dracula;
import assettracking.controller.DashboardController;
import assettracking.db.DatabaseConnection; // <-- IMPORT THIS
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.Map;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        // --- ADD THIS SHUTDOWN HOOK ---
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Shutdown signal received, closing resources...");
            DatabaseConnection.closeConnectionPool(); // Gracefully close the pool
        });
        // --- END OF ADDITION ---

        primaryStage.initStyle(StageStyle.UNDECORATED);
        Application.setUserAgentStylesheet(new Dracula().getUserAgentStylesheet());

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/MainView.fxml"));
        Parent mainView = mainLoader.load();

        StackPane rootPane = new StackPane(mainView);
        Map<String, Object> namespace = mainLoader.getNamespace();
        DashboardController dashboardController = (DashboardController) namespace.get("dashboardController");

        if (dashboardController != null) {
            dashboardController.init(rootPane);
        }

        Scene scene = new Scene(rootPane, 1600, 900);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("Inventory and Package Management System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}