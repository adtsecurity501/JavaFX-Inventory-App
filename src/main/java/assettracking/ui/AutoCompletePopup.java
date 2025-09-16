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

    // --- NEW PUBLIC METHOD TO CONTROL THE LISTENER ---
    public void suppressListener(boolean suppress) {
        this.isSuppressed = suppress;
    }

    public AutoCompletePopup setOnSuggestionSelected(Consumer<String> onSuggestionSelected) {
        this.onSuggestionSelected = onSuggestionSelected;
        return this;
    }

    private void setupListeners() {
        textField.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.doubleValue() > 0) {
                double newWidth = newVal.doubleValue();
                contextMenu.setPrefWidth(newWidth);
                suggestionList.setPrefWidth(newWidth - 3);
            }
        });

        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            // If the listener is suppressed, do nothing.
            if (isSuppressed) {
                return;
            }
            if (newVal == null || newVal.isEmpty()) {
                contextMenu.hide();
            } else {
                updateSuggestions();
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

    private void updateSuggestions() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return suggestionProvider.get();
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            ObservableList<String> suggestions = FXCollections.observableArrayList(task.getValue());

            // --- THIS IS THE FIX ---
            // Before showing the popup, we must check if the text field is still part of a visible scene and window.
            // This prevents the error if the user closes the dialog while the suggestions are loading.
            if (suggestions.isEmpty() || isSuppressed || textField.getScene() == null || textField.getScene().getWindow() == null) {
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
            // --- THIS IS THE KEY FIX ---
            // Suppress the listener, set the text, then un-suppress it.
            suppressListener(true);
            if (onSuggestionSelected != null) {
                onSuggestionSelected.accept(selected);
            } else {
                textField.setText(selected);
                textField.positionCaret(textField.getLength());
            }
            suppressListener(false);
            // --- END OF FIX ---
            contextMenu.hide();
        }
    }
}