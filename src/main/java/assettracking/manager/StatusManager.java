package assettracking.manager;

import java.util.*;

public final class StatusManager {

    // Using LinkedHashMap to maintain insertion order for UI consistency.
    private static final Map<String, List<String>> STATUS_MAP = new LinkedHashMap<>();

    // Static initializer block to populate the map once when the class is loaded.
    static {
        // --- THIS BLOCK HAS BEEN UPDATED WITH THE NEW LISTS ---
        STATUS_MAP.put("WIP", List.of(
                "In Evaluation",
                "Troubleshooting",
                "Awaiting Parts",
                "Awaiting Dell Tech",
                "Shipped to Dell",
                "Refurbishment"
        ));
        STATUS_MAP.put("Processed", List.of(
                "Ready for Deployment",
                "Ready for Imaging"
        ));
        STATUS_MAP.put("Inventory", List.of(
                "Surplus",
                "Parts Harvest",
                "Awaiting Disposition"
        ));
        STATUS_MAP.put("Disposal", List.of(
                "e-Waste",
                "Return to Vendor",
                "Donation"
        ));
        STATUS_MAP.put("Everon", List.of(
                "Pending Shipment",
                "Shipped"
        ));
        STATUS_MAP.put("Special Projects", List.of(
                "Project Intake",
                "Pending Deployment",
                "Deployed"
        ));
        STATUS_MAP.put("Flag!", List.of( // This is kept for system functionality
                "Requires Review"
        ));
        // --- END OF UPDATE ---
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private StatusManager() {}

    /**
     * Gets an unmodifiable list of the main status categories.
     * @return A List of status strings.
     */
    public static List<String> getStatuses() {
        return new ArrayList<>(STATUS_MAP.keySet());
    }

    /**
     * Gets an unmodifiable list of sub-statuses for a given main status.
     * @param status The main status category.
     * @return A List of sub-status strings, or an empty list if the status is not found.
     */
    public static List<String> getSubStatuses(String status) {
        return STATUS_MAP.getOrDefault(status, Collections.emptyList());
    }
}