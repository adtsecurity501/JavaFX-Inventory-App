package assettracking.manager;

import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.scene.Scene;

import java.util.Objects;

public final class ThemeManager {

    public static void applyTheme(Scene scene, Theme theme) {
        // Step 1: Set the application-wide default theme. This is still crucial.
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());

        // Step 2: Explicitly clear any old styles from the scene.
        scene.getStylesheets().clear();

        // Step 3: Add both stylesheets to the scene's list IN THE CORRECT ORDER.
        // This forces JavaFX to load the theme first, defining the variables,
        // and then load your custom style.css, which uses those variables.
        scene.getStylesheets().addAll(theme.getUserAgentStylesheet(), Objects.requireNonNull(ThemeManager.class.getResource("/style.css")).toExternalForm());
    }
}