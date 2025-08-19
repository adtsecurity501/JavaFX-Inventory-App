package assettracking.controller;

import assettracking.db.DatabaseConnection;
import assettracking.ui.ConfettiManager;
import assettracking.ui.FlaggedDeviceImporter;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DashboardController {

    // --- FXML Chart Components ---
    @FXML private BarChart<String, Number> weeklyIntakeChart;
    @FXML private PieChart inventoryPieChart;
    @FXML private PieChart deploymentBreakdownChart;

    // --- FXML KPI Stat Components (Granular) ---
    @FXML private Label dellLaptopsIntakenLabel, getacLaptopsIntakenLabel, tabletsIntakenLabel, desktopsIntakenLabel, monitorsIntakenLabel;
    @FXML private Label dellLaptopsProcessedLabel, getacLaptopsProcessedLabel, tabletsProcessedLabel, desktopsProcessedLabel, monitorsProcessedLabel;
    @FXML private Label totalProcessedLabel;
    @FXML private Label disposedLabel;
    @FXML private Label wipBacklogLabel;
    @FXML private Label avgTurnaroundLabel;
    @FXML private Label boxesAssembledLabel;
    @FXML private Label labelsCreatedLabel;
    @FXML private Label deviceGoalPacingLabel;
    @FXML private Label monitorGoalPacingLabel;

    // --- FXML Filter/Settings Components ---
    @FXML private ToggleGroup dateRangeToggleGroup;
    @FXML private RadioButton todayRadio;
    @FXML private RadioButton days7Radio;
    @FXML private RadioButton days30Radio;
    @FXML private Button importFlagsButton;
    @FXML private TextField deviceGoalField;
    @FXML private TextField monitorGoalField;

    // --- FXML Components for Dynamic Sections ---
    @FXML private Label timeRangeTitleLabel;
    @FXML private Label breakdownTitleLabel;
    @FXML private GridPane mainGridPane;
    @FXML private ColumnConstraints leftColumn;
    @FXML private ColumnConstraints rightColumn;
    @FXML private ScrollPane rightScrollPane;

    // --- Goal and Animation ---
    private double weeklyDeviceGoal = 100.0;
    private double weeklyMonitorGoal = 50.0;
    private ConfettiManager confettiManager;
    private boolean goalMetCelebrated = false;

    private final String LATEST_RECEIPT_SUBQUERY =
            "SELECT serial_number, MAX(receipt_id) as max_receipt_id FROM Receipt_Events GROUP BY serial_number";


    @FXML
    public void initialize() {
        setupFilters();
        if (deviceGoalField != null) deviceGoalField.setText(String.valueOf((int)weeklyDeviceGoal));
        if (monitorGoalField != null) monitorGoalField.setText(String.valueOf((int)weeklyMonitorGoal));

        mainGridPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSingleColumn = newVal.doubleValue() < 800; // Threshold for collapsing
            if (isSingleColumn) {
                GridPane.setRowIndex(rightScrollPane, 1);
                GridPane.setColumnIndex(rightScrollPane, 0);
                leftColumn.setPercentWidth(100);
                rightColumn.setPercentWidth(0);
            } else {
                GridPane.setRowIndex(rightScrollPane, 0);
                GridPane.setColumnIndex(rightScrollPane, 1);
                leftColumn.setPercentWidth(60);
                rightColumn.setPercentWidth(40);
            }
        });

        refreshAllCharts();
    }

    public void init(StackPane rootPane) {
        this.confettiManager = new ConfettiManager(rootPane);
    }

    private void setupFilters() {
        dateRangeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> refreshAllCharts());
    }

    @FXML
    private void refreshAllCharts() {
        Platform.runLater(() -> {
            updateDynamicTitles();
            loadKpiStats();
            loadDynamicCharts();
            loadStaticCharts();
        });
    }

    private void loadKpiStats() {
        loadGranularMetrics();
        loadDailyAndPacingMetrics();
        loadStaticKpis();
    }

    private String getDateFilterClause(String columnName) {
        if (todayRadio.isSelected()) return " date(" + columnName + ") = date('now')";
        if (days7Radio.isSelected()) return " date(" + columnName + ") >= date('now', '-7 days')";
        if (days30Radio.isSelected()) return " date(" + columnName + ") >= date('now', '-30 days')";
        return " date(" + columnName + ") >= date('now', '-7 days')"; // Default
    }

    private int getCount(Connection conn, String baseSql, String categoryClause) throws SQLException {
        String finalSql = baseSql + " AND " + categoryClause;
        try (PreparedStatement stmt = conn.prepareStatement(finalSql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        }
        return 0;
    }

    private void animateLabelUpdate(Label label, String newValue) {
        if (label == null || label.getText().equals(newValue)) {
            return;
        }

        label.setText(newValue);

        ScaleTransition st = new ScaleTransition(Duration.millis(150), label);
        st.setFromX(1);
        st.setFromY(1);
        st.setToX(1.3);
        st.setToY(1.3);
        st.setCycleCount(2);
        st.setAutoReverse(true);
        st.play();
    }

    private void loadGranularMetrics() {
        String intakeDateClause = getDateFilterClause("p.receive_date");
        String processedDateClause = getDateFilterClause("ds.last_update");

        String intakeBaseSql = "SELECT COUNT(*) as count FROM Receipt_Events re JOIN Packages p ON re.package_id = p.package_id WHERE " + intakeDateClause;
        String processedBaseSql = "SELECT COUNT(*) as count " +
                "FROM Device_Status ds " +
                "JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id " +
                "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON re.receipt_id = latest_re.max_receipt_id " +
                "WHERE ds.status = 'Processed' AND ds.sub_status = 'Ready for Deployment' AND " + processedDateClause;

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            animateLabelUpdate(monitorsIntakenLabel, String.valueOf(getCount(conn, intakeBaseSql, "re.category LIKE '%Monitor%'")));
            animateLabelUpdate(dellLaptopsIntakenLabel, String.valueOf(getCount(conn, intakeBaseSql, "re.category LIKE '%Laptop%' AND re.make LIKE '%Dell%'")));
            animateLabelUpdate(getacLaptopsIntakenLabel, String.valueOf(getCount(conn, intakeBaseSql, "re.category LIKE '%Laptop%' AND re.make LIKE '%Getac%'")));
            animateLabelUpdate(tabletsIntakenLabel, String.valueOf(getCount(conn, intakeBaseSql, "re.category LIKE '%Tablet%'")));
            animateLabelUpdate(desktopsIntakenLabel, String.valueOf(getCount(conn, intakeBaseSql, "re.category LIKE '%Desktop%'")));

            int monitorsProcessed = getCount(conn, processedBaseSql, "re.category LIKE '%Monitor%'");
            int dellLaptopsProcessed = getCount(conn, processedBaseSql, "re.category LIKE '%Laptop%' AND re.make LIKE '%Dell%'");
            int getacLaptopsProcessed = getCount(conn, processedBaseSql, "re.category LIKE '%Laptop%' AND re.make LIKE '%Getac%'");
            int tabletsProcessed = getCount(conn, processedBaseSql, "re.category LIKE '%Tablet%'");
            int desktopsProcessed = getCount(conn, processedBaseSql, "re.category LIKE '%Desktop%'");

            animateLabelUpdate(monitorsProcessedLabel, String.valueOf(monitorsProcessed));
            animateLabelUpdate(dellLaptopsProcessedLabel, String.valueOf(dellLaptopsProcessed));
            animateLabelUpdate(getacLaptopsProcessedLabel, String.valueOf(getacLaptopsProcessed));
            animateLabelUpdate(tabletsProcessedLabel, String.valueOf(tabletsProcessed));
            animateLabelUpdate(desktopsProcessedLabel, String.valueOf(desktopsProcessed));

            int totalDevicesProcessed = dellLaptopsProcessed + getacLaptopsProcessed + tabletsProcessed + desktopsProcessed;
            animateLabelUpdate(totalProcessedLabel, String.valueOf(totalDevicesProcessed + monitorsProcessed));

            if (days7Radio.isSelected()) {
                if (confettiManager != null && !goalMetCelebrated && (totalDevicesProcessed >= weeklyDeviceGoal || monitorsProcessed >= weeklyMonitorGoal)) {
                    confettiManager.start();
                    goalMetCelebrated = true;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private void loadDailyAndPacingMetrics() {
        String dailySql = "SELECT COUNT(DISTINCT re.serial_number) as count " +
                "FROM Device_Status ds " +
                "JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id " +
                "WHERE ds.status = 'Processed' AND ds.sub_status = 'Ready for Deployment' " +
                "AND date(ds.last_update) = date('now') " +
                "AND EXISTS (SELECT 1 FROM Device_Status historical_ds JOIN Receipt_Events historical_re ON historical_ds.receipt_id = historical_re.receipt_id WHERE historical_re.serial_number = re.serial_number AND historical_ds.status = 'WIP')";

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(dailySql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int count = rs.getInt("count");
                animateLabelUpdate(boxesAssembledLabel, String.valueOf(count));
                animateLabelUpdate(labelsCreatedLabel, String.valueOf(count * 2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            boxesAssembledLabel.setText("Err");
            labelsCreatedLabel.setText("Err");
        }

        if (weeklyDeviceGoal > 0) {
            int totalDeviceIntake = Integer.parseInt(dellLaptopsIntakenLabel.getText()) + Integer.parseInt(getacLaptopsIntakenLabel.getText()) + Integer.parseInt(tabletsIntakenLabel.getText()) + Integer.parseInt(desktopsIntakenLabel.getText());
            double devicePacing = (totalDeviceIntake / weeklyDeviceGoal) * 100;
            animateLabelUpdate(deviceGoalPacingLabel, String.format("%.1f%%", devicePacing));
        } else {
            deviceGoalPacingLabel.setText("N/A");
        }

        if (weeklyMonitorGoal > 0) {
            int totalMonitorIntake = Integer.parseInt(monitorsIntakenLabel.getText());
            double monitorPacing = (totalMonitorIntake / weeklyMonitorGoal) * 100;
            animateLabelUpdate(monitorGoalPacingLabel, String.format("%.1f%%", monitorPacing));
        } else {
            monitorGoalPacingLabel.setText("N/A");
        }
    }

    private void loadStaticKpis() {
        String wipSql = "SELECT COUNT(*) as count " +
                "FROM Device_Status ds " +
                "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON ds.receipt_id = latest_re.max_receipt_id " +
                "JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id " +
                "WHERE ds.status = 'WIP' AND re.category NOT LIKE '%Monitor%'";

        String turnaroundSql = "SELECT AVG(JULIANDAY(ds.last_update) - JULIANDAY(p.receive_date)) as avg_days FROM Device_Status ds JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id JOIN Packages p ON re.package_id = p.package_id WHERE ds.status = 'Processed' AND ds.sub_status = 'Ready for Deployment' AND ds.last_update >= date('now', '-30 days')";

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(wipSql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) animateLabelUpdate(wipBacklogLabel, String.valueOf(rs.getInt("count")));
            }
            try (PreparedStatement stmt = conn.prepareStatement(turnaroundSql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) animateLabelUpdate(avgTurnaroundLabel, String.format("%.1f Days", rs.getDouble("avg_days")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            wipBacklogLabel.setText("Err");
            avgTurnaroundLabel.setText("Err");
        }
    }

    private void loadDynamicCharts() {
        String dateFilter = getDateFilterClause("ds.last_update");
        String breakdownSql = "SELECT re.category, COUNT(*) as count " +
                "FROM Device_Status ds " +
                "JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id " +
                "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON ds.receipt_id = latest_re.max_receipt_id " +
                "WHERE ds.status = 'Processed' AND ds.sub_status = 'Ready for Deployment' AND " + dateFilter +
                " GROUP BY re.category ORDER BY count DESC";

        String intakeSql = "SELECT strftime('%Y-%m-%d', p.receive_date) as day, COUNT(re.receipt_id) as device_count " +
                "FROM Receipt_Events re JOIN Packages p ON re.package_id = p.package_id " +
                "WHERE " + getDateFilterClause("p.receive_date") + " GROUP BY day ORDER BY day ASC;";

        ObservableList<PieChart.Data> breakdownData = deploymentBreakdownChart.getData();
        breakdownData.clear();

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(breakdownSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                breakdownData.add(new PieChart.Data(rs.getString("category"), rs.getInt("count")));
            }
        } catch (SQLException e) { e.printStackTrace(); }

        setPieChartLabels(deploymentBreakdownChart, breakdownData);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Devices Received");
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(intakeSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                series.getData().add(new XYChart.Data<>(rs.getString("day"), rs.getInt("device_count")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        weeklyIntakeChart.getData().setAll(series);
    }

    private void loadStaticCharts() {
        String sql = "SELECT CASE ds.status WHEN 'Flag!' THEN 'Flagged for Review' ELSE ds.status END as status_display, COUNT(*) as status_count " +
                "FROM Device_Status ds " +
                "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON ds.receipt_id = latest_re.max_receipt_id " +
                "GROUP BY status_display;";

        List<PieChart.Data> pieChartDataTemp = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                pieChartDataTemp.add(new PieChart.Data(rs.getString("status_display"), rs.getInt("status_count")));
            }
        } catch (SQLException e) { e.printStackTrace(); }

        Platform.runLater(() -> {
            ObservableList<PieChart.Data> pieChartData = inventoryPieChart.getData();
            pieChartData.clear();
            pieChartData.addAll(pieChartDataTemp);
            setPieChartLabels(inventoryPieChart, pieChartData);
        });
    }

    @FXML
    private void handleImportFlags() {
        FlaggedDeviceImporter importer = new FlaggedDeviceImporter();
        importer.importFromFile((Stage) importFlagsButton.getScene().getWindow(), this::refreshAllCharts);
    }

    @FXML
    private void handleApplyGoals() {
        try {
            double newDeviceGoal = Double.parseDouble(deviceGoalField.getText());
            if (newDeviceGoal > 0) {
                this.weeklyDeviceGoal = newDeviceGoal;
            }
        } catch (NumberFormatException e) {
            deviceGoalField.setText(String.valueOf((int)this.weeklyDeviceGoal));
        }

        try {
            double newMonitorGoal = Double.parseDouble(monitorGoalField.getText());
            if (newMonitorGoal > 0) {
                this.weeklyMonitorGoal = newMonitorGoal;
            }
        } catch (NumberFormatException e) {
            monitorGoalField.setText(String.valueOf((int)this.weeklyMonitorGoal));
        }

        goalMetCelebrated = false;
        refreshAllCharts();
    }


    private void updateDynamicTitles() {
        String title;
        if (todayRadio.isSelected()) title = "Metrics for Today";
        else if (days7Radio.isSelected()) title = "Metrics for Last 7 Days";
        else title = "Metrics for Last 30 Days";
        timeRangeTitleLabel.setText(title);
        breakdownTitleLabel.setText("Processed Breakdown (" + ((RadioButton) dateRangeToggleGroup.getSelectedToggle()).getText() + ")");
    }

    private void setPieChartLabels(PieChart chart, ObservableList<PieChart.Data> data) {
        chart.setLabelsVisible(true);
        chart.setLegendVisible(false);
        double total = data.stream().mapToDouble(PieChart.Data::getPieValue).sum();

        if (total == 0) {
            data.clear();
            PieChart.Data noDataSlice = new PieChart.Data("No Data", 1);
            data.add(noDataSlice);
            Platform.runLater(() -> {
                if (noDataSlice.getNode() != null) {
                    noDataSlice.getNode().setVisible(false);
                }
            });
            return;
        }

        data.forEach(d -> {
            String originalName = d.getName();
            if (originalName != null && !originalName.contains("%")) {
                double percentage = (d.getPieValue() / total) * 100;
                d.setName(String.format("%s\n%d (%.1f%%)", originalName, (int) d.getPieValue(), percentage));
            }
        });
    }
}