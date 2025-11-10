package assettracking.controller;

import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MachineRemovalController {

    private static int highVolumeAlertOffset = 0;
    private final ObservableList<SearchResult> results = FXCollections.observableArrayList();

    @FXML
    private TextArea txtSerials;
    @FXML
    private RadioButton rdoAD, rdoSCCM, rdoBoth;
    @FXML
    private Button btnSearch;
    @FXML
    private ListView<SearchResult> lstResults;
    @FXML
    private Button btnRemove;
    @FXML
    private TextArea txtLog;
    @FXML
    private Label lblStatus;
    @FXML
    private ProgressBar progressBarMain;

    private int progressCounter = 0;

    @FXML
    public void initialize() {
        // ... (this method is unchanged) ...
        lstResults.setItems(results);
        lstResults.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lstResults.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SearchResult item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("text-default", "text-info", "text-danger");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    setOnMouseClicked(null);
                } else {
                    setText(item.getDisplayName());
                    getStyleClass().add(item.getStyleClass());
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem copyItem = new MenuItem("Copy Computer Name");
                    copyItem.setOnAction(event -> {
                        if (item.computerName() != null && !item.computerName().startsWith("[")) {
                            copyToClipboard(item.computerName());
                        }
                    });
                    copyItem.setDisable(item.computerName() == null || item.computerName().startsWith("["));
                    contextMenu.getItems().add(copyItem);
                    setContextMenu(contextMenu);
                    setOnMouseClicked(event -> {
                        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                            if (item.computerName() != null && !item.computerName().startsWith("[")) {
                                copyToClipboard(item.computerName());
                            }
                        }
                    });
                }
            }
        });
    }

    @FXML
    private void handleSearch() {
        // The getSearchTerms() method still returns a List with potential duplicates.
        List<String> originalSearchTerms = getSearchTerms();
        if (originalSearchTerms.isEmpty()) {
            writeLog("WARN", "No search terms entered.");
            return;
        }

        // Convert the List to a Set to automatically remove all duplicates,
        // then convert it back to a List for the PowerShell script.
        List<String> uniqueSearchTerms = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(originalSearchTerms));

        // Log if duplicates were found and removed.
        int duplicatesRemoved = originalSearchTerms.size() - uniqueSearchTerms.size();
        if (duplicatesRemoved > 0) {
            writeLog("INFO", "Removed " + duplicatesRemoved + " duplicate search term(s).");
        }

        // Reset the cascade offset for each new search operation.
        highVolumeAlertOffset = 0;

        String source = getSearchSource();
        progressCounter = 0;

        // Execute the task with the de-duplicated list.
        executePowerShellTask("search", uniqueSearchTerms, source, "Searching for " + uniqueSearchTerms.size() + " unique item(s)...");
    }

    private void executePowerShellTask(String mode, List<String> targets, String source, String statusMessage) {
        int totalTargets = targets.size();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // ... (this part is unchanged) ...
                Platform.runLater(() -> {
                    lblStatus.setText(statusMessage);
                    btnSearch.setDisable(true);
                    btnRemove.setDisable(true);
                    if ("search".equals(mode)) {
                        results.clear();
                        progressBarMain.setProgress(0.0);
                    } else {
                        progressBarMain.setProgress(-1.0);
                    }
                });
                Path scriptPath = Files.createTempFile("ps_script_", ".ps1");
                try (InputStream is = getClass().getResourceAsStream("/FindAndRemove.ps1")) {
                    if (is == null) throw new IOException("Could not find FindAndRemove.ps1 in resources.");
                    Files.copy(is, scriptPath, StandardCopyOption.REPLACE_EXISTING);
                }
                String targetString = String.join(",", targets);
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptPath.toAbsolutePath().toString(), "-" + mode, "-targets", targetString);
                if ("search".equals(mode)) {
                    pb.command().add("-source");
                    pb.command().add(source);
                }
                Process process = pb.start();
                Thread outThread = new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            final String ln = line;
                            Platform.runLater(() -> processOutput(ln, mode, totalTargets));
                        }
                    } catch (IOException ioe) {
                        Platform.runLater(() -> writeLog("ERROR", "Failed reading process output: " + ioe.getMessage()));
                    }
                });
                Thread errThread = new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            final String ln = line;
                            Platform.runLater(() -> writeLog("ERROR", ln));
                        }
                    } catch (IOException ioe) {
                        Platform.runLater(() -> writeLog("ERROR", "Failed reading process error: " + ioe.getMessage()));
                    }
                });
                outThread.start();
                errThread.start();
                process.waitFor();
                outThread.join();
                errThread.join();
                Files.delete(scriptPath);
                return null;
            }

            @Override
            protected void succeeded() {
                if ("search".equals(mode)) {
                    // --- THIS IS THE CORRECTED LOGIC (Part 1) ---
                    // Group ALL results by the initial search term first.
                    Map<String, List<SearchResult>> resultsByTerm = results.stream().collect(Collectors.groupingBy(SearchResult::searchTerm));

                    // Iterate through each search term (e.g., each serial number you searched for).
                    for (Map.Entry<String, List<SearchResult>> entry : resultsByTerm.entrySet()) {
                        String searchTerm = entry.getKey();

                        // Now, group the results FOR THIS TERM by their source (AD or SCCM).
                        Map<String, List<SearchResult>> resultsBySource = entry.getValue().stream().collect(Collectors.groupingBy(SearchResult::source));

                        // Check each source (AD, SCCM) individually.
                        for (Map.Entry<String, List<SearchResult>> sourceEntry : resultsBySource.entrySet()) {
                            // The threshold for showing the pop-up.
                            if (sourceEntry.getValue().size() >= 2) {
                                // Pass ONLY the list for this specific source.
                                showHighVolumeAlert(searchTerm, sourceEntry.getKey(), sourceEntry.getValue());
                            }
                        }
                    }
                    // --- END OF CORRECTION ---
                }
                resetUiState("Operation complete.");
                progressBarMain.setProgress(1.0);
            }

            @Override
            protected void failed() {
                resetUiState("Operation failed. See log for details.");
                writeLog("FATAL", getException().getMessage());
                progressBarMain.setProgress(0);
            }

            private void resetUiState(String message) {
                Platform.runLater(() -> {
                    lblStatus.setText(message);
                    btnSearch.setDisable(false);
                    btnRemove.setDisable(false);
                });
            }
        };
        new Thread(task).start();
    }

    // --- THIS IS THE CORRECTED LOGIC (Part 2) ---
    private void showHighVolumeAlert(String searchTerm, String source, List<SearchResult> sourceSpecificResults) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/HighVolumeResultsDialog.fxml"));
            Parent root = loader.load();
            HighVolumeResultsController controller = loader.getController();

            Consumer<List<String>> removeCallback = computerNamesToRemove -> {
                results.removeIf(searchResult -> computerNamesToRemove.contains(searchResult.computerName()));
                writeLog("INFO", "Removed " + computerNamesToRemove.size() + " item(s) from the results list via pop-up.");
            };

            // Now we pass the CORRECT, pre-filtered list and its size to the controller.
            controller.initData(searchTerm, source, sourceSpecificResults.size(), sourceSpecificResults, removeCallback);

            // Use the new method to create a modal but non-always-on-top stage.
            Stage stage = StageManager.createCascadingModalStage(btnSearch.getScene().getWindow(), "High Volume of Results", root);
            stage.sizeToScene();

            // The cascading logic remains the same.
            Window owner = btnSearch.getScene().getWindow();
            double centerX = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2;
            double centerY = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2;
            double offsetX = highVolumeAlertOffset * 30.0;
            double offsetY = highVolumeAlertOffset * 30.0;
            stage.setX(centerX + offsetX);
            stage.setY(centerY + offsetY);
            highVolumeAlertOffset++;

            // Use showAndWait() for modal behavior. This is the key to stopping the flicker.
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            writeLog("ERROR", "Failed to open high volume results pop-up.");
        }
    }
    // --- END OF CORRECTION ---

    // --- NO OTHER METHODS IN THIS FILE NEED TO BE CHANGED ---
    private void copyToClipboard(String text) {
        final ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        writeLog("INFO", "Copied '" + text + "' to clipboard.");
    }

    private void processOutput(String line, String mode, int totalTargets) {
        if (line.startsWith("LOG:")) {
            String[] parts = line.split(":", 3);
            if (parts.length > 2) {
                String logType = parts[1];
                String message = parts[2];
                if ("DONE_TERM".equals(logType)) {
                    if ("search".equals(mode)) {
                        progressCounter++;
                        progressBarMain.setProgress((double) progressCounter / totalTargets);
                    }
                } else {
                    writeLog(logType, message);
                }
            }
        } else if (line.startsWith("RESULT:")) {
            String data = line.substring(7);
            String[] parts = data.split(",", 4);
            if (parts.length == 4) {
                SearchResult newResult = new SearchResult(parts[0], parts[1], parts[2], parts[3]);
                if ("OK".equalsIgnoreCase(newResult.status())) {
                    results.add(newResult);
                }
            }
        }
    }

    private List<String> getSearchTerms() {
        return txtSerials.getText().lines().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private String getSearchSource() {
        if (rdoAD.isSelected()) return "AD";
        if (rdoSCCM.isSelected()) return "SCCM";
        if (rdoBoth.isSelected()) return "Both";
        return "Both";
    }

    private void writeLog(String type, String message) {
        txtLog.appendText(String.format("[%s] %s\n", type, message));
        txtLog.setScrollTop(Double.MAX_VALUE);
    }

    @FXML
    private void handleClearLog() {
        txtLog.clear();
    }

    @FXML
    private void handleSelectAll() {
        lstResults.getSelectionModel().selectAll();
    }

    @FXML
    private void handleSelectNone() {
        lstResults.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRemove() {
        List<String> selectedComputers = lstResults.getSelectionModel().getSelectedItems().stream().map(SearchResult::computerName).filter(name -> name != null && !name.startsWith("[")).collect(Collectors.toList());

        if (selectedComputers.isEmpty()) {
            writeLog("WARN", "No valid computers selected to remove.");
            return;
        }

        boolean confirmed = StageManager.showConfirmationDialog(btnRemove.getScene().getWindow(), "Confirm Batch Removal", "Are you sure you want to permanently remove the " + selectedComputers.size() + " selected computer(s)?", "This action will attempt to delete the computer objects from Active Directory and/or SCCM and cannot be undone.");

        if (confirmed) {
            executePowerShellTask("remove", selectedComputers, null, "Removing " + selectedComputers.size() + " computer(s)...");
        } else {
            writeLog("INFO", "User cancelled the removal operation.");
        }
    }

    public record SearchResult(String source, String searchTerm, String computerName, String status) {
        public String getDisplayName() {
            if ("OK".equalsIgnoreCase(status)) {
                return String.format("[%s] %s (Found for: %s)", source, computerName, searchTerm);
            } else {
                return String.format("%s (Searched for: %s)", status, searchTerm);
            }
        }

        public String getStyleClass() {
            if ("OK".equalsIgnoreCase(status)) {
                return "AD".equalsIgnoreCase(source) ? "text-default" : "text-info";
            } else {
                return "text-danger";
            }
        }
    }
}