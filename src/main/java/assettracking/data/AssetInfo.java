package assettracking.data;

public class AssetInfo {
    private String serialNumber;
    private String make;
    private String modelNumber; // Corresponds to part_number in your DB's Assets table
    private String description;
    private String category;
    private String imei;
    private boolean everonSerial;
    private String capacity;

    // Default constructor
    public AssetInfo() {}

    // Parameterized constructor (optional, but can be useful)
    public AssetInfo(String serialNumber, String make, String modelNumber, String description, String category, String imei, boolean everonSerial, String capacity) {
        this.serialNumber = serialNumber;
        this.make = make;
        this.modelNumber = modelNumber;
        this.description = description;
        this.category = category;
        this.imei = imei;
        this.everonSerial = everonSerial;
        this.capacity = capacity;
    }

    // Getters and Setters
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModelNumber() { return modelNumber; }
    public void setModelNumber(String modelNumber) { this.modelNumber = modelNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public boolean isEveronSerial() { return everonSerial; }
    public void setEveronSerial(boolean everonSerial) { this.everonSerial = everonSerial; }

    public String getCapacity() { return capacity; }
    public void setCapacity(String capacity) { this.capacity = capacity; }

    @Override
    public String toString() {
        return "AssetInfo{" +
               "serialNumber='" + serialNumber + '\'' +
               ", make='" + make + '\'' +
               ", modelNumber='" + modelNumber + '\'' +
               ", description='" + description + '\'' +
               ", category='" + category + '\'' +
               ", imei='" + imei + '\'' +
               ", everonSerial=" + everonSerial +
               ", capacity='" + capacity + '\'' +
               '}';
    }
}