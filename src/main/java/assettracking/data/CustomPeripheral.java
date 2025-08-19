package assettracking.data;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class CustomPeripheral {
    private final SimpleStringProperty category;
    private final SimpleIntegerProperty quantity;
    private final SimpleStringProperty condition;

    public CustomPeripheral(String category, int quantity, String condition) {
        this.category = new SimpleStringProperty(category);
        this.quantity = new SimpleIntegerProperty(quantity);
        this.condition = new SimpleStringProperty(condition);
    }

    // --- Standard Getters & Setters ---
    public String getCategory() { return category.get(); }
    public void setCategory(String category) { this.category.set(category); }
    public int getQuantity() { return quantity.get(); }
    public void setQuantity(int quantity) { this.quantity.set(quantity); }
    public String getCondition() { return condition.get(); }
    public void setCondition(String condition) { this.condition.set(condition); }

    // --- JavaFX Property Getters (for the TableView) ---
    public SimpleStringProperty categoryProperty() { return category; }
    public SimpleIntegerProperty quantityProperty() { return quantity; }
    public SimpleStringProperty conditionProperty() { return condition; }
}