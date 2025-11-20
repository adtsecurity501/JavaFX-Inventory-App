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
    private static final Pattern EMAIL_BLOCK_PATTERN = Pattern.compile("PARSED_EMAIL:(.+)(?=\\n|$)");

    public CompletableFuture<String> testOutlookConnection(String folderName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executePython(List.of(folderName, "--test_connection"));
            } catch (Exception e) {
                return "FATAL: Could not execute script. " + e.getMessage();
            }
        });
    }

    public CompletableFuture<List<ImagingResult>> fetchAndParseEmails(List<String> commandArgs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String fullOutput = executePython(commandArgs);
                List<ImagingResult> results = new ArrayList<>();
                Matcher matcher = EMAIL_BLOCK_PATTERN.matcher(fullOutput);
                while (matcher.find()) {
                    String line = matcher.group(1).trim();
                    String[] parts = line.split("_\\|\\|_");

                    if (parts.length < 4) {
                        logger.warn("Skipping malformed PARSED_EMAIL line (expected >= 4 parts): {}", line);
                        continue;
                    }

                    String computerName = parts[0];
                    String serialNumber = parts[1];
                    String reimageTime = parts[2];
                    String failedInstalls = parts[3];
                    String receivedTime = parts.length >= 5 ? parts[4] : "";

                    results.add(new ImagingResult(computerName, serialNumber, reimageTime, failedInstalls, receivedTime));
                }
                logger.info("Imaging helper finished. Parsed {} results from output.", results.size());
                return results;
            } catch (Exception e) {
                logger.error("Failed to fetch and parse emails", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Run the imaging helper:
     * - Prefer get_imaging_emails.exe extracted from resources (no Python needed on target machines)
     * - Fall back to Python + get_imaging_emails.py on dev machines if EXE is missing
     */
    private String executePython(List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        Path helperPath;

        try {
            // 1) Prefer bundled EXE from resources – no Python needed on target machines
            helperPath = extractExeToTemp("get_imaging_emails.exe");
            logger.info("Using bundled get_imaging_emails.exe from resources.");
            command.add(helperPath.toAbsolutePath().toString());
        } catch (IOException ex) {
            // 2) Fallback: use Python + script (useful on dev machines)
            logger.warn("Could not load get_imaging_emails.exe, falling back to Python script.", ex);

            helperPath = extractScriptToTemp("get_imaging_emails.py");

            String pythonExecutable = "python/python.exe";
            if (new java.io.File(pythonExecutable).exists()) {
                command.add(pythonExecutable);
            } else {
                command.add("python.exe");
                logger.warn("Bundled Python not found. Falling back to system PATH.");
            }

            command.add(helperPath.toAbsolutePath().toString());
        }

        // Add remaining args
        command.addAll(args);

        List<String> finalCommand = command.stream().filter(arg -> arg != null && !arg.isBlank()).collect(Collectors.toList());

        logger.info("Executing Imaging helper command: {}", String.join(" ", finalCommand));

        ProcessBuilder pb = new ProcessBuilder(finalCommand);
        Process process = pb.start();

        String fullOutput = new BufferedReader(new InputStreamReader(process.getInputStream())).lines().collect(Collectors.joining("\n"));
        String fullError = new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().collect(Collectors.joining("\n"));

        int exitCode = process.waitFor();

        try {
            Files.deleteIfExists(helperPath);
        } catch (IOException e) {
            logger.warn("Failed to delete temp imaging helper {}", helperPath, e);
        }

        logger.debug("Imaging helper STDOUT:\n{}", fullOutput);
        if (!fullError.isEmpty()) {
            logger.error("Imaging helper STDERR (exit code {}):\n{}", exitCode, fullError);
        }

        // If there was any stderr, return that so the UI can show it; otherwise return stdout
        return !fullError.isEmpty() ? fullError : fullOutput;
    }

    private Path extractExeToTemp(String exeName) throws IOException {
        Path exePath = Files.createTempFile("get_imaging_emails_", ".exe");
        exePath.toFile().deleteOnExit();
        try (InputStream is = getClass().getResourceAsStream("/" + exeName)) {
            if (is == null) {
                throw new IOException("Could not find " + exeName + " in application resources.");
            }
            Files.copy(is, exePath, StandardCopyOption.REPLACE_EXISTING);
        }
        return exePath;
    }

    private Path extractScriptToTemp(String scriptName) throws IOException {
        Path scriptPath = Files.createTempFile("get_imaging_emails_", ".py");
        scriptPath.toFile().deleteOnExit();
        try (InputStream is = getClass().getResourceAsStream("/" + scriptName)) {
            if (is == null) {
                throw new IOException("Could not find " + scriptName + " in application resources.");
            }
            Files.copy(is, scriptPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return scriptPath;
    }
}
