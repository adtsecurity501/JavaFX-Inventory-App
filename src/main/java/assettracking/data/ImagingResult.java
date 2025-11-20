package assettracking.data;

import javafx.beans.property.SimpleStringProperty;

/**
 * A simple data class representing a parsed imaging result row
 * from the Outlook imaging emails.
 */
public class ImagingResult {

    private final SimpleStringProperty computerName;
    private final SimpleStringProperty serialNumber;
    private final SimpleStringProperty reimageTime;
    private final SimpleStringProperty failedInstalls;
    private final SimpleStringProperty receivedTime;   // NEW

    public ImagingResult(String computerName, String serialNumber, String reimageTime, String failedInstalls, String receivedTime) {
        this.computerName = new SimpleStringProperty(computerName);
        this.serialNumber = new SimpleStringProperty(serialNumber);
        this.reimageTime = new SimpleStringProperty(reimageTime);
        this.failedInstalls = new SimpleStringProperty(failedInstalls);
        this.receivedTime = new SimpleStringProperty(receivedTime);
    }

    public String getComputerName() {
        return computerName.get();
    }

    public SimpleStringProperty computerNameProperty() {
        return computerName;
    }

    public String getSerialNumber() {
        return serialNumber.get();
    }

    public SimpleStringProperty serialNumberProperty() {
        return serialNumber;
    }

    public String getReimageTime() {
        return reimageTime.get();
    }

    public SimpleStringProperty reimageTimeProperty() {
        return reimageTime;
    }

    public String getFailedInstalls() {
        return failedInstalls.get();
    }

    public SimpleStringProperty failedInstallsProperty() {
        return failedInstalls;
    }

    public String getReceivedTime() {
        return receivedTime.get();
    }

    public SimpleStringProperty receivedTimeProperty() {
        return receivedTime;
    }
}
