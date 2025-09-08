package assettracking.data;

public class ReceiptEvent {
    private int receiptId;
    private String serialNumber;
    private int packageId;
    private String category;
    private String make;
    private String modelNumber;
    private String description;
    private String imei;

    // Constructor
    public ReceiptEvent(int receiptId, String serialNumber, int packageId, String category, String make, String modelNumber, String description, String imei) {
        this.receiptId = receiptId;
        this.serialNumber = serialNumber;
        this.packageId = packageId;
        this.category = category;
        this.make = make;
        this.modelNumber = modelNumber;
        this.description = description;
        this.imei = imei;
    }

    // Getters and Setters
    public int getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(int receiptId) {
        this.receiptId = receiptId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public int getPackageId() {
        return packageId;
    }

    public void setPackageId(int packageId) {
        this.packageId = packageId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(String modelNumber) {
        this.modelNumber = modelNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }
}

