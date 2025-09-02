package assettracking.manager;

import assettracking.data.bulk.RosterEntry;
import assettracking.ui.ExcelReader;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Service responsible for reading and parsing the "Sales Readiness Roster" Excel file.
 */
public class RosterImportService {
    public List<RosterEntry> importFromFile(File file) throws IOException {
        return ExcelReader.readRosterFile(file);
    }
}