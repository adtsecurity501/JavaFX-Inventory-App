package assettracking.ui;

import assettracking.dao.AssetDAO;
import assettracking.data.AssetEntry;
import javafx.application.Platform;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

public class SerialLookupTableCell extends TableCell<AssetEntry, String> {

    private final AssetDAO assetDAO;
    private TextField textField;

    public SerialLookupTableCell(AssetDAO assetDAO) {
        this.assetDAO = assetDAO;
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
        textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);

        // This is the core logic for the autofill feature
        textField.setOnAction(event -> {
            String serial = textField.getText();
            commitEdit(serial); // First, commit the serial number change itself

            if (serial != null && !serial.isEmpty()) {
                assetDAO.findAssetBySerialNumber(serial).ifPresent(assetInfo -> {
                    AssetEntry currentEntry = getTableView().getItems().get(getIndex());
                    // Use Platform.runLater to avoid modifying the table during a render pass
                    Platform.runLater(() -> {
                        currentEntry.setCategory(assetInfo.getCategory());
                        currentEntry.setMake(assetInfo.getMake());
                        currentEntry.setModelNumber(assetInfo.getModelNumber());
                        currentEntry.setDescription(assetInfo.getDescription());
                        currentEntry.setImei(assetInfo.getImei());
                    });
                });
            }
        });

        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitEdit(textField.getText());
            }
        });
    }
}