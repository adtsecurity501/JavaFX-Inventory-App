package assettracking.label.service.template;

import assettracking.label.model.template.BarcodeElement;
import assettracking.label.model.template.LabelTemplate;
import assettracking.label.model.template.TextElement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TemplateService {

    private final ObjectMapper objectMapper;
    private final Path templatesDirectory;

    public TemplateService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        this.templatesDirectory = Paths.get(System.getProperty("user.home"), "ADT_Label_Templates");
        try {
            Files.createDirectories(templatesDirectory);
        } catch (IOException e) {
            System.err.println("Could not create templates directory: " + e.getMessage());
            System.err.println("Database error: " + e.getMessage());
        }

        createDefaultTemplatesIfNotExists();
    }

    private static TextElement createText(String text, int x, int y, int size) {
        TextElement element = new TextElement();
        element.setText(text);
        element.setX(x);
        element.setY(y);
        element.setFontSize(size);
        return element;
    }

    private static BarcodeElement createBarcode(String content, int y, int height) {
        BarcodeElement element = new BarcodeElement();
        element.setContent(content);
        element.setX(15);
        element.setY(y);
        element.setHeight(height);
        return element;
    }

    public void saveTemplate(LabelTemplate template) throws IOException {
        String fileName = template.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
        File file = templatesDirectory.resolve(fileName).toFile();
        objectMapper.writeValue(file, template);
    }

    public LabelTemplate loadTemplate(String templateName) throws IOException {
        String fileName = templateName.endsWith(".json") ? templateName : templateName + ".json";
        File file = templatesDirectory.resolve(fileName).toFile();
        return objectMapper.readValue(file, LabelTemplate.class);
    }

    public List<String> getTemplateNames() {
        try (Stream<Path> stream = Files.list(templatesDirectory)) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Could not read template directory: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void createDefaultTemplatesIfNotExists() {
        createAssetTagTemplate();
        createAssetTagWithImeiTemplate();
    }

    private void createAssetTagTemplate() {
        String templateName = "Standard_Asset_Tag.json";
        File templateFile = templatesDirectory.resolve(templateName).toFile();
        if (templateFile.exists()) return;

        LabelTemplate template = new LabelTemplate();
        template.setName("Standard Asset Tag");
        template.setWidth(508);
        template.setHeight(203);

        // Replicates the exact layout of your original ZPL
        template.getElements().add(createText("Property of ADT, LLC", 14, 29, 18));
        template.getElements().add(createText("Help Desk:", 318, 24, 18));
        template.getElements().add(createText("1-877-238-4357", 318, 43, 18));
        template.getElements().add(createText("S/N:", 14, 60, 27));
        template.getElements().add(createText("${serial}", 81, 60, 27));
        template.getElements().add(createBarcode("${serial}", 113, 41));

        try {
            saveTemplate(template);
        } catch (IOException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }

    private void createAssetTagWithImeiTemplate() {
        String templateName = "Asset_Tag_with_IMEI.json";
        File templateFile = templatesDirectory.resolve(templateName).toFile();
        if (templateFile.exists()) return;

        LabelTemplate template = new LabelTemplate();
        template.setName("Asset Tag with IMEI");
        template.setWidth(508);
        template.setHeight(203);

        // --- NEW, TIGHTER COORDINATES TO PREVENT OVERFLOW ---
        template.getElements().add(createText("Property of ADT, LLC", 14, 29, 18));
        template.getElements().add(createText("Help Desk:", 318, 24, 18));
        template.getElements().add(createText("1-877-238-4357", 318, 43, 18));

        // S/N Section
        template.getElements().add(createText("S/N:", 14, 64, 27));
        template.getElements().add(createText("${serial}", 81, 64, 27));
        // S/N Barcode is moved UP significantly to tighten the layout
        template.getElements().add(createBarcode("${serial}", 92, 40)); // Was Y=117, H=41

        // IMEI Section
        // IMEI text is moved UP to follow the S/N barcode
        template.getElements().add(createText("IMEI:", 14, 135, 27)); // Was Y=146
        template.getElements().add(createText("${imei}", 101, 135, 27)); // Was Y=146
        // IMEI barcode is moved UP to fit on the label
        template.getElements().add(createBarcode("${imei}", 163, 40)); // Was Y=198, H=41

        try {
            saveTemplate(template);
        } catch (IOException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }
}