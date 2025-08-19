package assettracking.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem; // ### FIX: Added missing import ###
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

    public AutoCompletePopup(TextField textField, Supplier<List<String>> suggestionProvider) {
        this.textField = textField;
        this.suggestionProvider = suggestionProvider;
        this.suggestionList = new ListView<>();

        // ### FIX: Correct way to put a ListView in a ContextMenu ###
        CustomMenuItem customMenuItem = new CustomMenuItem(suggestionList, false);
        this.contextMenu = new ContextMenu(customMenuItem);

        setupListeners();
    }

    public AutoCompletePopup setOnSuggestionSelected(Consumer<String> onSuggestionSelected) {
        this.onSuggestionSelected = onSuggestionSelected;
        return this;
    }

    private void setupListeners() {
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
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

        // ### FIX: Replaced textField.end() with the correct method ###
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
            if (suggestions.isEmpty()) {
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