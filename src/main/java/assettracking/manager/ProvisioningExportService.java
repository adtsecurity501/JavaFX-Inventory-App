package assettracking.manager;

import assettracking.data.bulk.StagedDevice;
import assettracking.ui.ExcelWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Service responsible for writing staged device data to the Excel template.
 */
public class ProvisioningExportService {
    public void exportToFile(File outputFile, List<StagedDevice> devices) throws IOException {
        // This is the correct way to get a resource from within a JAR
        try (InputStream templateStream = getClass().getResourceAsStream("/template/Device_Submission_Template.xlsx")) {
            if (templateStream == null) {
                throw new IOException("The required Excel template 'Device_Submission_Template.xlsx' could not be found in application resources.");
            }

            // Pass the InputStream directly to the updated ExcelWriter method
            ExcelWriter.writeTemplate(templateStream, outputFile, devices);
        }
    }
}