package assettracking.data;

public class DeviceStatus {
    private int statusId;
    private int receiptId; // Changed from deviceId
    private int sheetId;

    public DeviceStatus(int statusId, int receiptId, int sheetId) {
        this.statusId = statusId;
        this.receiptId = receiptId;
        this.sheetId = sheetId;
    }

    public int getStatusId() {
        return statusId;
    }

    public void setStatusId(int statusId) {
        this.statusId = statusId;
    }

    public int getReceiptId() {
        return receiptId;
    } // Changed from getDeviceId

    public void setReceiptId(int receiptId) {
        this.receiptId = receiptId;
    } // Changed from setDeviceId

    public int getSheetId() {
        return sheetId;
    }

    public void setSheetId(int sheetId) {
        this.sheetId = sheetId;
    }
}