package assettracking.data;

/**
 * A record to hold the summary view for a Box ID (Box ID and item count).
 */
public record BoxIdSummary(String boxId, int itemCount) {
}