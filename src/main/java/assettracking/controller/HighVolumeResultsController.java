package assettracking.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HighVolumeResultsController {

    @FXML
    private Label headerLabel;
    @FXML
    private ListView<String> resultsListView;
    // Field to store the search term for highlighting
    private String searchTerm;
    private Consumer<List<String>> onRemoveCallback;

    @FXML
    private void handleSelectAll() {
        resultsListView.getSelectionModel().selectAll();
    }

    @FXML
    private void handleSelectNone() {
        resultsListView.getSelectionModel().clearSelection();
    }

    @FXML
    public void initialize() {
        resultsListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // This is the core of the highlighting feature
        resultsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    TextFlow textFlow = createHighlightedText(item, searchTerm);
                    setGraphic(textFlow);
                }
            }
        });
    }

    public void initData(String searchTerm, String source, int count, List<MachineRemovalController.SearchResult> allResults, Consumer<List<String>> onRemoveCallback) {
        this.searchTerm = searchTerm;
        this.onRemoveCallback = onRemoveCallback;
        headerLabel.setText(String.format("The search term '%s' returned %d results from %s.", searchTerm, count, source));

        List<String> computerNames = allResults.stream().map(MachineRemovalController.SearchResult::computerName).collect(Collectors.toList());
        resultsListView.setItems(FXCollections.observableArrayList(computerNames));
    }


    @FXML
    private void handleDismiss() {
        ((Stage) headerLabel.getScene().getWindow()).close();
    }

    @FXML
    private void handleRemoveSelected() {
        List<String> selectedItems = resultsListView.getSelectionModel().getSelectedItems();
        if (selectedItems != null && !selectedItems.isEmpty() && onRemoveCallback != null) {
            // Execute the callback, passing the list of names to remove
            onRemoveCallback.accept(selectedItems);

            // Also remove them from this pop-up's list for immediate feedback
            resultsListView.getItems().removeAll(selectedItems);
        }
    }

    // Helper method to create styled text for the list cell
    private TextFlow createHighlightedText(String fullText, String termToHighlight) {
        TextFlow textFlow = new TextFlow();
        if (termToHighlight == null || termToHighlight.isEmpty()) {
            textFlow.getChildren().add(new Text(fullText));
            return textFlow;
        }

        int index = fullText.toLowerCase().indexOf(termToHighlight.toLowerCase());
        if (index == -1) {
            textFlow.getChildren().add(new Text(fullText));
        } else {
            Text preMatch = new Text(fullText.substring(0, index));

            Text match = new Text(fullText.substring(index, index + termToHighlight.length()));
            match.setStyle("-fx-font-weight: bold; -fx-fill: -color-accent-emphasis;");

            Text postMatch = new Text(fullText.substring(index + termToHighlight.length()));

            textFlow.getChildren().addAll(preMatch, match, postMatch);
        }
        return textFlow;
    }
}