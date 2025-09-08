package assettracking.data;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

// A JavaFX Bean to represent a record from the SKU_Table
public class Sku {
    private final StringProperty skuNumber;
    private final StringProperty modelNumber;
    private final StringProperty category;
    private final StringProperty manufacturer;
    private final StringProperty description;

    public Sku() {
        this.skuNumber = new SimpleStringProperty();
        this.modelNumber = new SimpleStringProperty();
        this.category = new SimpleStringProperty();
        this.manufacturer = new SimpleStringProperty();
        this.description = new SimpleStringProperty();
    }

    // Getters for values
    public String getSkuNumber() {
        return skuNumber.get();
    }

    // Setters
    public void setSkuNumber(String skuNumber) {
        this.skuNumber.set(skuNumber);
    }

    public String getModelNumber() {
        return modelNumber.get();
    }

    public void setModelNumber(String modelNumber) {
        this.modelNumber.set(modelNumber);
    }

    public String getCategory() {
        return category.get();
    }

    public void setCategory(String category) {
        this.category.set(category);
    }

    public String getManufacturer() {
        return manufacturer.get();
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer.set(manufacturer);
    }

    public String getDescription() {
        return description.get();
    }

    public void setDescription(String description) {
        this.description.set(description);
    }

    // Getters for JavaFX properties
    public StringProperty skuNumberProperty() {
        return skuNumber;
    }

    public StringProperty modelNumberProperty() {
        return modelNumber;
    }

    public StringProperty categoryProperty() {
        return category;
    }

    public StringProperty manufacturerProperty() {
        return manufacturer;
    }

    public StringProperty descriptionProperty() {
        return description;
    }
}