package assettracking.manager;

import assettracking.dao.bulk.iPadProvisioningDAO;
import assettracking.data.bulk.BulkDevice;
import assettracking.ui.ExcelReader;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Service responsible for importing the "Device Information Full" Excel file
 * and persisting it to the database.
 */
public class DeviceImportService {
    private final iPadProvisioningDAO dao = new iPadProvisioningDAO();

    public int importFromFile(File file) throws IOException, SQLException {
        // Step 1: Read and parse the Excel file into a list of objects
        List<BulkDevice> devices = ExcelReader.readDeviceFile(file);

        if (devices.isEmpty()) {
            return 0;
        }

        // Step 2: Save the parsed data to the database
        dao.upsertBulkDevices(devices);

        return devices.size();
    }
}