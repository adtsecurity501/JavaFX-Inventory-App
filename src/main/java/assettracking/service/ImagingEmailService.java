package assettracking.service;

import assettracking.data.ImagingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ImagingEmailService {

    private static final Logger logger = LoggerFactory.getLogger(ImagingEmailService.class);
    private static final Pattern EMAIL_BLOCK_PATTERN = Pattern.compile("PARSED_EMAIL:(.+?_\\|\\|_.+?_\\|\\|_.+?_\\|\\|_.+?)(?=\\n|$)");

    public CompletableFuture<String> testOutlookConnection(String folderName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executePython(List.of("--test_connection", folderName));
            } catch (Exception e) {
                return "FATAL: Could not execute script. " + e.getMessage();
            }
        });
    }

    public CompletableFuture<List<ImagingResult>> fetchAndParseEmails(List<String> command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String fullOutput = executePython(command);

                List<ImagingResult> results = new ArrayList<>();
                Matcher matcher = EMAIL_BLOCK_PATTERN.matcher(fullOutput);
                while (matcher.find()) {
                    String[] parts = matcher.group(1).split("_\\|\\|_");
                    if (parts.length == 4) {
                        results.add(new ImagingResult(parts[0], parts[1], parts[2], parts[3]));
                    }
                }

                logger.info("Python script finished. Parsed {} results.", results.size());
                return results;
            } catch (Exception e) {
                logger.error("Failed to fetch and parse emails", e);
                return new ArrayList<>();
            }
        });
    }

    private String executePython(List<String> args) throws IOException, InterruptedException {
        Path scriptPath = extractScriptToTemp("get_imaging_emails.py");

        List<String> command = new ArrayList<>();

        // --- THIS IS THE KEY CHANGE ---
        // 1. Define the path to the bundled python executable.
        String pythonExecutable = "python/python.exe";

        // 2. Check if the bundled python exists.
        if (new java.io.File(pythonExecutable).exists()) {
            // If it exists, use it.
            command.add(pythonExecutable);
            logger.info("Using bundled Python executable.");
        } else {
            // If not, fall back to the system's python.
            command.add("python.exe");
            logger.warn("Bundled Python not found at '{}'. Falling back to system PATH. The application may not be portable.", pythonExecutable);
        }
        // --- END OF KEY CHANGE ---

        command.add(scriptPath.toAbsolutePath().toString());
        command.addAll(args);

        logger.info("Executing Python script with command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        String fullOutput = new BufferedReader(new InputStreamReader(process.getInputStream())).lines().collect(Collectors.joining("\n"));
        String fullError = new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().collect(Collectors.joining("\n"));

        process.waitFor();
        Files.delete(scriptPath);

        logger.debug("Python script STDOUT:\n{}", fullOutput);
        if (!fullError.isEmpty()) {
            logger.error("Python script STDERR:\n{}", fullError);
        }

        return !fullError.isEmpty() ? fullError : fullOutput;
    }

    private Path extractScriptToTemp(String scriptName) throws IOException {
        Path scriptPath = Files.createTempFile("get_imaging_emails_", ".py");
        scriptPath.toFile().deleteOnExit();
        try (InputStream is = getClass().getResourceAsStream("/" + scriptName)) {
            if (is == null) throw new IOException("Could not find " + scriptName + " in application resources.");
            Files.copy(is, scriptPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return scriptPath;
    }
}