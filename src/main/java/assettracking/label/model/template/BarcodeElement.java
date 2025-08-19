package assettracking.label.model.template;

public class BarcodeElement extends LabelElement {
    private String content = "${serial}"; // Default content
    private int height = 50;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    @Override
    public String toZpl() {
        return String.format("^FO%d,%d^BY2,3,%d^BCN,,N,N^FD>:%s^FS", x, y, height, content);
    }
}