package assettracking.manager;

import assettracking.data.TopModelStat;
import assettracking.db.DatabaseConnection;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardDataService {

    private static final String LATEST_RECEIPT_SUBQUERY = "SELECT serial_number, MAX(receipt_id) as max_receipt_id FROM receipt_events GROUP BY serial_number";

    public Map<String, Integer> getGranularMetrics(String intakeDateClause, String statusDateClause) throws SQLException {
        Map<String, Integer> metrics = new HashMap<>();
        String sql = String.format("""
                    SELECT
                        re.category,
                        SUM(CASE WHEN %s THEN 1 ELSE 0 END) as IntakenCount,
                        SUM(CASE WHEN ds.status = 'Processed' AND %s THEN 1 ELSE 0 END) as ProcessedCount,
                        SUM(CASE WHEN ds.status = 'Disposed' AND %s THEN 1 ELSE 0 END) as DisposedCount
                    FROM receipt_events re
                    LEFT JOIN packages p ON re.package_id = p.package_id
                    LEFT JOIN device_status ds ON re.receipt_id = ds.receipt_id
                    WHERE
                        re.category ILIKE '%%%%laptop%%%%' OR
                        re.category ILIKE '%%%%tablet%%%%' OR
                        re.category ILIKE '%%%%desktop%%%%' OR
                        re.category ILIKE '%%%%monitor%%%%'
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
                    FROM device_status ds JOIN receipt_events re ON ds.receipt_id = re.receipt_id
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
        String sql = "SELECT CASE WHEN ds.status = 'Flag!' THEN 'Flagged for Review' ELSE ds.status END as status_display, COUNT(*) as status_count FROM device_status ds JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON ds.receipt_id = latest_re.max_receipt_id GROUP BY status_display;";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                data.add(new PieChart.Data(rs.getString("status_display"), rs.getInt("status_count")));
            }
        }
        return data;
    }

    public List<PieChart.Data> getProcessedBreakdownData(String dateFilterClause) throws SQLException {
        List<PieChart.Data> data = new ArrayList<>();
        String sql = "SELECT re.category, COUNT(*) as count " +
                "FROM device_status ds " +
                "JOIN receipt_events re ON ds.receipt_id = re.receipt_id " +
                "JOIN (" + LATEST_RECEIPT_SUBQUERY + ") latest_re ON ds.receipt_id = latest_re.max_receipt_id " +
                "WHERE ds.status = 'Processed' " +
                "AND re.category IS NOT NULL AND re.category != '' " +
                "AND " + dateFilterClause + " " +
                "GROUP BY re.category " +
                "ORDER BY count DESC";

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
        String sql = "SELECT TO_CHAR(p.receive_date, 'YYYY-MM-DD') AS day, COUNT(re.receipt_id) AS device_count " +
                "FROM receipt_events re " +
                "JOIN packages p ON re.package_id = p.package_id " +
                "WHERE " + dateFilterClause + " " +
                "GROUP BY day " +
                "ORDER BY day ASC;";

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                series.getData().add(new XYChart.Data<>(rs.getString("day"), rs.getInt("device_count")));
            }
        }
        return series;
    }


    public Map<String, String> getStaticKpis() throws SQLException {
        Map<String, String> kpis = new HashMap<>();
        String triageSql = "SELECT COUNT(*) as count FROM device_status ds JOIN (" + LATEST_RECEIPT_SUBQUERY + ") l ON ds.receipt_id = l.max_receipt_id JOIN receipt_events re ON ds.receipt_id = re.receipt_id WHERE ds.status IN ('Intake', 'Triage & Repair') AND re.category NOT ILIKE '%Monitor%'";
        String awaitingDisposalSql = "SELECT COUNT(*) as count FROM device_status ds JOIN (" + LATEST_RECEIPT_SUBQUERY + ") l ON ds.receipt_id = l.max_receipt_id WHERE ds.status = 'Disposed' AND ds.sub_status IN ('Can-Am, Pending Pickup', 'Ingram, Pending Pickup', 'Ready for Wipe')";
        String turnaroundSql = "SELECT AVG(CAST(ds.last_update AS DATE) - p.receive_date) as avg_days FROM device_status ds JOIN receipt_events re ON ds.receipt_id = re.receipt_id JOIN packages p ON re.package_id = p.package_id WHERE ds.status = 'Processed' AND ds.sub_status = 'Ready for Deployment' AND ds.last_update >= (CURRENT_DATE - INTERVAL '30 DAY')";
        String dailySql = "SELECT COUNT(DISTINCT re.serial_number) as count FROM device_status ds JOIN receipt_events re ON ds.receipt_id = re.receipt_id WHERE ds.status = 'Processed' AND ds.sub_status = 'Ready for Deployment' AND ds.last_update >= CURRENT_DATE AND ds.last_update < (CURRENT_DATE + INTERVAL '1 DAY') AND EXISTS (SELECT 1 FROM device_status h_ds JOIN receipt_events h_re ON h_ds.receipt_id = h_re.receipt_id WHERE h_re.serial_number = re.serial_number AND h_ds.status IN ('Intake', 'Triage & Repair'))";

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
            try (PreparedStatement stmt = conn.prepareStatement(dailySql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) kpis.put("boxesAssembled", String.valueOf(rs.getInt("count")));
            }
        }
        return kpis;
    }
}