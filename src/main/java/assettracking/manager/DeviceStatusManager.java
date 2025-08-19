package assettracking.manager;
import assettracking.controller.DeviceStatusTrackingController;
import assettracking.dao.DeviceStatusDAO;
import assettracking.data.DeviceStatusView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;

public class DeviceStatusManager {
    private final DeviceStatusTrackingController controller;
    private final ObservableList<DeviceStatusView> deviceStatusList = FXCollections.observableArrayList();
    private int rowsPerPage = 200;
    private final DeviceStatusDAO deviceStatusDAO;
// REMOVED: No more uiConfigurator field.

    public DeviceStatusManager(DeviceStatusTrackingController controller) {
        this.controller = controller;
        this.deviceStatusDAO = new DeviceStatusDAO(this, deviceStatusList);
    }

// REMOVED: initializeUI() is no longer needed here.

    public void resetPagination() {
        Platform.runLater(() -> {
            int totalCount = deviceStatusDAO.fetchPageCount();
            int pageCount = (int) Math.ceil((double) totalCount / rowsPerPage);
            if (pageCount == 0) pageCount = 1;

            controller.pagination.setPageCount(pageCount);
            controller.pagination.setPageFactory(this::createPage);
        });
    }

    private Node createPage(int pageIndex) {
        deviceStatusDAO.updateTableForPage(pageIndex);
        return new Label();
    }

    public void clearFilters() {
        // The manager's only job is to reload the data. The controller handles clearing the UI fields.
        resetPagination();
    }

    public void updateDeviceStatus(ObservableList<DeviceStatusView> selectedDevices, String newStatus, String newSubStatus) {
        deviceStatusDAO.updateDeviceStatus(selectedDevices, newStatus, newSubStatus);
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

    // Add this getter so the DAO can access the controller's UI fields for filtering
    public DeviceStatusTrackingController getController() {
        return controller;
    }
}