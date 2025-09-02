package assettracking.manager;

import assettracking.controller.ScanUpdateController.ScanResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the state of the success and failure lists for the ScanUpdate UI.
 */
public class ScanResultManager {
    private final ObservableList<ScanResult> successList = FXCollections.observableArrayList();
    private final ObservableList<ScanResult> failedList = FXCollections.observableArrayList();

    public void addSuccess(String serial, String status) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        successList.add(0, new ScanResult(serial, status, timestamp));
    }

    public void addFailure(String serial, String reason) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        failedList.add(0, new ScanResult(serial, reason, timestamp));
    }

    public ObservableList<ScanResult> getSuccessList() { return successList; }
    public ObservableList<ScanResult> getFailedList() { return failedList; }
    public boolean hasFailedScans() { return !failedList.isEmpty(); }
    public void clearFailedScans() { failedList.clear(); }

    public List<String> getFailedSerials() {
        return failedList.stream()
                .map(ScanResult::getSerial)
                .collect(Collectors.toList());
    }
}