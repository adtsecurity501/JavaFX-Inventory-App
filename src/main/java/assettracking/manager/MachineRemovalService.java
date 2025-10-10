package assettracking.manager;

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
import java.util.stream.Collectors;

/**
 * A service to interact with the FindAndRemove.ps1 PowerShell script.
 * This service is decoupled from the UI and can be used anywhere in the application.
 */
public class MachineRemovalService {

    /**
     * A record to hold structured data parsed from the PowerShell script's output.
     */
    public record SearchResult(String source, String searchTerm, String computerName, String status) {}

    /**
     * Asynchronously searches for computers in AD and/or SCCM.
     * @param searchTerms A list of serial numbers or computer names to search for.
     * @return A CompletableFuture that will complete with a list of SearchResult objects.
     */
    public CompletableFuture<List<SearchResult>> search(List<String> searchTerms) {
        return CompletableFuture.supplyAsync(() ->
                executePowerShell("search", searchTerms, "Both").stream()
                        .filter(line -> line.startsWith("RESULT:"))
                        .map(this::parseSearchResult)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Asynchronously removes computers from AD and SCCM.
     * @param computerNames The exact computer names to remove.
     * @return A CompletableFuture that will complete with a list of log messages from the script.
     */
    public CompletableFuture<List<String>> remove(List<String> computerNames) {
        return CompletableFuture.supplyAsync(() ->
                executePowerShell("remove", computerNames, null).stream()
                        .filter(line -> line.startsWith("LOG:"))
                        .map(line -> line.substring(4)) // Remove "LOG:" prefix
                        .collect(Collectors.toList())
        );
    }

    private SearchResult parseSearchResult(String line) {
        // Line format: RESULT:Source,SearchTerm,ComputerName,Status
        String[] parts = line.substring(7).split(",", 4);
        if (parts.length == 4) {
            return new SearchResult(parts[0], parts[1], parts[2], parts[3]);
        }
        return new SearchResult("PARSE_ERROR", line, "", "Error");
    }

    private List<String> executePowerShell(String mode, List<String> targets, String source) {
        List<String> output = new ArrayList<>();
        try {
            Path scriptPath = extractScriptToTemp();
            String targetString = String.join(",", targets);

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptPath.toAbsolutePath().toString(), "-" + mode, "-targets", targetString);
            if ("search".equals(mode)) {
                pb.command().add("-source");
                pb.command().add(source);
            }

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }
            process.waitFor();
            Files.delete(scriptPath);

        } catch (IOException | InterruptedException e) {
            output.add("LOG:FATAL:Java Exception: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        return output;
    }

    private Path extractScriptToTemp() throws IOException {
        Path scriptPath = Files.createTempFile("ps_script_", ".ps1");
        try (InputStream is = getClass().getResourceAsStream("/FindAndRemove.ps1")) {
            if (is == null) {
                throw new IOException("Could not find FindAndRemove.ps1 in resources.");
            }
            Files.copy(is, scriptPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return scriptPath;
    }
}