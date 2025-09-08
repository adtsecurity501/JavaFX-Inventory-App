package assettracking;

import atlantafx.base.theme.Dracula;
import assettracking.controller.DashboardController;
import assettracking.db.DatabaseConnection;
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

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Shutdown signal received, closing resources...");
            DatabaseConnection.closeConnectionPool();
        });

        // Reverted to UNDECORATED
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
}