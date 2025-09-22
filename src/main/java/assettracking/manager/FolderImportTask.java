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

            try {
                results.add(deviceImportService.processAndUpsertData(file));
            } catch (Exception e) {
                logger.error("Critical error processing file: {}", file.getName(), e);
                results.add(new ImportResult(file, 0, List.of("Critical error processing file: " + e.getMessage())));
            }
        }
        updateMessage("Import process complete.");
        return results;
    }
}