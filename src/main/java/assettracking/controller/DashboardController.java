package assettracking.controller;

import assettracking.dao.AppSettingsDAO;
import assettracking.data.TopModelStat;
import assettracking.manager.ConfettiManager;
import assettracking.manager.DashboardDataService;
import assettracking.manager.StageManager;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashboardController {

    private final DashboardDataService dataService = new DashboardDataService();
    private final AppSettingsDAO appSettingsDAO = new AppSettingsDAO();
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);

    // Bar Chart Series are now final fields, created only once.
    private final XYChart.Series<String, Number> intakeSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> processedSeries = new XYChart.Series<>();

    private final ObservableList<TopModelStat> topModelsList = FXCollections.observableArrayList();

    @FXML private BarChart<String, Number> intakeProcessedChart;
    @FXML private PieChart inventoryPieChart, deploymentBreakdownChart;
    @FXML private Label laptopsIntakenLabel, laptopsProcessedLabel, tabletsIntakenLabel, desktopsIntakenLabel, monitorsIntakenLabel;
    @FXML private Label tabletsProcessedLabel, desktopsProcessedLabel, monitorsProcessedLabel, totalProcessedLabel;
    @FXML private Label laptopsDisposedLabel, tabletsDisposedLabel, desktopsDisposedLabel, monitorsDisposedLabel;
    @FXML private Label activeTriageLabel, awaitingDisposalLabel, avgTurnaroundLabel, boxesAssembledLabel, labelsCreatedLabel;
    @FXML private Label deviceGoalPacingLabel, monitorGoalPacingLabel, timeRangeTitleLabel, breakdownTitleLabel;
    @FXML private Label inventoryOverviewTitleLabel, healthTitleLabel, pacingTitleLabel, topModelsTitleLabel;
    @FXML private ToggleGroup dateRangeToggleGroup;
    @FXML private RadioButton days7Radio;
    @FXML private GridPane mainGridPane;
    @FXML private ColumnConstraints leftColumn, rightColumn;
    @FXML private ScrollPane rightScrollPane;
    @FXML private TableView<TopModelStat> topModelsTable;
    @FXML private TableColumn<TopModelStat, String> modelNumberCol;
    @FXML private TableColumn<TopModelStat, Integer> modelCountCol;
    @FXML private Label boxesAssembledTitleLabel, labelsCreatedTitleLabel;

    private double weeklyDeviceGoal = 100.0;
    private double weeklyMonitorGoal = 50.0;
    private ConfettiManager confettiManager;
    private boolean goalMetCelebrated = false;

    @FXML
    public void initialize() {
        setupCharts();
        setupTopModelsTable();
        setupUIListeners();
        loadInitialDataAsync();
    }

    private void setupCharts() {
        intakeSeries.setName("Devices Intaken");
        processedSeries.setName("Devices Processed");
        // This is the correct place to add the series, just once at startup.
        intakeProcessedChart.getData().addAll(intakeSeries, processedSeries);
    }

    public void init(StackPane rootPane) {
        this.confettiManager = new ConfettiManager(rootPane);
    }

    private void setupUIListeners() {
        dateRangeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> refreshAllData());
        mainGridPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSingleColumn = newVal.doubleValue() < 800;
            GridPane.setRowIndex(rightScrollPane, isSingleColumn ? 1 : 0);
            GridPane.setColumnIndex(rightScrollPane, isSingleColumn ? 0 : 1);
            leftColumn.setPercentWidth(isSingleColumn ? 100 : 60);
            rightColumn.setPercentWidth(isSingleColumn ? 0 : 40);
        });
    }

    private void setupTopModelsTable() {
        modelNumberCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().modelNumber()));
        modelCountCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().count()));
        topModelsTable.setItems(topModelsList);
    }

    private void loadInitialDataAsync() {
        Task<Void> initialLoadTask = new Task<>() {
            @Override
            protected Void call() {
                AppSettingsDAO settingsDAO = new AppSettingsDAO();
                weeklyDeviceGoal = Double.parseDouble(settingsDAO.getSetting("device_goal").orElse("100.0"));
                weeklyMonitorGoal = Double.parseDouble(settingsDAO.getSetting("monitor_goal").orElse("50.0"));
                return null;
            }
        };
        initialLoadTask.setOnSucceeded(e -> refreshAllData());
        initialLoadTask.setOnFailed(e -> Platform.runLater(() -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Connection Failed", "Could not connect to the database. Error: " + initialLoadTask.getException().getMessage())));
        new Thread(initialLoadTask).start();
    }

    @FXML
    private void refreshAllData() {
        if (!isRefreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            updateDynamicTitles();
            loadGranularMetrics();
            loadStaticKpis();
            loadDynamicCharts();
            loadTopModelsData();
        } finally {
            isRefreshing.set(false);
        }
    }

    private void loadDynamicCharts() {
        Task<List<PieChart.Data>> inventoryTask = new Task<>() {
            @Override
            protected List<PieChart.Data> call() throws Exception {
                return dataService.getInventoryOverviewData();
            }
        };
        inventoryTask.setOnSucceeded(e -> setPieChartData(inventoryPieChart, FXCollections.observableArrayList(inventoryTask.getValue())));
        new Thread(inventoryTask).start();

        Task<List<PieChart.Data>> breakdownTask = new Task<>() {
            @Override
            protected List<PieChart.Data> call() throws Exception {
                return dataService.getProcessedBreakdownData(getDateFilterClause("ds.last_update"));
            }
        };
        breakdownTask.setOnSucceeded(e -> setPieChartData(deploymentBreakdownChart, FXCollections.observableArrayList(breakdownTask.getValue())));
        new Thread(breakdownTask).start();

        Task<Map<String, int[]>> intakeVsProcessedTask = new Task<>() {
            @Override
            protected Map<String, int[]> call() throws Exception {
                LocalDate endDate = LocalDate.now();
                RadioButton selected = (RadioButton) dateRangeToggleGroup.getSelectedToggle();
                String selectionText = (selected == null) ? "Last 7 Days" : selected.getText();
                LocalDate startDate = switch (selectionText) {
                    case "Today" -> endDate;
                    case "Last 30 Days" -> endDate.minusDays(29);
                    default -> endDate.minusDays(6);
                };
                return dataService.getIntakeVsProcessedData(startDate, endDate);
            }
        };

        intakeVsProcessedTask.setOnSucceeded(e -> {
            Map<String, int[]> dailyCounts = intakeVsProcessedTask.getValue();
            Platform.runLater(() -> {
                List<XYChart.Data<String, Number>> intakeData = new ArrayList<>();
                List<XYChart.Data<String, Number>> processedData = new ArrayList<>();

                LocalDate endDate = LocalDate.now();
                RadioButton selected = (RadioButton) dateRangeToggleGroup.getSelectedToggle();
                String selectionText = (selected == null) ? "Last 7 Days" : selected.getText();
                LocalDate startDate = switch (selectionText) {
                    case "Today" -> endDate;
                    case "Last 30 Days" -> endDate.minusDays(29);
                    default -> endDate.minusDays(6);
                };

                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                    String dayString = date.toString();
                    int[] counts = dailyCounts.getOrDefault(dayString, new int[]{0, 0});
                    intakeData.add(new XYChart.Data<>(dayString, counts[0]));
                    processedData.add(new XYChart.Data<>(dayString, counts[1]));
                }

                intakeSeries.getData().setAll(intakeData);
                processedSeries.getData().setAll(processedData);
            });
        });
        new Thread(intakeVsProcessedTask).start();
    }

    // --- All other methods remain the same ---
    private void loadGranularMetrics() {
        Task<Map<String, Integer>> task = new Task<>() {
            @Override
            protected Map<String, Integer> call() throws Exception {
                return dataService.getGranularMetrics(getDateFilterClause("p.receive_date"), getDateFilterClause("ds.last_update"));
            }
        };
        task.setOnSucceeded(e -> {
            Map<String, Integer> metrics = task.getValue();
            animateLabelUpdate(laptopsIntakenLabel, metrics.getOrDefault("laptopsIntaken", 0).toString());
            animateLabelUpdate(laptopsProcessedLabel, metrics.getOrDefault("laptopsProcessed", 0).toString());
            animateLabelUpdate(laptopsDisposedLabel, metrics.getOrDefault("laptopsDisposed", 0).toString());
            animateLabelUpdate(tabletsIntakenLabel, metrics.getOrDefault("tabletsIntaken", 0).toString());
            animateLabelUpdate(tabletsProcessedLabel, metrics.getOrDefault("tabletsProcessed", 0).toString());
            animateLabelUpdate(tabletsDisposedLabel, metrics.getOrDefault("tabletsDisposed", 0).toString());
            animateLabelUpdate(desktopsIntakenLabel, metrics.getOrDefault("desktopsIntaken", 0).toString());
            animateLabelUpdate(desktopsProcessedLabel, metrics.getOrDefault("desktopsProcessed", 0).toString());
            animateLabelUpdate(desktopsDisposedLabel, metrics.getOrDefault("desktopsDisposed", 0).toString());
            animateLabelUpdate(monitorsIntakenLabel, metrics.getOrDefault("monitorsIntaken", 0).toString());
            animateLabelUpdate(monitorsProcessedLabel, metrics.getOrDefault("monitorsProcessed", 0).toString());
            animateLabelUpdate(monitorsDisposedLabel, metrics.getOrDefault("monitorsDisposed", 0).toString());
            int totalDevicesProcessed = metrics.getOrDefault("laptopsProcessed", 0) + metrics.getOrDefault("tabletsProcessed", 0) + metrics.getOrDefault("desktopsProcessed", 0);
            int totalMonitorsProcessed = metrics.getOrDefault("monitorsProcessed", 0);
            animateLabelUpdate(totalProcessedLabel, String.valueOf(totalDevicesProcessed + totalMonitorsProcessed));
            updatePacing(totalDevicesProcessed, totalMonitorsProcessed);
            if (days7Radio.isSelected() && confettiManager != null && !goalMetCelebrated && (totalDevicesProcessed >= weeklyDeviceGoal || totalMonitorsProcessed >= weeklyMonitorGoal)) {
                confettiManager.start();
                goalMetCelebrated = true;
            }
        });
        task.setOnFailed(e -> Platform.runLater(() -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Dashboard Error", "Could not load performance metrics.")));
        new Thread(task).start();
    }

    private void loadStaticKpis() {
        Task<Map<String, String>> task = new Task<>() {
            @Override
            protected Map<String, String> call() throws Exception {
                return dataService.getStaticKpis(getDateFilterClause("ds.last_update"));
            }
        };
        task.setOnSucceeded(e -> {
            Map<String, String> kpis = task.getValue();
            animateLabelUpdate(activeTriageLabel, kpis.getOrDefault("activeTriage", "0"));
            animateLabelUpdate(awaitingDisposalLabel, kpis.getOrDefault("awaitingDisposal", "0"));
            animateLabelUpdate(avgTurnaroundLabel, kpis.getOrDefault("avgTurnaround", "0.0 Days"));
            int boxes = Integer.parseInt(kpis.getOrDefault("boxesAssembled", "0"));
            animateLabelUpdate(boxesAssembledLabel, String.valueOf(boxes));
            animateLabelUpdate(labelsCreatedLabel, String.valueOf(boxes * 2));
        });
        task.setOnFailed(e -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Dashboard Error", "Could not load static KPIs."));
        new Thread(task).start();
    }

    private void loadTopModelsData() {
        Task<List<TopModelStat>> task = new Task<>() {
            @Override
            protected List<TopModelStat> call() throws Exception {
                return dataService.getTopModels(getDateFilterClause("ds.last_update"));
            }
        };
        task.setOnSucceeded(e -> topModelsList.setAll(task.getValue()));
        task.setOnFailed(e -> Platform.runLater(() -> StageManager.showAlert(getStage(), Alert.AlertType.ERROR, "Database Error", "Could not load Top Processed Models data.")));
        new Thread(task).start();
    }

    private void updatePacing(int deviceCount, int monitorCount) {
        if (weeklyDeviceGoal > 0) {
            animateLabelUpdate(deviceGoalPacingLabel, String.format("%.1f%%", (deviceCount / weeklyDeviceGoal) * 100));
        } else {
            deviceGoalPacingLabel.setText("N/A");
        }
        if (weeklyMonitorGoal > 0) {
            animateLabelUpdate(monitorGoalPacingLabel, String.format("%.1f%%", (monitorCount / weeklyMonitorGoal) * 100));
        } else {
            monitorGoalPacingLabel.setText("N/A");
        }
    }

    private String getDateFilterClause(String columnName) {
        String dateColumn = String.format("CAST(%s AS DATE)", columnName);
        RadioButton selected = (RadioButton) dateRangeToggleGroup.getSelectedToggle();
        if (selected == null || selected.getText().equals("Last 7 Days")) {
            return String.format(" %s >= DATEADD('DAY', -7, CURRENT_DATE)", dateColumn);
        }
        if (selected.getText().equals("Today")) {
            return String.format(" %s >= CURRENT_DATE", dateColumn);
        }
        return String.format(" %s >= DATEADD('DAY', -30, CURRENT_DATE)", dateColumn);
    }

    private void updateDynamicTitles() {
        RadioButton selected = (RadioButton) dateRangeToggleGroup.getSelectedToggle();
        String timeSuffix;
        String pacingTimeSuffix;
        if (selected == null || selected.getText().equals("Last 7 Days")) {
            timeRangeTitleLabel.setText("Metrics for Last 7 Days");
            pacingTitleLabel.setText("Weekly Pacing (Refurbished Only)");
            timeSuffix = "(Last 7 Days)";
            pacingTimeSuffix = "Last 7 Days";
        } else if (selected.getText().equals("Today")) {
            timeRangeTitleLabel.setText("Metrics for Today");
            pacingTitleLabel.setText("Daily Pacing (Refurbished Only)");
            timeSuffix = "(Today)";
            pacingTimeSuffix = "Today";
        } else {
            timeRangeTitleLabel.setText("Metrics for Last 30 Days");
            pacingTitleLabel.setText("Monthly Pacing (Refurbished Only)");
            timeSuffix = "(Last 30 Days)";
            pacingTimeSuffix = "Last 30 Days";
        }
        healthTitleLabel.setText("Overall Health " + timeSuffix);
        breakdownTitleLabel.setText("Processed Breakdown " + timeSuffix);
        topModelsTitleLabel.setText("Top Processed Models " + timeSuffix);
        inventoryOverviewTitleLabel.setText("Asset Status Overview (All Time)");
        boxesAssembledTitleLabel.setText("Boxes Assembled " + pacingTimeSuffix);
        labelsCreatedTitleLabel.setText("Labels Created " + pacingTimeSuffix);
    }

    private void setPieChartData(PieChart chart, ObservableList<PieChart.Data> data) {
        double total = data.stream().mapToDouble(PieChart.Data::getPieValue).sum();
        if (total == 0) {
            data.clear();
            PieChart.Data noDataSlice = new PieChart.Data("No Data", 1);
            data.add(noDataSlice);
            chart.setData(data);
            Platform.runLater(() -> {
                if (noDataSlice.getNode() != null) noDataSlice.getNode().setVisible(false);
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
        chart.setData(data);
    }

    private void animateLabelUpdate(Label label, String newValue) {
        if (label == null || label.getText().equals(newValue)) return;
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

    private Stage getStage() {
        return (Stage) mainGridPane.getScene().getWindow();
    }
}