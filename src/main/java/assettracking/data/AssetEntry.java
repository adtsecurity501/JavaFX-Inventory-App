package assettracking.data;

import javafx.beans.property.SimpleStringProperty;

public class AssetEntry {
    private final SimpleStringProperty serialNumber = new SimpleStringProperty("");
    private final SimpleStringProperty imei = new SimpleStringProperty("");
    private final SimpleStringProperty category = new SimpleStringProperty("");
    private final SimpleStringProperty make = new SimpleStringProperty("");
    private final SimpleStringProperty modelNumber = new SimpleStringProperty("");
    private final SimpleStringProperty description = new SimpleStringProperty("");
    private final SimpleStringProperty probableCause = new SimpleStringProperty("");

    public AssetEntry(String serialNumber, String imei, String category, String make, String modelNumber, String description, String probableCause) {
        setSerialNumber(serialNumber);
        setImei(imei);
        setCategory(category);
        setMake(make);
        setModelNumber(modelNumber);
        setDescription(description);
        setProbableCause(probableCause);
    }

    // --- Getters & Setters ---
    public String getSerialNumber() { return serialNumber.get(); }
    public void setSerialNumber(String value) { serialNumber.set(value); }
    public SimpleStringProperty serialNumberProperty() { return serialNumber; }

    public String getImei() { return imei.get(); }
    public void setImei(String value) { imei.set(value); }
    public SimpleStringProperty imeiProperty() { return imei; }

    public String getCategory() { return category.get(); }
    public void setCategory(String value) { category.set(value); }
    public SimpleStringProperty categoryProperty() { return category; }

    public String getMake() { return make.get(); }
    public void setMake(String value) { make.set(value); }
    public SimpleStringProperty makeProperty() { return make; }

    public String getModelNumber() { return modelNumber.get(); }
    public void setModelNumber(String value) { modelNumber.set(value); }
    public SimpleStringProperty modelNumberProperty() { return modelNumber; }

    public String getDescription() { return description.get(); }
    public void setDescription(String value) { description.set(value); }
    public SimpleStringProperty descriptionProperty() { return description; }

    public String getProbableCause() { return probableCause.get(); }
    public void setProbableCause(String value) { probableCause.set(value); }
    public SimpleStringProperty probableCauseProperty() { return probableCause; }
}