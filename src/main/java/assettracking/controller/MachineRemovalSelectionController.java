package assettracking.controller;

import assettracking.manager.MachineRemovalService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MachineRemovalSelectionController {

    @FXML private Label headerLabel;
    @FXML private VBox adResultsBox;
    @FXML private VBox sccmResultsBox;
    @FXML private Button removeButton;
    @FXML private Label statusLabel;

    private final MachineRemovalService removalService = new MachineRemovalService();

    public void initData(String serialNumber, List<MachineRemovalService.SearchResult> results) {
        headerLabel.setText("Multiple potential matches were found for serial number '" + serialNumber + "'. Please select the machine(s) you want to remove.");

        List<MachineRemovalService.SearchResult> adResults = results.stream()
                .filter(r -> "AD".equalsIgnoreCase(r.source()))
                .collect(Collectors.toList());

        List<MachineRemovalService.SearchResult> sccmResults = results.stream()
                .filter(r -> "SCCM".equalsIgnoreCase(r.source()))
                .collect(Collectors.toList());

        populateResults(adResultsBox, adResults);
        populateResults(sccmResultsBox, sccmResults);
    }

    private void populateResults(VBox container, List<MachineRemovalService.SearchResult> results) {
        if (results.isEmpty()) {
            container.getChildren().add(new Label("No results found in this source."));
        } else {
            for (MachineRemovalService.SearchResult result : results) {
                CheckBox cb = new CheckBox(result.computerName());
                cb.setUserData(result);
                container.getChildren().add(cb);
            }
        }
    }

    @FXML
    private void handleRemove() {
        List<String> namesToRemove = new ArrayList<>();
        adResultsBox.getChildren().stream()
                .filter(node -> node instanceof CheckBox && ((CheckBox) node).isSelected())
                .map(node -> ((MachineRemovalService.SearchResult) node.getUserData()).computerName())
                .forEach(namesToRemove::add);

        sccmResultsBox.getChildren().stream()
                .filter(node -> node instanceof CheckBox && ((CheckBox) node).isSelected())
                .map(node -> ((MachineRemovalService.SearchResult) node.getUserData()).computerName())
                .forEach(namesToRemove::add);

        if (namesToRemove.isEmpty()) {
            statusLabel.setText("Please select at least one machine to remove.");
            return;
        }

        removeButton.setDisable(true);
        statusLabel.setText("Removing " + namesToRemove.size() + " machine(s)...");

        removalService.remove(namesToRemove).thenAccept(log ->
                Platform.runLater(() -> {
                    // You could pass this log back to the main window, but for now, we just close.
                    System.out.println("Manual Removal Log: " + String.join("\n", log));
                    handleCancel(); // Close the dialog on success
                })
        );
    }

    @FXML
    private void handleCancel() {
        ((Stage) removeButton.getScene().getWindow()).close();
    }
}