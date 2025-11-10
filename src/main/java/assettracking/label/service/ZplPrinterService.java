package assettracking.label.service;

import assettracking.label.model.template.LabelTemplate;
import assettracking.label.service.template.TemplateService;
import assettracking.label.service.template.ZplGeneratorService;

import javax.print.*;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.HashAttributeSet;
import javax.print.attribute.standard.PrinterName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ZplPrinterService {

    public static String getAdtLabelZpl(String sku, String description) {
        return String.format("""
                ^XA
                ^PW710
                ^LL305
                ^FO17,13^GB677,181,8^FS
                ^FT35,66^A0N,47,48^FDSKU:^FS
                ^FT280,145^A0N,110,108^FD%s^FS
                ^BY2,3,74^FT50,150^BCN,,Y,N^FD>:%s^FS
                ^FT50,272^A0N,38,14^FB600,1,0,L^FD%s^FS
                ^PQ1,1,1,Y^XZ
                """, sku, sku, description);
    }

    public static String getSerialLabelZpl(String sku, String serial) {
        String today = DateTimeFormatter.ofPattern("MM/dd/yyyy").format(LocalDate.now());
        return String.format("""
                ^XA
                ^PW711
                ^LL0305
                ^FT45,73^A0N,45,45^FH\\^FDSerial Number:^FS
                ^FO18,11^GB675,172,8^FS
                ^FT335,71^A0N,34,33^FH\\^FD%s^FS
                ^BY2,3,69^FT45,164^BCN,,N,N^FD>:%s^FS
                ^FT18,234^A0N,33,31^FH\\^FDIT Depot^FS
                ^FT18,275^A0N,33,31^FH\\^FDSpringville^FS
                ^FT193,277^A0N,28,28^FH\\^FD%s^FS
                ^FT494,274^A0N,39,38^FH\\^FDSKU:^FS
                ^FT574,274^A0N,39,38^FH\\^FD%s^FS
                ^PQ1,1,1,Y^XZ
                """, serial, serial, today, sku);
    }

    // --- METHODS MIGRATED FROM ORIGINAL APPLICATION ---

    public static String getAssetTagZpl(String serial, String imei, String esim) {
        try {
            TemplateService templateService = new TemplateService();
            ZplGeneratorService generator = new ZplGeneratorService();

            LabelTemplate template;
            Map<String, String> data = new HashMap<>();
            data.put("serial", serial != null ? serial : "");

            // REVISED LOGIC: Check for the most specific case (both) first.
            if (imei != null && !imei.isBlank() && esim != null && !esim.isBlank()) {
                template = templateService.loadTemplate("Asset_Tag_with_IMEI_and_eSIM.json");
                data.put("imei", imei);
                data.put("esim", esim);
            } else if (esim != null && !esim.isBlank()) {
                template = templateService.loadTemplate("Asset_Tag_with_eSIM.json");
                data.put("esim", esim);
            } else if (imei != null && !imei.isBlank()) {
                template = templateService.loadTemplate("Asset_Tag_with_IMEI.json");
                data.put("imei", imei);
            } else {
                template = templateService.loadTemplate("Standard_Asset_Tag.json");
            }

            String generatedZpl = generator.generate(template, data);
            return generatedZpl.replace("^XZ", "^MMC,Y^XZ");

        } catch (IOException e) {
            System.err.println("Database error loading asset tag template: " + e.getMessage());
            return "^XA^FO50,50^A0N,40,40^FDError: Template Not Found^FS^XZ";
        }
    }


    public static String getGenericBarcodeZpl(String barcode) {
        return String.format("""
                ^XA
                ^PW711
                ^LL0305
                ^FT45,73^A0N,45,45^FH\\^FDBarcode:^FS
                ^BY2,3,69^FT45,164^BCN,,N,N^FD>:%s^FS
                ^FT45,210^A0N,30,30^FH\\\\^FD%s^FS
                ^PQ1,1,1,Y^XZ
                """, barcode, barcode);
    }

    public static String getImageLabelZpl(String imageSkuDescription, String deviceSku, String prefix) {
        String prefixLabel = (prefix != null && !prefix.isBlank()) ? "Custom Prefix:" : "";
        String finalPrefix = (prefix != null) ? prefix : "";
        return String.format("""
                ^XA
                ^PW710^LL305
                ^FO20,20^GB670,265,3^FS
                ^FO20,100^GB670,3,3^FS
                ^FT40,80^A0N,40,40^FB650,1,0,L^FD%s^FS
                ^FT35,150^A0N,35,35^FDDevice SKU:^FS
                ^FT280,150^A0N,35,35^FD%s^FS
                ^FT35,200^A0N,35,35^FD%s^FS
                ^FT280,200^A0N,35,35^FD%s^FS
                ^FT500,270^A0N,30,30^FDCompleted^FS
                ^FO643,240^GB40,40,2^FS
                ^PQ1,1,1,Y^XZ
                """, imageSkuDescription, deviceSku, prefixLabel, finalPrefix);
    }

    public boolean sendZplToPrinter(String printerName, String zplData) {
        try {
            PrintService printService = findPrintService(printerName);
            if (printService == null) {
                System.err.println("Printer not found: " + printerName);
                return false;
            }
            DocPrintJob job = printService.createPrintJob();
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            byte[] zplBytes = zplData.getBytes(StandardCharsets.US_ASCII);
            Doc doc = new SimpleDoc(zplBytes, flavor, null);
            job.print(doc, null);
            System.out.println("Successfully sent ZPL to printer: " + printerName);
            return true;
        } catch (PrintException e) {
            System.err.println("Printing failed for printer: " + printerName);
            System.err.println("Database error: " + e.getMessage());
            return false;
        }
    }

    private PrintService findPrintService(String printerName) {
        AttributeSet attrSet = new HashAttributeSet();
        attrSet.add(new PrinterName(printerName, null));
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, attrSet);
        return services.length > 0 ? services[0] : null;
    }
}