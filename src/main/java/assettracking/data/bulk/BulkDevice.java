package assettracking.data.bulk;

import javafx.beans.property.SimpleStringProperty;

// Represents a device from the "Device Information Full" file and the Bulk_Devices table.
public class BulkDevice {
    private final SimpleStringProperty serialNumber;
    private final SimpleStringProperty imei;
    private final SimpleStringProperty iccid; // SIM card
    private final SimpleStringProperty capacity;
    private final SimpleStringProperty deviceName;
    private final SimpleStringProperty lastImportDate;

    public BulkDevice(String serialNumber, String imei, String iccid, String capacity, String deviceName, String lastImportDate) {
        this.serialNumber = new SimpleStringProperty(serialNumber);
        this.imei = new SimpleStringProperty(imei);
        this.iccid = new SimpleStringProperty(iccid);
        this.capacity = new SimpleStringProperty(capacity);
        this.deviceName = new SimpleStringProperty(deviceName);
        this.lastImportDate = new SimpleStringProperty(lastImportDate);
    }

    // Getters
    public String getSerialNumber() { return serialNumber.get(); }
    public String getImei() { return imei.get(); }
    public String getIccid() { return iccid.get(); }
    public String getCapacity() { return capacity.get(); }
    public String getDeviceName() { return deviceName.get(); }
    public String getLastImportDate() { return lastImportDate.get(); }

    // Setters
    public void setIccid(String iccid) { this.iccid.set(iccid); }

    // JavaFX Property Getters
    public SimpleStringProperty serialNumberProperty() { return serialNumber; }
    public SimpleStringProperty imeiProperty() { return imei; }
    public SimpleStringProperty iccidProperty() { return iccid; }
}