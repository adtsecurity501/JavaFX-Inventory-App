package assettracking;

/**
 * This is a launcher class to work around the JavaFX module system issues
 * with shaded (uber) JARs. It launches the real main application class.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}