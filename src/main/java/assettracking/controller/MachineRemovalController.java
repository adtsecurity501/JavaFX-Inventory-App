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

    private void copyToClipboard(String text) {
        final ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        writeLog("INFO", "Copied '" + text + "' to clipboard.");
    }

    @FXML
    private void handleSearch() {
        List<String> searchTerms = getSearchTerms();
        if (searchTerms.isEmpty()) {
            writeLog("WARN", "No search terms entered.");
            return;
        }

        // Reset the cascade offset for each new search
        highVolumeAlertOffset = 0;

        String source = getSearchSource();
        progressCounter = 0;
        executePowerShellTask("search", searchTerms, source, "Searching for " + searchTerms.size() + " item(s)...");
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

    private void executePowerShellTask(String mode, List<String> targets, String source, String statusMessage) {
        int totalTargets = targets.size();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
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
                Thread outThread = new Thread(() -> new BufferedReader(new InputStreamReader(process.getInputStream())).lines().forEach(line -> Platform.runLater(() -> processOutput(line, mode, totalTargets))));
                Thread errThread = new Thread(() -> new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(line -> Platform.runLater(() -> writeLog("ERROR", line))));
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
                    Map<String, List<SearchResult>> resultsByTerm = results.stream().collect(Collectors.groupingBy(SearchResult::searchTerm));

                    for (Map.Entry<String, List<SearchResult>> entry : resultsByTerm.entrySet()) {
                        String searchTerm = entry.getKey();
                        Map<String, List<SearchResult>> resultsBySource = entry.getValue().stream().collect(Collectors.groupingBy(SearchResult::source));

                        for (Map.Entry<String, List<SearchResult>> sourceEntry : resultsBySource.entrySet()) {
                            if (sourceEntry.getValue().size() >= 3) {
                                showHighVolumeAlert(searchTerm, sourceEntry.getKey(), sourceEntry.getValue().size(), sourceEntry.getValue());
                            }
                        }
                    }
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

    private void processOutput(String line, String mode, int totalTargets) {
        if (line.startsWith("LOG:")) {
            String[] parts = line.split(":", 3);
            if (parts.length > 2) {
                String logType = parts[1];
                String message = parts[2];

                // --- THIS IS THE NEW LOGIC FOR THE PROGRESS BAR ---
                if ("DONE_TERM".equals(logType)) {
                    if ("search".equals(mode)) {
                        progressCounter++;
                        progressBarMain.setProgress((double) progressCounter / totalTargets);
                    }
                } else {
                    writeLog(logType, message);
                }
                // --- END OF NEW LOGIC ---
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

    private void showHighVolumeAlert(String searchTerm, String source, int count, List<SearchResult> allResultsForSource) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/HighVolumeResultsDialog.fxml"));
            Parent root = loader.load();

            HighVolumeResultsController controller = loader.getController();

            Consumer<List<String>> removeCallback = computerNamesToRemove -> {
                results.removeIf(searchResult -> computerNamesToRemove.contains(searchResult.computerName()));
                writeLog("INFO", "Removed " + computerNamesToRemove.size() + " item(s) from the results list via pop-up.");
            };

            controller.initData(searchTerm, source, count, allResultsForSource, removeCallback);

            Stage stage = StageManager.createCustomStage(btnSearch.getScene().getWindow(), "High Volume of Results", root, false);
            Window owner = btnSearch.getScene().getWindow();
            stage.sizeToScene();

            double centerX = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2;
            double centerY = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2;

            double offsetX = highVolumeAlertOffset * 30.0;
            double offsetY = highVolumeAlertOffset * 30.0;

            stage.setX(centerX + offsetX);
            stage.setY(centerY + offsetY);

            highVolumeAlertOffset++;
            // --- END OF FIX ---

            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            writeLog("ERROR", "Failed to open high volume results pop-up.");
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