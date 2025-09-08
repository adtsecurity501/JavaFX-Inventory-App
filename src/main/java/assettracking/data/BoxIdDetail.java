package assettracking.data;

/**
 * A record to hold the detailed view for a Box ID (the serial numbers inside).
 */
public record BoxIdDetail(String serialNumber, String status, String subStatus) {
}