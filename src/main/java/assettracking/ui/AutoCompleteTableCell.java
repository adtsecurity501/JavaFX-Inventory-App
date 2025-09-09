package assettracking.ui;

import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.function.Function;

public class AutoCompleteTableCell<S> extends TableCell<S, String> {

    // MODIFIED: This now takes a Function that accepts the current text input
    private final Function<String, List<String>> suggestionProvider;
    private TextField textField;
    private AutoCompletePopup popup;

    public AutoCompleteTableCell(Function<String, List<String>> suggestionProvider) {
        this.suggestionProvider = suggestionProvider;
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

        // --- THIS IS THE KEY FIX ---
        // The AutoCompletePopup's suggestion provider is now a lambda that calls the
        // cell's suggestionProvider, passing in the textField's current text.
        // This ensures the suggestions are always relevant to what the user is typing.
        popup = new AutoCompletePopup(textField, () -> suggestionProvider.apply(textField.getText()));
        popup.setOnSuggestionSelected(this::commitEdit);
        // --- END OF FIX ---

        textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
        textField.setOnAction(event -> commitEdit(textField.getText()));
        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitEdit(textField.getText());
            }
        });
    }
}