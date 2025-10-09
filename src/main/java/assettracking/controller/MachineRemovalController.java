package assettracking.controller;

import assettracking.manager.StageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

public class MachineRemovalController {

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

        // --- UPDATED CELL FACTORY WITH COPY FUNCTIONALITY ---
        lstResults.setCellFactory(lv -> {
            ListCell<SearchResult> cell = new ListCell<>() {
                @Override
                protected void updateItem(SearchResult item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setOnMouseClicked(null); // Clear event handlers
                        setContextMenu(null);   // Clear context menu
                    } else {
                        setText(item.getDisplayName());
                        setTextFill(item.getColor());

                        // Add Double-Click to Copy
                        setOnMouseClicked(event -> {
                            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                                if (item.getComputerName() != null && !item.getComputerName().startsWith("[")) {
                                    copyToClipboard(item.getComputerName());
                                }
                            }
                        });

                        // Add Right-Click Context Menu
                        ContextMenu contextMenu = new ContextMenu();
                        MenuItem copyItem = new MenuItem("Copy Computer Name");
                        copyItem.setOnAction(event -> {
                            if (item.getComputerName() != null && !item.getComputerName().startsWith("[")) {
                                copyToClipboard(item.getComputerName());
                            }
                        });
                        // Disable the copy option for error messages
                        copyItem.setDisable(item.getComputerName() == null || item.getComputerName().startsWith("["));
                        contextMenu.getItems().add(copyItem);
                        setContextMenu(contextMenu);
                    }
                }
            };
            return cell;
        });
    }

    // --- NEW HELPER METHOD FOR COPYING ---
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

        String source = getSearchSource();
        progressCounter = 0; // Reset counter for new search
        executePowerShellTask("search", searchTerms, source, "Searching for " + searchTerms.size() + " item(s)...");
    }

    @FXML
    private void handleRemove() {
        List<String> selectedComputers = lstResults.getSelectionModel().getSelectedItems().stream().map(SearchResult::getComputerName).filter(name -> name != null && !name.startsWith("[")) // Filter out errors/not found
                .collect(Collectors.toList());

        if (selectedComputers.isEmpty()) {
            writeLog("WARN", "No valid computers selected to remove.");
            return;
        }

        boolean confirmed = StageManager.showConfirmationDialog(btnRemove.getScene().getWindow(), "Confirm Batch Removal", "Are you sure you want to permanently remove the " + selectedComputers.size() + " selected computer(s)?", "This action cannot be undone.");

        if (confirmed) {
            executePowerShellTask("remove", selectedComputers, null, "Removing " + selectedComputers.size() + " computer(s)...");
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
                        progressBarMain.setProgress(-1.0); // Indeterminate for removal
                    }
                });

                // Extract the script from JAR to a temp file
                Path scriptPath = Files.createTempFile("ps_script_", ".ps1");
                try (InputStream is = getClass().getResourceAsStream("/FindAndRemove.ps1")) {
                    if (is == null) {
                        throw new IOException("Could not find FindAndRemove.ps1 in resources.");
                    }
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

                int exitCode = process.waitFor();
                outThread.join();
                errThread.join();

                Files.delete(scriptPath);

                if (exitCode != 0) {
                    Platform.runLater(() -> writeLog("ERROR", "PowerShell script exited with code: " + exitCode));
                }
                return null;
            }

            @Override
            protected void succeeded() {
                resetUiState("Operation complete.");
                progressBarMain.setProgress(1.0); // Mark as complete
            }

            @Override
            protected void failed() {
                resetUiState("Operation failed. See log for details.");
                writeLog("FATAL", getException().getMessage());
                getException().printStackTrace();
                progressBarMain.setProgress(0); // Reset on failure
            }

            private void resetUiState(String message) {
                Platform.runLater(() -> {
                    lblStatus.setText(message);
                    btnSearch.setDisable(false);
                    btnRemove.setDisable(false);
                });
            }
        };

        // We no longer bind the progress bar. It will only be updated manually.
        new Thread(task).start();
    }

    private void processOutput(String line, String mode, int totalTargets) {
        if (line.startsWith("LOG:")) {
            String[] parts = line.split(":", 3);
            if (parts.length > 2) {
                writeLog(parts[1], parts[2]);
            }
        } else if (line.startsWith("RESULT:")) {
            String data = line.substring(7);
            String[] parts = data.split(",", 4);
            if (parts.length == 4) {
                results.add(new SearchResult(parts[0], parts[1], parts[2], parts[3]));
                if ("search".equals(mode)) {
                    progressCounter++;
                    progressBarMain.setProgress((double) progressCounter / totalTargets);
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
        return "Both"; // Default
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

    public static class SearchResult {
        private final String source;
        private final String searchTerm;
        private final String computerName;
        private final String status;

        public SearchResult(String source, String searchTerm, String computerName, String status) {
            this.source = source;
            this.searchTerm = searchTerm;
            this.computerName = computerName;
            this.status = status;
        }

        public String getComputerName() {
            return computerName;
        }

        public String getDisplayName() {
            if ("OK".equalsIgnoreCase(status)) {
                return String.format("[%s] %s (Found for: %s)", source, computerName, searchTerm);
            } else {
                return String.format("%s (Searched for: %s)", status, searchTerm);
            }
        }

        public Color getColor() {
            if ("OK".equalsIgnoreCase(status)) {
                return "AD".equalsIgnoreCase(source) ? Color.BLACK : Color.DARKBLUE;
            } else {
                return Color.RED;
            }
        }
    }
}