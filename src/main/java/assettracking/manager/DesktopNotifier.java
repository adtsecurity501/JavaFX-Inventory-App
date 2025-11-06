package assettracking.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class DesktopNotifier {

    private static final Logger logger = LoggerFactory.getLogger(DesktopNotifier.class);

    private DesktopNotifier() {
    }

    public static void showNotification(String title, String message) {
        CompletableFuture.runAsync(() -> {
            logger.info("Attempting to show desktop notification: Title='{}', Message='{}'", title, message);
            try {
                Path scriptPath = extractScriptToTemp("Show-Toast.ps1");
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptPath.toAbsolutePath().toString(), "-Title", title, "-Message", message);

                Process process = pb.start();

                // Capture any output or errors from the script for diagnosis
                String output = new BufferedReader(new InputStreamReader(process.getInputStream())).lines().collect(Collectors.joining("\n"));

                String error = new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().collect(Collectors.joining("\n"));

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    logger.info("Successfully executed notification script.");
                    if (!output.isEmpty()) {
                        logger.debug("PowerShell script output: {}", output);
                    }
                } else {
                    logger.error("Notification script failed with exit code: {}", exitCode);
                    if (!output.isEmpty()) {
                        logger.error("PowerShell Standard Output:\n{}", output);
                    }
                    if (!error.isEmpty()) {
                        logger.error("PowerShell Error Output:\n{}", error);
                    }
                }

            } catch (IOException | InterruptedException e) {
                logger.error("Failed to execute PowerShell notification script.", e);
                Thread.currentThread().interrupt();
            }
        });
    }

    private static Path extractScriptToTemp(String scriptName) throws IOException {
        Path scriptPath = Files.createTempFile("toast_script_", ".ps1");
        scriptPath.toFile().deleteOnExit();
        try (InputStream is = DesktopNotifier.class.getResourceAsStream("/" + scriptName)) {
            if (is == null) {
                throw new IOException("Could not find script in resources: " + scriptName);
            }
            Files.copy(is, scriptPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return scriptPath;
    }
}