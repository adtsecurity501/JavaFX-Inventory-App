package assettracking.data.bulk;

import javafx.beans.property.SimpleStringProperty;

// Represents the final combined data ready for staging and export.
public class StagedDevice {
    private final SimpleStringProperty firstName;
    private final SimpleStringProperty lastName;
    private final SimpleStringProperty serialNumber;
    private final SimpleStringProperty imei;
    private final SimpleStringProperty sim;
    private final SimpleStringProperty snReferenceNumber;
    private final SimpleStringProperty employeeEmail;
    private final SimpleStringProperty depotOrderNumber;

    public StagedDevice(RosterEntry rosterEntry, BulkDevice bulkDevice) {
        this.firstName = new SimpleStringProperty(rosterEntry.getFirstName());
        this.lastName = new SimpleStringProperty(rosterEntry.getLastName());
        this.serialNumber = new SimpleStringProperty(bulkDevice.getSerialNumber());
        this.imei = new SimpleStringProperty(bulkDevice.getImei());
        this.sim = new SimpleStringProperty(bulkDevice.getIccid());
        this.snReferenceNumber = new SimpleStringProperty(rosterEntry.getSnReferenceNumber());
        this.employeeEmail = new SimpleStringProperty(rosterEntry.getEmail());
        this.depotOrderNumber = new SimpleStringProperty(rosterEntry.getDepotOrderNumber());
    }

    // --- NEW ---
    // Add this new constructor to handle devices without a roster entry.
    public StagedDevice(BulkDevice bulkDevice) {
        this.firstName = new SimpleStringProperty(""); // Blank field for manual entry later
        this.lastName = new SimpleStringProperty("");  // Blank field
        this.serialNumber = new SimpleStringProperty(bulkDevice.getSerialNumber());
        this.imei = new SimpleStringProperty(bulkDevice.getImei());
        this.sim = new SimpleStringProperty(bulkDevice.getIccid());
        this.snReferenceNumber = new SimpleStringProperty(""); // Blank field
        this.employeeEmail = new SimpleStringProperty("");     // Blank field
        this.depotOrderNumber = new SimpleStringProperty("");  // Blank field
    }
    // --- END NEW ---

    // Getters for TableView columns
    public String getFirstName() { return firstName.get(); }
    public String getLastName() { return lastName.get(); }
    public String getSerialNumber() { return serialNumber.get(); }
    public String getImei() { return imei.get(); }
    public String getSim() { return sim.get(); }
    public String getSnReferenceNumber() { return snReferenceNumber.get(); }
    public String getEmployeeEmail() { return employeeEmail.get(); }
    public String getDepotOrderNumber() { return depotOrderNumber.get(); }

    // Property Getters for JavaFX
    public SimpleStringProperty firstNameProperty() { return firstName; }
    public SimpleStringProperty lastNameProperty() { return lastName; }
    public SimpleStringProperty serialNumberProperty() { return serialNumber; }
    public SimpleStringProperty imeiProperty() { return imei; }
    public SimpleStringProperty simProperty() { return sim; }
    public SimpleStringProperty snReferenceNumberProperty() { return snReferenceNumber; }
}