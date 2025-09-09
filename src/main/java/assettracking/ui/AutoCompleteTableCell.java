package assettracking.ui;

import assettracking.data.AssetEntry;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class AutoCompleteTableCell<S> extends TableCell<S, String> {

    private final Function<String, List<String>> suggestionProvider;
    private final BiConsumer<S, String> onSuggestionSelectedCallback;
    private TextField textField;
    private AutoCompletePopup popup;

    // Overloaded constructor for simple cases that don't need cross-cell updates
    public AutoCompleteTableCell(Function<String, List<String>> suggestionProvider) {
        this(suggestionProvider, null);
    }

    // Main constructor that accepts the callback
    public AutoCompleteTableCell(Function<String, List<String>> suggestionProvider, BiConsumer<S, String> onSuggestionSelectedCallback) {
        this.suggestionProvider = suggestionProvider;
        this.onSuggestionSelectedCallback = onSuggestionSelectedCallback;
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (textField == null) {
            createTextField();
        }
        setText(null);
        setGraphic(textField);
        textField.setText(getItem());
        textField.selectAll();
        textField.requestFocus();
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem());
        setGraphic(null);
    }

    @Override
    public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            if (isEditing()) {
                if (textField != null) {
                    textField.setText(getItem());
                }
                setText(null);
                setGraphic(textField);
            } else {
                setText(getItem());
                setGraphic(null);
            }
        }
    }

    private void createTextField() {
        textField = new TextField(getItem());

        popup = new AutoCompletePopup(textField, () -> suggestionProvider.apply(textField.getText()));

        // --- THIS IS THE KEY CHANGE ---
        // When a suggestion is selected, we now first execute the callback
        // before committing the edit to the current cell.
        popup.setOnSuggestionSelected(selectedValue -> {
            if (onSuggestionSelectedCallback != null) {
                // Get the data object (AssetEntry) for the current row
                S currentItem = getTableView().getItems().get(getIndex());
                // Execute the callback, passing the row's data object and the selected value
                onSuggestionSelectedCallback.accept(currentItem, selectedValue);
            }
            // Now, commit the edit for this specific cell
            commitEdit(selectedValue);
        });
        // --- END OF KEY CHANGE ---

        textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
        textField.setOnAction(event -> commitEdit(textField.getText()));
        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitEdit(textField.getText());
            }
        });
    }
}