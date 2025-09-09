package assettracking.manager;

import assettracking.controller.DeviceStatusTrackingController;
import assettracking.dao.DeviceStatusDAO;
import assettracking.data.DeviceStatusView;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Label;

public class DeviceStatusManager {
    private final DeviceStatusTrackingController controller;
    private final ObservableList<DeviceStatusView> deviceStatusList = FXCollections.observableArrayList();
    private final DeviceStatusDAO deviceStatusDAO;
    private int rowsPerPage = 200;

    public DeviceStatusManager(DeviceStatusTrackingController controller) {
        this.controller = controller;
        this.deviceStatusDAO = new DeviceStatusDAO(this, deviceStatusList);
    }

    // MODIFIED: This method now runs the count query in the background.
    public void resetPagination() {
        Task<Integer> countTask = new Task<>() {
            @Override
            protected Integer call() {
                // This runs on a background thread
                return deviceStatusDAO.fetchPageCount();
            }
        };

        countTask.setOnSucceeded(e -> {
            // This runs on the UI thread after the count is fetched
            int totalCount = countTask.getValue();
            int pageCount = (int) Math.ceil((double) totalCount / rowsPerPage);
            if (pageCount == 0) pageCount = 1;

            controller.pagination.setPageCount(pageCount);
            // Check if current page is valid, if not, reset to 0
            if (controller.pagination.getCurrentPageIndex() >= pageCount) {
                controller.pagination.setCurrentPageIndex(0);
            }
            controller.pagination.setPageFactory(this::createPage);
        });

        countTask.setOnFailed(e -> {
            countTask.getException().printStackTrace();
            // Optionally show an error to the user
        });

        new Thread(countTask).start();
    }

    private Node createPage(int pageIndex) {
        deviceStatusDAO.updateTableForPage(pageIndex);
        return new Label(); // Placeholder node, as required by the factory
    }

    public void clearFilters() {
        resetPagination();
    }

    public void updateDeviceStatus(ObservableList<DeviceStatusView> selectedDevices, String newStatus, String newSubStatus, String note, String boxId) {
        deviceStatusDAO.updateDeviceStatus(selectedDevices, newStatus, newSubStatus, note, boxId);
    }

    public int getRowsPerPage() {
        return rowsPerPage;
    }

    public void setRowsPerPage(int rowsPerPage) {
        this.rowsPerPage = rowsPerPage;
    }

    public ObservableList<DeviceStatusView> getDeviceStatusList() {
        return deviceStatusList;
    }

    public DeviceStatusTrackingController getController() {
        return controller;
    }
}