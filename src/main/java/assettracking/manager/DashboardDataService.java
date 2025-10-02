package assettracking.manager;

import assettracking.data.TopModelStat;
import assettracking.db.DatabaseConnection;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

public class DashboardDataService {
    private static final Logger logger = LoggerFactory.getLogger(DashboardDataService.class);


    private static final String LATEST_RECEIPT_SUBQUERY = "SELECT serial_number, MAX(receipt_id) as max_receipt_id FROM Receipt_Events GROUP BY serial_number";

    public Map<String, Integer> getGranularMetrics(String intakeDateClause, String statusDateClause) throws SQLException {
        Map<String, Integer> metrics = new HashMap<>();
        String sql = String.format("""
                    SELECT
                        re.category,
                        SUM(CASE WHEN %s THEN 1 ELSE 0 END) as IntakenCount,
                        SUM(CASE WHEN ds.status = 'Processed' AND %s THEN 1 ELSE 0 END) as ProcessedCount,
                        SUM(CASE WHEN ds.status = 'Disposed' AND %s THEN 1 ELSE 0 END) as DisposedCount
                    FROM Receipt_Events re
                    LEFT JOIN Packages p ON re.package_id = p.package_id
                    LEFT JOIN Device_Status ds ON re.receipt_id = ds.receipt_id
                    WHERE
                        re.category LIKE '%%%%Laptop%%%%' OR
                        re.category LIKE '%%%%Tablet%%%%' OR
                        re.category LIKE '%%%%Desktop%%%%' OR
                        re.category LIKE '%%%%Monitor%%%%'
                    GROUP BY re.category
                """, intakeDateClause, statusDateClause, statusDateClause);

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String category = rs.getString("category");
                if (category == null) continue;
                if (category.contains("Laptop")) {
                    metrics.merge("laptopsIntaken", rs.getInt("IntakenCount"), Integer::sum);
                    metrics.merge("laptopsProcessed", rs.getInt("ProcessedCount"), Integer::sum);
                    metrics.merge("laptopsDisposed", rs.getInt("DisposedCount"), Integer::sum);
                } else if (category.contains("Tablet")) {
                    metrics.merge("tabletsIntaken", rs.getInt("IntakenCount"), Integer::sum);
                    metrics.merge("tabletsProcessed", rs.getInt("ProcessedCount"), Integer::sum);
                    metrics.merge("tabletsDisposed", rs.getInt("DisposedCount"), Integer::sum);
                } else if (category.contains("Desktop")) {
                    metrics.merge("desktopsIntaken", rs.getInt("IntakenCount"), Integer::sum);
                    metrics.merge("desktopsProcessed", rs.getInt("ProcessedCount"), Integer::sum);
                    metrics.merge("desktopsDisposed", rs.getInt("DisposedCount"), Integer::sum);
                } else if (category.contains("Monitor")) {
                    metrics.merge("monitorsIntaken", rs.getInt("IntakenCount"), Integer::sum);
                    metrics.merge("monitorsProcessed", rs.getInt("ProcessedCount"), Integer::sum);
                    metrics.merge("monitorsDisposed", rs.getInt("DisposedCount"), Integer::sum);
                }
            }
        }
        return metrics;
    }

    public List<TopModelStat> getTopModels(String dateFilterClause) throws SQLException {
        List<TopModelStat> results = new ArrayList<>();
        String sql = String.format("""
                    SELECT re.model_number, COUNT(re.receipt_id) as model_count
                    FROM Device_Status ds JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id
                    WHERE ds.status = 'Processed' AND %s
                    GROUP BY re.model_number
                    HAVING re.model_number IS NOT NULL AND re.model_number != ''
                    ORDER BY model_count DESC LIMIT 10
                """, dateFilterClause);

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(new TopModelStat(rs.getString("model_number"), rs.getInt("model_count")));
            }
        }
        return results;
    }

    public List<PieChart.Data> getInventoryOverviewData() throws SQLException {
        List<PieChart.Data> data = new ArrayList<>();
        String sql = "SELECT CASE WHEN ds.status = 'Flag!' THEN 'Flagged for Review' ELSE ds.status END as status_display, COUNT(*) as status_count FROM Device_Status ds JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON ds.receipt_id = latest_re.max_receipt_id GROUP BY status_display;";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                data.add(new PieChart.Data(rs.getString("status_display"), rs.getInt("status_count")));
            }
        }
        return data;
    }

    public List<XYChart.Series<String, Number>> getIntakeVsProcessedData(LocalDate startDate, LocalDate endDate) throws SQLException {
        Map<String, int[]> dailyCounts = new LinkedHashMap<>();

        // Query 1: The alias 'day' is now correctly quoted as "day".
        String intakeSql = """
                    SELECT
                        p.receive_date AS "day",
                        COUNT(re.receipt_id) AS device_count
                    FROM Receipt_Events re
                    JOIN Packages p ON re.package_id = p.package_id
                    WHERE p.receive_date BETWEEN ? AND ?
                    GROUP BY p.receive_date
                    ORDER BY p.receive_date ASC;
                """;

        // Query 2: The alias 'day' is also correctly quoted here.
        String processedSql = """
                    SELECT
                        CAST(ds.last_update AS DATE) AS "day",
                        COUNT(ds.receipt_id) AS device_count
                    FROM Device_Status ds
                    WHERE ds.status = 'Processed'
                      AND ds.last_update >= ? AND ds.last_update < ?
                    GROUP BY CAST(ds.last_update AS DATE)
                    ORDER BY "day" ASC;
                """;

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(intakeSql)) {
                stmt.setString(1, startDate.toString());
                stmt.setString(2, endDate.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String day = rs.getString("day");
                    int intakeCount = rs.getInt("device_count");
                    dailyCounts.put(day, new int[]{intakeCount, 0});
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
                stmt.setDate(1, java.sql.Date.valueOf(startDate));
                stmt.setDate(2, java.sql.Date.valueOf(endDate.plusDays(1)));
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String day = rs.getString("day");
                    int processedCount = rs.getInt("device_count");
                    dailyCounts.computeIfAbsent(day, k -> new int[2])[1] = processedCount;
                }
            }
        }

        XYChart.Series<String, Number> intakeSeries = new XYChart.Series<>();
        intakeSeries.setName("Devices Intaken");
        XYChart.Series<String, Number> processedSeries = new XYChart.Series<>();
        processedSeries.setName("Devices Processed");

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String dayString = date.toString();
            int[] counts = dailyCounts.getOrDefault(dayString, new int[]{0, 0});
            intakeSeries.getData().add(new XYChart.Data<>(dayString, counts[0]));
            processedSeries.getData().add(new XYChart.Data<>(dayString, counts[1]));
        }

        return List.of(intakeSeries, processedSeries);
    }

    public List<PieChart.Data> getProcessedBreakdownData(String dateFilterClause) throws SQLException {
        List<PieChart.Data> data = new ArrayList<>();
        // --- THIS QUERY IS NOW CORRECTED ---
        // It adds the condition "re.category IS NOT NULL AND re.category != ''" to exclude blank categories.
        String sql = "SELECT re.category, COUNT(*) as count " + "FROM Device_Status ds " + "JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id " + "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON ds.receipt_id = latest_re.max_receipt_id " + "WHERE ds.status = 'Processed' " + "AND re.category IS NOT NULL AND re.category != '' " + // <-- THE FIX
                "AND " + dateFilterClause + " " + "GROUP BY re.category " + "ORDER BY count DESC";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                data.add(new PieChart.Data(rs.getString("category"), rs.getInt("count")));
            }
        }
        return data;
    }

    public XYChart.Series<String, Number> getIntakeVolumeData(String dateFilterClause) throws SQLException {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Devices Received");

        // --- FINAL, ROBUST QUERY ---
        // PARSEDATETIME forces the text column to be treated as a date for the query.
        String sql = "SELECT FORMATDATETIME(PARSEDATETIME(p.receive_date, 'yyyy-MM-dd'), 'yyyy-MM-dd') AS day, COUNT(re.receipt_id) AS device_count " + "FROM Receipt_Events re " + "LEFT JOIN Packages p ON re.package_id = p.package_id " + "WHERE p.receive_date IS NOT NULL AND " + dateFilterClause + " " + "GROUP BY p.receive_date " + "ORDER BY p.receive_date ASC;";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String day = rs.getString("day");
                int deviceCount = rs.getInt("device_count");
                series.getData().add(new XYChart.Data<>(day, deviceCount));
            }
        }
        return series;
    }


    public Map<String, String> getStaticKpis(String dateFilterClause) throws SQLException {
        Map<String, String> kpis = new HashMap<>();
        String triageSql = "SELECT COUNT(*) as count FROM Device_Status ds " + "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") l ON ds.receipt_id = l.max_receipt_id " + "JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id " + "WHERE ds.status = 'Intake' ";
        String awaitingDisposalSql = "SELECT COUNT(*) as count FROM Device_Status ds JOIN (" + LATEST_RECEIPT_SUBQUERY + ") l ON ds.receipt_id = l.max_receipt_id WHERE ds.status = 'Disposed' AND ds.sub_status IN ('Can-Am, Pending Pickup', 'Ingram, Pending Pickup', 'Ready for Wipe')";
        String turnaroundSql = "SELECT AVG(DATEDIFF('DAY', p.receive_date, ds.last_update)) as avg_days FROM Device_Status ds JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id JOIN Packages p ON re.package_id = p.package_id WHERE ds.status = 'Processed' AND ds.sub_status = 'Ready for Deployment' AND ds.last_update >= DATEADD('DAY', -30, CURRENT_DATE)";

        // This query now correctly uses the dateFilterClause passed from the controller
        String dailySql = String.format("""
                    SELECT COUNT(DISTINCT re.serial_number) as count
                    FROM Device_Status ds
                    JOIN Receipt_Events re ON ds.receipt_id = re.receipt_id
                    WHERE ds.status = 'Processed'
                      AND ds.sub_status = 'Ready for Deployment'
                      AND %s
                      AND EXISTS (
                        SELECT 1
                        FROM Device_Status h_ds
                        JOIN Receipt_Events h_re ON h_ds.receipt_id = h_re.receipt_id
                        WHERE h_re.serial_number = re.serial_number
                          AND h_ds.status IN ('Intake', 'Triage & Repair')
                      )
                """, dateFilterClause);

        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(triageSql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) kpis.put("activeTriage", String.valueOf(rs.getInt("count")));
            }
            try (PreparedStatement stmt = conn.prepareStatement(awaitingDisposalSql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) kpis.put("awaitingDisposal", String.valueOf(rs.getInt("count")));
            }
            try (PreparedStatement stmt = conn.prepareStatement(turnaroundSql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) kpis.put("avgTurnaround", String.format("%.1f Days", rs.getDouble("avg_days")));
            }
            // This query will now execute with the correct date filter
            try (PreparedStatement stmt = conn.prepareStatement(dailySql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) kpis.put("boxesAssembled", String.valueOf(rs.getInt("count")));
            }
        }
        return kpis;
    }
}