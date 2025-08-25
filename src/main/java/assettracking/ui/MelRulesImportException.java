package assettracking.ui;

/**
 * A custom exception for handling specific, user-facing errors during the MEL rules import process.
 */
public class MelRulesImportException extends Exception {
    public MelRulesImportException(String message) {
        super(message);
    }
}