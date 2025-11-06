package assettracking.data;

import javafx.beans.property.SimpleStringProperty;

// A simple data class to hold the parsed results from one email.
public class ImagingResult {
    private final SimpleStringProperty computerName;
    private final SimpleStringProperty serialNumber;
    private final SimpleStringProperty reimageTime;
    private final SimpleStringProperty failedInstalls;

    public ImagingResult(String computerName, String serialNumber, String reimageTime, String failedInstalls) {
        this.computerName = new SimpleStringProperty(computerName);
        this.serialNumber = new SimpleStringProperty(serialNumber);
        this.reimageTime = new SimpleStringProperty(reimageTime);
        this.failedInstalls = new SimpleStringProperty(failedInstalls);
    }

    // Standard getters for JavaFX PropertyValueFactory
    public String getComputerName() {
        return computerName.get();
    }

    public String getSerialNumber() {
        return serialNumber.get();
    }

    public String getReimageTime() {
        return reimageTime.get();
    }

    public String getFailedInstalls() {
        return failedInstalls.get();
    }
}