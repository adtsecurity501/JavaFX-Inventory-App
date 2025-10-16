package assettracking.manager;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A dedicated background task for handling the automated folder import process.
 * This class extends Task, allowing it to safely update its own progress and message.
 */
public class FolderImportTask extends Task<List<ImportResult>> {

    private static final Logger logger = LoggerFactory.getLogger(FolderImportTask.class);
    private final List<String> folderPathsToScan;
    private final DeviceImportService deviceImportService;

    public FolderImportTask(List<String> folderPathsToScan, DeviceImportService deviceImportService) {
        this.folderPathsToScan = folderPathsToScan;
        this.deviceImportService = deviceImportService;
    }

    @Override
    protected List<ImportResult> call() throws Exception {
        updateMessage("Scanning for device files...");
        List<File> allFiles = deviceImportService.findAllDeviceFiles(folderPathsToScan);

        if (allFiles.isEmpty()) {
            updateMessage("No new device files found to process.");
            return Collections.emptyList();
        }

        List<ImportResult> results = new ArrayList<>();
        int totalFiles = allFiles.size();

        for (int i = 0; i < totalFiles; i++) {
            File file = allFiles.get(i);
            updateProgress(i + 1, totalFiles);
            updateMessage(String.format("Processing file %d/%d: %s", i + 1, totalFiles, file.getName()));

            // --- THIS IS THE FIX ---
            // Wrap the processing of each file in a try-catch block.
            try {
                results.add(deviceImportService.processAndUpsertData(file));
            } catch (Exception e) {
                // If a single file fails (e.g., it's locked), log the error and continue.
                logger.error("Critical error processing file: {}", file.getName(), e);
                // Add a result indicating failure for this specific file.
                results.add(new ImportResult(file, 0, List.of("Critical error processing file: " + e.getMessage())));
            }
            // --- END OF FIX ---
        }
        updateMessage("Import process complete.");
        return results;
    }
}