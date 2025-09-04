package assettracking.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.WindowEvent;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AutoCompletePopup {
    private final TextField textField;
    private final Supplier<List<String>> suggestionProvider;
    private final ContextMenu contextMenu;
    private final ListView<String> suggestionList;
    private Consumer<String> onSuggestionSelected;

    // This flag will be controlled EXTERNALLY by the controller
    private boolean isSuppressed = false;

    public AutoCompletePopup(TextField textField, Supplier<List<String>> suggestionProvider) {
        this.textField = textField;
        this.suggestionProvider = suggestionProvider;
        this.suggestionList = new ListView<>();
        CustomMenuItem customMenuItem = new CustomMenuItem(suggestionList, false);
        this.contextMenu = new ContextMenu(customMenuItem);
        contextMenu.getStyleClass().add("autocomplete-popup");
        setupListeners();
    }

    // --- THIS IS THE NEW PUBLIC METHOD ---
    /**
     * Allows an external class (like a controller) to temporarily disable the suggestion listener.
     * @param suppress true to ignore text changes, false to resume normal behavior.
     */
    public void suppressListener(boolean suppress) {
        this.isSuppressed = suppress;
    }

    public AutoCompletePopup setOnSuggestionSelected(Consumer<String> onSuggestionSelected) {
        this.onSuggestionSelected = onSuggestionSelected;
        return this;
    }

    private void setupListeners() {
        // Dynamically bind the popup's width to the text field's width.
        // This ensures it always matches, regardless of the window or field size.
        textField.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.doubleValue() > 0) {
                double newWidth = newVal.doubleValue();
                // Set the width of the outer popup window
                contextMenu.setPrefWidth(newWidth);
                // Set the width of the inner list view, subtracting a small amount for padding
                suggestionList.setPrefWidth(newWidth - 3);
            }
        });
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isSuppressed) {
                return;
            }
            if (newVal == null || newVal.length() < 2) {
                contextMenu.hide();
            } else {
                updateSuggestions(newVal);
            }
        });

        suggestionList.setOnMouseClicked(event -> selectAndHide());
        suggestionList.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                selectAndHide();
            }
        });

        textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                contextMenu.hide();
            }
        });

        contextMenu.addEventFilter(WindowEvent.WINDOW_HIDDEN, event -> textField.positionCaret(textField.getLength()));
    }

    private void updateSuggestions(String input) {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return suggestionProvider.get();
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            ObservableList<String> suggestions = FXCollections.observableArrayList(task.getValue());
            if (suggestions.isEmpty() || isSuppressed) { // Also check suppression here
                contextMenu.hide();
            } else {
                suggestionList.setItems(suggestions);
                if (!contextMenu.isShowing()) {
                    contextMenu.show(textField, Side.BOTTOM, 0, 0);
                }
            }
        }));
        new Thread(task).start();
    }

    private void selectAndHide() {
        String selected = suggestionList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (onSuggestionSelected != null) {
                onSuggestionSelected.accept(selected);
            } else {
                textField.setText(selected);
            }
            contextMenu.hide();
        }
    }
}