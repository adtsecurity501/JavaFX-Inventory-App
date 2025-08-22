package assettracking.data;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class DeviceStatusView {
    private final SimpleIntegerProperty receiptId;
    private final SimpleStringProperty serialNumber;
    private final SimpleStringProperty category;
    private final SimpleStringProperty make;
    private final SimpleStringProperty description;
    private final SimpleStringProperty status;
    private final SimpleStringProperty subStatus;
    private final SimpleStringProperty lastUpdate;
    private final SimpleStringProperty receiveDate; // New Field
    private final SimpleStringProperty changeNote;
    private final SimpleBooleanProperty isFlagged;

    public DeviceStatusView(int receiptId, String serialNumber, String category, String make, String description,
                            String status, String subStatus, String lastUpdate, String receiveDate, String changeNote, boolean isFlagged) {
        this.receiptId = new SimpleIntegerProperty(receiptId);
        this.serialNumber = new SimpleStringProperty(serialNumber);
        this.category = new SimpleStringProperty(category);
        this.make = new SimpleStringProperty(make);
        this.description = new SimpleStringProperty(description);
        this.status = new SimpleStringProperty(status);
        this.subStatus = new SimpleStringProperty(subStatus);
        this.lastUpdate = new SimpleStringProperty(lastUpdate);
        this.receiveDate = new SimpleStringProperty(receiveDate); // New Field
        this.changeNote = new SimpleStringProperty(changeNote);
        this.isFlagged = new SimpleBooleanProperty(isFlagged);
    }

    // Getters
    public int getReceiptId() { return receiptId.get(); }
    public String getSerialNumber() { return serialNumber.get(); }
    public String getCategory() { return category.get(); }
    public String getMake() { return make.get(); }
    public String getDescription() { return description.get(); }
    public String getStatus() { return status.get(); }
    public String getSubStatus() { return subStatus.get(); }
    public String getLastUpdate() { return lastUpdate.get(); }
    public String getReceiveDate() { return receiveDate.get(); } // New Getter
    public String getChangeNote() { return changeNote.get(); }
    public boolean isIsFlagged() { return isFlagged.get(); }

    // Property Getters
    public SimpleIntegerProperty receiptIdProperty() { return receiptId; }
    public SimpleStringProperty serialNumberProperty() { return serialNumber; }
    public SimpleStringProperty categoryProperty() { return category; }
    public SimpleStringProperty makeProperty() { return make; }
    public SimpleStringProperty descriptionProperty() { return description; }
    public SimpleStringProperty statusProperty() { return status; }
    public SimpleStringProperty subStatusProperty() { return subStatus; }
    public SimpleStringProperty lastUpdateProperty() { return lastUpdate; }
    public SimpleStringProperty receiveDateProperty() { return receiveDate; } // New Property Getter
    public SimpleStringProperty changeNoteProperty() { return changeNote; }
    public SimpleBooleanProperty isFlaggedProperty() { return isFlagged; }
}