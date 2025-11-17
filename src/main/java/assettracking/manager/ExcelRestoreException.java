package assettracking.manager;

/**
 * A custom exception for handling specific, user-facing errors during the Excel restore process.
 */
public class ExcelRestoreException extends Exception {
    public ExcelRestoreException(String message) {
        super(message);
    }
}