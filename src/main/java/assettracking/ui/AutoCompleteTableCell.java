package assettracking.ui;

import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import java.util.List;
import java.util.function.Supplier;

public class AutoCompleteTableCell<S> extends TableCell<S, String> {

    private TextField textField;
    private AutoCompletePopup popup;
    private final Supplier<List<String>> suggestionProvider;

    public AutoCompleteTableCell(Supplier<List<String>> suggestionProvider) {
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
        // The popup is created here and its suggestion provider lambda
        // has access to the textField instance.
        popup = new AutoCompletePopup(textField, () -> suggestionProvider.get());
        popup.setOnSuggestionSelected(this::commitEdit);

        textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
        textField.setOnAction(event -> commitEdit(textField.getText()));
        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitEdit(textField.getText());
            }
        });
    }
}