package assettracking.label.model.template;

import java.util.ArrayList;
import java.util.List;

public class LabelTemplate {
    private String name = "New Template";
    private int width = 710;  // Corresponds to ^PW710
    private int height = 305; // Corresponds to ^LL305
    private List<LabelElement> elements = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public List<LabelElement> getElements() {
        return elements;
    }

    public void setElements(List<LabelElement> elements) {
        this.elements = elements;
    }
}