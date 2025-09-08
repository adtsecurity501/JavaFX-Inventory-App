package assettracking.data;

/**
 * Represents a single rule from the Mel_Rules table.
 */
public record MelRule(String modelNumber, String action, String notes) {
}