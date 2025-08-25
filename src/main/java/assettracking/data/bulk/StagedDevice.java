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
    // --- NEW FIELDS ---
    private final SimpleStringProperty carrier;
    private final SimpleStringProperty carrierAccountNumber;

    public StagedDevice(RosterEntry rosterEntry, BulkDevice bulkDevice) {
        this.firstName = new SimpleStringProperty(rosterEntry.getFirstName());
        this.lastName = new SimpleStringProperty(rosterEntry.getLastName());
        this.serialNumber = new SimpleStringProperty(bulkDevice.getSerialNumber());
        this.imei = new SimpleStringProperty(bulkDevice.getImei());
        this.sim = new SimpleStringProperty(bulkDevice.getIccid());
        this.snReferenceNumber = new SimpleStringProperty(rosterEntry.getSnReferenceNumber());
        this.employeeEmail = new SimpleStringProperty(rosterEntry.getEmail());
        this.depotOrderNumber = new SimpleStringProperty(rosterEntry.getDepotOrderNumber());
        // --- INITIALIZE NEW FIELDS (Default to Verizon) ---
        this.carrier = new SimpleStringProperty("Verizon");
        this.carrierAccountNumber = new SimpleStringProperty("VER-942x");
    }

    public StagedDevice(BulkDevice bulkDevice) {
        this.firstName = new SimpleStringProperty("");
        this.lastName = new SimpleStringProperty("");
        this.serialNumber = new SimpleStringProperty(bulkDevice.getSerialNumber());
        this.imei = new SimpleStringProperty(bulkDevice.getImei());
        this.sim = new SimpleStringProperty(bulkDevice.getIccid());
        this.snReferenceNumber = new SimpleStringProperty("");
        this.employeeEmail = new SimpleStringProperty("");
        this.depotOrderNumber = new SimpleStringProperty("");
        // --- INITIALIZE NEW FIELDS (Default to Verizon for unassigned too) ---
        this.carrier = new SimpleStringProperty("Verizon");
        this.carrierAccountNumber = new SimpleStringProperty("VER-942x");
    }

    // --- SETTERS FOR NEW AND EXISTING FIELDS ---
    public void setSim(String newSim) { this.sim.set(newSim); }
    public void setCarrier(String newCarrier) { this.carrier.set(newCarrier); }
    public void setCarrierAccountNumber(String newAccountNumber) { this.carrierAccountNumber.set(newAccountNumber); }


    // Getters for TableView columns
    public String getFirstName() { return firstName.get(); }
    public String getLastName() { return lastName.get(); }
    public String getSerialNumber() { return serialNumber.get(); }
    public String getImei() { return imei.get(); }
    public String getSim() { return sim.get(); }
    public String getSnReferenceNumber() { return snReferenceNumber.get(); }
    public String getEmployeeEmail() { return employeeEmail.get(); }
    public String getDepotOrderNumber() { return depotOrderNumber.get(); }
    public String getCarrier() { return carrier.get(); } // <-- NEW
    public String getCarrierAccountNumber() { return carrierAccountNumber.get(); } // <-- NEW

    // Property Getters for JavaFX
    public SimpleStringProperty firstNameProperty() { return firstName; }
    public SimpleStringProperty lastNameProperty() { return lastName; }
    public SimpleStringProperty serialNumberProperty() { return serialNumber; }
    public SimpleStringProperty imeiProperty() { return imei; }
    public SimpleStringProperty simProperty() { return sim; }
    public SimpleStringProperty snReferenceNumberProperty() { return snReferenceNumber; }
    public SimpleStringProperty carrierProperty() { return carrier; } // <-- NEW
    public SimpleStringProperty carrierAccountNumberProperty() { return carrierAccountNumber; } // <-- NEW
}