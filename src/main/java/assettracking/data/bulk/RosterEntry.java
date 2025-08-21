package assettracking.data.bulk;

// Represents a user from the "Sales Readiness Class Roster" file.
public class RosterEntry {
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String snReferenceNumber;
    private final String depotOrderNumber;

    public RosterEntry(String firstName, String lastName, String email, String snReferenceNumber, String depotOrderNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.snReferenceNumber = snReferenceNumber;
        this.depotOrderNumber = depotOrderNumber;
    }

    // Getters
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getSnReferenceNumber() { return snReferenceNumber; }
    public String getDepotOrderNumber() { return depotOrderNumber; }

    @Override
    public String toString() {
        return firstName + " " + lastName + " (" + snReferenceNumber + ")";
    }
}