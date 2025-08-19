package assettracking.data;

public class DispositionInfo {
    private int dispositionId;
    private int receiptId; // Changed from deviceId
    private boolean isEveron;
    private boolean isEndOfLife;
    private boolean isUnderCapacity;
    private String otherDisqualification;

    public DispositionInfo(int dispositionId, int receiptId, boolean isEveron, boolean isEndOfLife, boolean isUnderCapacity, String otherDisqualification) {
        this.dispositionId = dispositionId;
        this.receiptId = receiptId;
        this.isEveron = isEveron;
        this.isEndOfLife = isEndOfLife;
        this.isUnderCapacity = isUnderCapacity;
        this.otherDisqualification = otherDisqualification;
    }

    public int getDispositionId() { return dispositionId; }
    public void setDispositionId(int dispositionId) { this.dispositionId = dispositionId; }
    public int getReceiptId() { return receiptId; } // Changed from getDeviceId
    public void setReceiptId(int receiptId) { this.receiptId = receiptId; } // Changed from setDeviceId
    public boolean isEveron() { return isEveron; }
    public void setEveron(boolean everon) { isEveron = everon; }
    public boolean isEndOfLife() { return isEndOfLife; }
    public void setEndOfLife(boolean endOfLife) { isEndOfLife = endOfLife; }
    public boolean isUnderCapacity() { return isUnderCapacity; }
    public void setUnderCapacity(boolean underCapacity) { isUnderCapacity = underCapacity; }
    public String getOtherDisqualification() { return otherDisqualification; }
    public void setOtherDisqualification(String otherDisqualification) { this.otherDisqualification = otherDisqualification; }
}