package assettracking.data;

/**
 * A record to hold the complete data for a flagged device,
 * including the serial number, reason, and whether to prevent auto-removal.
 */
public record FlaggedDeviceData(String serialNumber, String reason, boolean preventRemoval) {
}