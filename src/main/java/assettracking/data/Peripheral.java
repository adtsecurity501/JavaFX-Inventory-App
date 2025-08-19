package assettracking.data;

public class Peripheral {
    private int peripheralId;
    private int packageId;
    private String category;
    private String condition;

    // Constructor
    public Peripheral(int peripheralId, int packageId, String category, String condition) {
        this.peripheralId = peripheralId;
        this.packageId = packageId;
        this.category = category;
        this.condition = condition;
    }

    // Getters and Setters
    public int getPeripheralId() { return peripheralId; }
    public void setPeripheralId(int peripheralId) { this.peripheralId = peripheralId; }
    public int getPackageId() { return packageId; }
    public void setPackageId(int packageId) { this.packageId = packageId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
}