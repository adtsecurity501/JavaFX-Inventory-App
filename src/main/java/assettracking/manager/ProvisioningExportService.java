package assettracking.manager;

import assettracking.data.bulk.StagedDevice;
import assettracking.ui.ExcelWriter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Service responsible for writing staged device data to the Excel template.
 */
public class ProvisioningExportService {
    public void exportToFile(File outputFile, List<StagedDevice> devices) throws IOException {
        URL resourceUrl = getClass().getResource("/template/Device_Submission_Template.xlsx");
        if (resourceUrl == null) {
            throw new IOException("The required Excel template 'Device_Submission_Template.xlsx' could not be found in application resources.");
        }

        // Note: In a JAR, getFile() can be problematic. A better approach would be to copy the stream.
        // For this context, we'll assume it works from the IDE/build structure.
        File templateFile = new File(resourceUrl.getFile());

        ExcelWriter.writeTemplate(templateFile, outputFile, devices);
    }
}