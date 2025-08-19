package assettracking.data;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class PeripheralView {
    private final SimpleStringProperty category;
    private final SimpleStringProperty condition;
    private final SimpleStringProperty description;
    private final SimpleIntegerProperty quantity;

    public PeripheralView(String category, String condition, String description, int quantity) {
        this.category = new SimpleStringProperty(category);
        this.condition = new SimpleStringProperty(condition);
        this.description = new SimpleStringProperty(description);
        this.quantity = new SimpleIntegerProperty(quantity);
    }

    // --- Standard Getters ---
    public String getCategory() { return category.get(); }
    public String getCondition() { return condition.get(); }
    public String getDescription() { return description.get(); }
    public int getQuantity() { return quantity.get(); }

    // --- JavaFX Property Getters ---
    public SimpleStringProperty categoryProperty() { return category; }
    public SimpleStringProperty conditionProperty() { return condition; }
    public SimpleStringProperty descriptionProperty() { return description; }
    public SimpleIntegerProperty quantityProperty() { return quantity; }
}