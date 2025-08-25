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

    public DeviceStatusManager(DeviceStatusTrackingController controller) {
        this.controller = controller;
        this.deviceStatusDAO = new DeviceStatusDAO(this, deviceStatusList);
    }

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
        resetPagination();
    }

    public void updateDeviceStatus(ObservableList<DeviceStatusView> selectedDevices, String newStatus, String newSubStatus, String note) {
        deviceStatusDAO.updateDeviceStatus(selectedDevices, newStatus, newSubStatus, note);
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