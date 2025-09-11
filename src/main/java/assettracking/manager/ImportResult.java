package assettracking.manager;

import java.io.File;
import java.util.List;

public record ImportResult(File file, int successfulCount, List<String> errors) {
}