package assettracking.controller;

import assettracking.dao.AppSettingsDAO;
import assettracking.data.TopModelStat;
import assettracking.db.DatabaseConnection;
import assettracking.manager.StageManager;
import assettracking.ui.ConfettiManager;
import assettracking.ui.FlaggedDeviceImporter;
import assettracking.ui.MelRulesImporter;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
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

    // --- (All FXML Fields are unchanged) ---
    @FXML
    private BarChart<String, Number> weeklyIntakeChart;
    @FXML
    private PieChart inventoryPieChart;
    @FXML
    private PieChart deploymentBreakdownChart;
    @FXML
    private Label laptopsIntakenLabel, laptopsProcessedLabel, tabletsIntakenLabel, desktopsIntakenLabel, monitorsIntakenLabel;
    @FXML
    private Label tabletsProcessedLabel, desktopsProcessedLabel, monitorsProcessedLabel;
    @FXML
    private Label totalProcessedLabel;
    @FXML
    private Label laptopsDisposedLabel, tabletsDisposedLabel, desktopsDisposedLabel, monitorsDisposedLabel;
    @FXML
    private Label activeTriageLabel;
    @FXML
    private Label awaitingDisposalLabel;
    @FXML
    private Label avgTurnaroundLabel;
    @FXML
    private Label boxesAssembledLabel;
    @FXML
    private Label labelsCreatedLabel;
    @FXML
    private Label deviceGoalPacingLabel;
    @FXML
    private Label monitorGoalPacingLabel;
    @FXML
    private ToggleGroup dateRangeToggleGroup;
    @FXML
    private RadioButton todayRadio;
    @FXML
    private RadioButton days7Radio;
    @FXML
    private RadioButton days30Radio;
    @FXML
    private Button importFlagsButton;
    @FXML
    private TextField deviceGoalField;
    @FXML
    private TextField monitorGoalField;
    @FXML
    private Label timeRangeTitleLabel;
    @FXML
    private Label breakdownTitleLabel;
    @FXML
    private Label inventoryOverviewTitleLabel;
    @FXML
    private GridPane mainGridPane;
    @FXML
    private ColumnConstraints leftColumn;
    @FXML
    private ColumnConstraints rightColumn;
    @FXML
    private ScrollPane rightScrollPane;
    @FXML
    private Label healthTitleLabel;
    @FXML
    private Label pacingTitleLabel;
    @FXML
    private Label topModelsTitleLabel;
    @FXML
    private TableView<TopModelStat> topModelsTable;
    @FXML
    private TableColumn<TopModelStat, String> modelNumberCol;
    @FXML
    private TableColumn<TopModelStat, Integer> modelCountCol;

    private final ObservableList<TopModelStat> topModelsList = FXCollections.observableArrayList();
    private final AppSettingsDAO appSettingsDAO = new AppSettingsDAO();

    private double weeklyDeviceGoal = 100.0;
    private double weeklyMonitorGoal = 50.0;
    private ConfettiManager confettiManager;
    private boolean goalMetCelebrated = false;
    private final String LATEST_RECEIPT_SUBQUERY =
            "SELECT serial_number, MAX(receipt_id) as max_receipt_id FROM Receipt_Events GROUP BY serial_number";

    @FXML
    public void initialize() {
        setupFilters();
        setupTopModelsTable();
        loadGoals();
        if (deviceGoalField != null) deviceGoalField.setText(String.valueOf((int) weeklyDeviceGoal));
        if (monitorGoalField != null) monitorGoalField.setText(String.valueOf((int) weeklyMonitorGoal));

        mainGridPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSingleColumn = newVal.doubleValue() < 800;
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

    /**
     * FINAL CORRECTED METHOD: This now uses lambda expressions that correctly call the
     * accessor methods of the 'TopModelStat' record (e.g., .modelNumber() and .count()).
     * This is the definitive fix for the "Cannot read from unreadable property" error.
     */
    private void setupTopModelsTable() {
        modelNumberCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().modelNumber()));
        modelCountCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().count()));

        topModelsTable.setItems(topModelsList);
    }

    @FXML
    private void refreshAllCharts() {
        Platform.runLater(() -> {
            updateDynamicTitles();
            loadKpiStats();
            loadDynamicCharts();
            loadStaticCharts();
            loadTopModelsData();
        });
    }

    private void loadTopModelsData() {
        topModelsList.clear();
        String dateFilter = getDateFilterClause("ds.last_update");
        String sql = """
                    SELECT
                        re.model_number,
                        COUNT(re.receipt_id) as model_count
                    FROM
                        Device_Status ds
                    JOIN
                        Receipt_Events re ON ds.receipt_id = re.receipt_id
                    WHERE
                        ds.status = 'Processed' AND %s
                    GROUP BY
                        re.model_number
                    HAVING
                        re.model_number IS NOT NULL AND re.model_number != ''
                    ORDER BY
                        model_count DESC
                    LIMIT 10
                """.formatted(dateFilter);

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                topModelsList.add(new TopModelStat(rs.getString("model_number"), rs.getInt("model_count")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            StageManager.showAlert(topModelsTable.getScene().getWindow(), Alert.AlertType.ERROR, "Database Error", "Could not load Top Processed Models data.");
        }
    }

    private void updateDynamicTitles() {
        String timeSuffix;
        if (todayRadio.isSelected()) {
            timeRangeTitleLabel.setText("Metrics for Today");
            pacingTitleLabel.setText("Daily Pacing (Refurbished Only)");
            timeSuffix = "(Today)";
        } else if (days7Radio.isSelected()) {
            timeRangeTitleLabel.setText("Metrics for Last 7 Days");
            pacingTitleLabel.setText("Weekly Pacing (Refurbished Only)");
            timeSuffix = "(Last 7 Days)";
        } else {
            timeRangeTitleLabel.setText("Metrics for Last 30 Days");
            pacingTitleLabel.setText("Monthly Pacing (Refurbished Only)");
            timeSuffix = "(Last 30 Days)";
        }

        healthTitleLabel.setText("Overall Health " + timeSuffix);
        breakdownTitleLabel.setText("Processed Breakdown " + timeSuffix);
        topModelsTitleLabel.setText("Top Processed Models " + timeSuffix);
        inventoryOverviewTitleLabel.setText("Asset Status Overview (All Time)");
    }

    private void loadStaticKpis() {
        String triageSql = "SELECT COUNT(*) as count FROM Device_Status ds JOIN (" + LATEST_RECEIPT_SUBQUERY + ") l ON ds.receipt_id = l.max_receipt_id JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id WHERE ds.status IN ('Intake', 'Triage & Repair') AND re.category NOT LIKE '%Monitor%'";

        String awaitingDisposalSql = "SELECT COUNT(*) as count FROM Device_Status ds " +
                "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") l ON ds.receipt_id = l.max_receipt_id " +
                "WHERE ds.status = 'Disposed' AND ds.sub_status IN ('Can-Am, Pending Pickup', 'Ingram, Pending Pickup', 'Ready for Wipe')";

        String turnaroundSql = "SELECT AVG(JULIANDAY(ds.last_update) - JULIANDAY(p.receive_date)) as avg_days FROM Device_Status ds JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id JOIN Packages p ON re.package_id = p.package_id WHERE ds.status = 'Processed' AND ds.sub_status = 'Ready for Deployment' AND ds.last_update >= date('now', '-30 days')";

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(triageSql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) animateLabelUpdate(activeTriageLabel, String.valueOf(rs.getInt("count")));
            }
            try (PreparedStatement stmt = conn.prepareStatement(awaitingDisposalSql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) animateLabelUpdate(awaitingDisposalLabel, String.valueOf(rs.getInt("count")));
            }
            try (PreparedStatement stmt = conn.prepareStatement(turnaroundSql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    animateLabelUpdate(avgTurnaroundLabel, String.format("%.1f Days", rs.getDouble("avg_days")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- NO CHANGES TO ANY OTHER METHODS IN THIS FILE ---

    public void init(StackPane rootPane) {
        this.confettiManager = new ConfettiManager(rootPane);
    }

    private void setupFilters() {
        dateRangeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> refreshAllCharts());
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
        return " date(" + columnName + ") >= date('now', '-7 days')";
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
        String dateClause = getDateFilterClause("ds.last_update");
        String intakeDateClause = getDateFilterClause("p.receive_date");

        // This single query replaces 12 separate database calls.
        String consolidatedSql = """
        SELECT
            re.category,
            SUM(CASE WHEN %s THEN 1 ELSE 0 END) as IntakenCount,
            SUM(CASE WHEN ds.status = 'Processed' AND %s THEN 1 ELSE 0 END) as ProcessedCount,
            SUM(CASE WHEN ds.status = 'Disposed' AND %s THEN 1 ELSE 0 END) as DisposedCount
        FROM
            Receipt_Events re
        LEFT JOIN
            Packages p ON re.package_id = p.package_id
        LEFT JOIN
            Device_Status ds ON re.receipt_id = ds.receipt_id
        WHERE
            re.category LIKE '%%Laptop%%' OR
            re.category LIKE '%%Tablet%%' OR
            re.category LIKE '%%Desktop%%' OR
            re.category LIKE '%%Monitor%%'
        GROUP BY
            re.category
    """.formatted(intakeDateClause, dateClause, dateClause);

        // Default all counts to 0
        int laptopsIntaken = 0, tabletsIntaken = 0, desktopsIntaken = 0, monitorsIntaken = 0;
        int laptopsProcessed = 0, tabletsProcessed = 0, desktopsProcessed = 0, monitorsProcessed = 0;
        int laptopsDisposed = 0, tabletsDisposed = 0, desktopsDisposed = 0, monitorsDisposed = 0;

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(consolidatedSql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String category = rs.getString("category");
                if (category == null) continue;

                if (category.contains("Laptop")) {
                    laptopsIntaken += rs.getInt("IntakenCount");
                    laptopsProcessed += rs.getInt("ProcessedCount");
                    laptopsDisposed += rs.getInt("DisposedCount");
                } else if (category.contains("Tablet")) {
                    tabletsIntaken += rs.getInt("IntakenCount");
                    tabletsProcessed += rs.getInt("ProcessedCount");
                    tabletsDisposed += rs.getInt("DisposedCount");
                } else if (category.contains("Desktop")) {
                    desktopsIntaken += rs.getInt("IntakenCount");
                    desktopsProcessed += rs.getInt("ProcessedCount");
                    desktopsDisposed += rs.getInt("DisposedCount");
                } else if (category.contains("Monitor")) {
                    monitorsIntaken += rs.getInt("IntakenCount");
                    monitorsProcessed += rs.getInt("ProcessedCount");
                    monitorsDisposed += rs.getInt("DisposedCount");
                }
            }

            // Update all labels with the results from the single query
            animateLabelUpdate(laptopsIntakenLabel, String.valueOf(laptopsIntaken));
            animateLabelUpdate(tabletsIntakenLabel, String.valueOf(tabletsIntaken));
            animateLabelUpdate(desktopsIntakenLabel, String.valueOf(desktopsIntaken));
            animateLabelUpdate(monitorsIntakenLabel, String.valueOf(monitorsIntaken));

            animateLabelUpdate(laptopsProcessedLabel, String.valueOf(laptopsProcessed));
            animateLabelUpdate(tabletsProcessedLabel, String.valueOf(tabletsProcessed));
            animateLabelUpdate(desktopsProcessedLabel, String.valueOf(desktopsProcessed));
            animateLabelUpdate(monitorsProcessedLabel, String.valueOf(monitorsProcessed));

            animateLabelUpdate(laptopsDisposedLabel, String.valueOf(laptopsDisposed));
            animateLabelUpdate(tabletsDisposedLabel, String.valueOf(tabletsDisposed));
            animateLabelUpdate(desktopsDisposedLabel, String.valueOf(desktopsDisposed));
            animateLabelUpdate(monitorsDisposedLabel, String.valueOf(monitorsDisposed));

            int totalDevicesProcessed = laptopsProcessed + tabletsProcessed + desktopsProcessed + monitorsProcessed;
            animateLabelUpdate(totalProcessedLabel, String.valueOf(totalDevicesProcessed));

            if (days7Radio.isSelected() && confettiManager != null && !goalMetCelebrated && (totalDevicesProcessed >= weeklyDeviceGoal || monitorsProcessed >= weeklyMonitorGoal)) {
                confettiManager.start();
                goalMetCelebrated = true;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            // Optionally show an alert to the user
            StageManager.showAlert(totalProcessedLabel.getScene().getWindow(), Alert.AlertType.ERROR, "Dashboard Error", "Could not load performance metrics.");
        }
    }

    private void loadDailyAndPacingMetrics() {
        String dailySql = "SELECT COUNT(DISTINCT re.serial_number) as count " + "FROM Device_Status ds " + "JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id " + "WHERE ds.status = 'Processed' AND ds.sub_status = 'Ready for Deployment' " + "AND date(ds.last_update) = date('now') " + "AND EXISTS (SELECT 1 FROM Device_Status h_ds JOIN Receipt_Events h_re ON h_ds.receipt_id = h_re.receipt_id WHERE h_re.serial_number = re.serial_number AND h_ds.status IN ('Intake', 'Triage & Repair'))";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(dailySql); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int count = rs.getInt("count");
                animateLabelUpdate(boxesAssembledLabel, String.valueOf(count));
                animateLabelUpdate(labelsCreatedLabel, String.valueOf(count * 2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        int totalDeviceIntake = 0;
        int totalMonitorIntake = 0;
        try {
            totalDeviceIntake = Integer.parseInt(laptopsIntakenLabel.getText()) + Integer.parseInt(tabletsIntakenLabel.getText()) + Integer.parseInt(desktopsIntakenLabel.getText());
        } catch (NumberFormatException e) { /* Default to 0 */ }
        try {
            totalMonitorIntake = Integer.parseInt(monitorsIntakenLabel.getText());
        } catch (NumberFormatException e) { /* Default to 0 */ }
        if (weeklyDeviceGoal > 0) {
            animateLabelUpdate(deviceGoalPacingLabel, String.format("%.1f%%", (totalDeviceIntake / weeklyDeviceGoal) * 100));
        } else {
            deviceGoalPacingLabel.setText("N/A");
        }
        if (weeklyMonitorGoal > 0) {
            animateLabelUpdate(monitorGoalPacingLabel, String.format("%.1f%%", (totalMonitorIntake / weeklyMonitorGoal) * 100));
        } else {
            monitorGoalPacingLabel.setText("N/A");
        }
    }

    private void loadDynamicCharts() {
        String dateFilter = getDateFilterClause("ds.last_update");
        String breakdownSql = "SELECT re.category, COUNT(*) as count " + "FROM Device_Status ds " + "JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id " + "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON ds.receipt_id = latest_re.max_receipt_id " + "WHERE ds.status = 'Processed' AND " + dateFilter + " GROUP BY re.category ORDER BY count DESC";
        String intakeSql = "SELECT strftime('%Y-%m-%d', p.receive_date) as day, COUNT(re.receipt_id) as device_count " + "FROM Receipt_Events re JOIN Packages p ON re.package_id = p.package_id " + "WHERE " + getDateFilterClause("p.receive_date") + " GROUP BY day ORDER BY day ASC;";
        ObservableList<PieChart.Data> breakdownData = FXCollections.observableArrayList();
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(breakdownSql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                breakdownData.add(new PieChart.Data(rs.getString("category"), rs.getInt("count")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        deploymentBreakdownChart.setData(breakdownData);
        setPieChartLabels(deploymentBreakdownChart, breakdownData);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Devices Received");
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(intakeSql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                series.getData().add(new XYChart.Data<>(rs.getString("day"), rs.getInt("device_count")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        weeklyIntakeChart.getData().setAll(series);
    }

    private void loadStaticCharts() {
        String sql = "SELECT CASE ds.status WHEN 'Flag!' THEN 'Flagged for Review' ELSE ds.status END as status_display, COUNT(*) as status_count " + "FROM Device_Status ds " + "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON ds.receipt_id = latest_re.max_receipt_id " + "GROUP BY status_display;";
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                pieChartData.add(new PieChart.Data(rs.getString("status_display"), rs.getInt("status_count")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        inventoryPieChart.setData(pieChartData);
        setPieChartLabels(inventoryPieChart, pieChartData);
    }

    @FXML
    private void handleImportFlags() {
        FlaggedDeviceImporter importer = new FlaggedDeviceImporter();
        importer.importFromFile((Stage) importFlagsButton.getScene().getWindow(), this::refreshAllCharts);
    }

    /**
     * NEW METHOD: Loads goal values from the database when the dashboard starts.
     * If no values are found, it uses the default hardcoded values.
     */
    private void loadGoals() {
        // Load device goal, default to 100 if not found
        String deviceGoalStr = appSettingsDAO.getSetting("device_goal").orElse("100.0");
        this.weeklyDeviceGoal = Double.parseDouble(deviceGoalStr);
        if (deviceGoalField != null) {
            deviceGoalField.setText(String.valueOf((int)this.weeklyDeviceGoal));
        }

        // Load monitor goal, default to 50 if not found
        String monitorGoalStr = appSettingsDAO.getSetting("monitor_goal").orElse("50.0");
        this.weeklyMonitorGoal = Double.parseDouble(monitorGoalStr);
        if (monitorGoalField != null) {
            monitorGoalField.setText(String.valueOf((int)this.weeklyMonitorGoal));
        }
    }

    /**
     * MODIFIED METHOD: Now saves the goals to the database when the "Apply" button is clicked.
     */
    @FXML
    private void handleApplyGoals() {
        try {
            weeklyDeviceGoal = Double.parseDouble(deviceGoalField.getText());
            appSettingsDAO.saveSetting("device_goal", String.valueOf(weeklyDeviceGoal)); // SAVE TO DB
        } catch (NumberFormatException e) {
            deviceGoalField.setText(String.valueOf((int)weeklyDeviceGoal));
        }
        try {
            weeklyMonitorGoal = Double.parseDouble(monitorGoalField.getText());
            appSettingsDAO.saveSetting("monitor_goal", String.valueOf(weeklyMonitorGoal)); // SAVE TO DB
        } catch (NumberFormatException e) {
            monitorGoalField.setText(String.valueOf((int)weeklyMonitorGoal));
        }
        goalMetCelebrated = false;
        refreshAllCharts();
    }


    @FXML
    private void handleManageMelRules() {
        MelRulesImporter importer = new MelRulesImporter();
        importer.importFromFile((Stage) importFlagsButton.getScene().getWindow());
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