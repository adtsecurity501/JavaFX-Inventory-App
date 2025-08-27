package assettracking.manager;

import java.util.*;

public final class StatusManager {

    // Using LinkedHashMap to maintain insertion order for UI consistency.
    private static final Map<String, List<String>> STATUS_MAP = new LinkedHashMap<>();

    // Static initializer block to populate the map once when the class is loaded.
    static {
        // 1. New default status for used/returned devices
        STATUS_MAP.put("Intake", List.of(
                "In Evaluation"
        ));

        // 2. New category for all "hands-on" work
        STATUS_MAP.put("Triage & Repair", List.of(
                "Troubleshooting",
                "Refurbishment",
                "Awaiting Parts",
                "Awaiting Dell Tech",
                "Shipped to Dell"
        ));

        // 3. Status for devices ready for use or storage
        STATUS_MAP.put("Processed", List.of(
                "Ready for Deployment",
                "Ready for Imaging",
                "Kept in Depot(Parts)",
                "Kept in Depot(Functioning)"
        ));

        // 4. Final disposition statuses (items leaving inventory)
        STATUS_MAP.put("Disposed", List.of(
                "Can-Am, Pending Pickup",
                "Can-Am, Picked Up",
                "Ingram, Pending Pickup",
                "Ingram, Picked Up",
                "Ready for Wipe"
        ));

        // 5. & 6. Shipping-related statuses
        STATUS_MAP.put("Everon", List.of(
                "Pending Shipment",
                "Shipped"
        ));
        STATUS_MAP.put("Phone", List.of(
                "Pending Shipment",
                "Shipped"
        ));

        // 7. System status for flagged items
        STATUS_MAP.put("Flag!", List.of(
                "Requires Review"
        ));
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