package assettracking.label.model.template;

public class TextElement extends LabelElement {
    private String text = "Sample Text"; // Default text
    private int fontSize = 30;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    @Override
    public String toZpl() {
        return String.format("^FO%d,%d^A0N,%d,%d^FD%s^FS", x, y, fontSize, fontSize, text);
    }
}