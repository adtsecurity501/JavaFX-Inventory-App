package assettracking.dao;

import assettracking.data.Package;
import assettracking.db.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PackageDAO {
    private static final Logger logger = LoggerFactory.getLogger(PackageDAO.class);


    public int addPackage(String tracking, String firstName, String lastName, String city, String state, String zip, LocalDate date) {
        String sql = "INSERT INTO packages (tracking_number, first_name, last_name, city, state, zip_code, receive_date) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, tracking);
            stmt.setString(2, firstName);
            stmt.setString(3, lastName);
            stmt.setString(4, city);
            stmt.setString(5, state);
            stmt.setString(6, zip);
            stmt.setObject(7, date);

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error adding package: ", e);
        }
        return -1;
    }

    public int countFilteredPackages(String trackingFilter, LocalDate fromDate, LocalDate toDate) throws SQLException {
        QueryAndParams queryAndParams = buildFilteredQuery(true, trackingFilter, fromDate, toDate);
        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql)) {
            for (int i = 0; i < queryAndParams.params.size(); i++) {
                stmt.setObject(i + 1, queryAndParams.params.get(i));
            }
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<Package> getFilteredPackagesForPage(String trackingFilter, LocalDate fromDate, LocalDate toDate, int rowsPerPage, int pageIndex) throws SQLException {
        List<Package> packageList = new ArrayList<>();
        QueryAndParams queryAndParams = buildFilteredQuery(false, trackingFilter, fromDate, toDate);

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql)) {

            int paramIndex = 1;
            for (Object param : queryAndParams.params) {
                stmt.setObject(paramIndex++, param);
            }
            stmt.setInt(paramIndex++, rowsPerPage);
            stmt.setInt(paramIndex, pageIndex * rowsPerPage);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                packageList.add(new Package(rs.getInt("package_id"), rs.getString("tracking_number"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("city"), rs.getString("state"), rs.getString("zip_code"), rs.getDate("receive_date").toLocalDate()));
            }
        }
        return packageList;
    }

    public List<Package> searchPackagesByTracking(String trackingFilter) throws SQLException {
        List<Package> packageList = new ArrayList<>();
        String sql = "SELECT * FROM packages WHERE tracking_number ILIKE ? ORDER BY receive_date DESC LIMIT 50";

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + trackingFilter + "%");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                packageList.add(new Package(rs.getInt("package_id"), rs.getString("tracking_number"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("city"), rs.getString("state"), rs.getString("zip_code"), rs.getDate("receive_date").toLocalDate()));
            }
        }
        return packageList;
    }

    public int getAssetCountForPackage(int packageId) throws SQLException {
        String sql = """
                    SELECT COUNT(re.serial_number)
                    FROM receipt_events re
                    JOIN (
                        SELECT serial_number, MAX(receipt_id) as max_receipt_id
                        FROM receipt_events
                        GROUP BY serial_number
                    ) latest ON re.serial_number = latest.serial_number AND re.receipt_id = latest.max_receipt_id
                    JOIN device_status ds ON re.receipt_id = ds.receipt_id
                    WHERE re.package_id = ? AND (ds.sub_status IS NULL OR ds.sub_status != 'Deleted (Mistake)')
                """;

        try (Connection conn = DatabaseConnection.getInventoryConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, packageId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public boolean deletePackage(int packageId) {
        String getReceiptIdsSql = "SELECT receipt_id FROM receipt_events WHERE package_id = ?";
        String deleteDispositionsSql = "DELETE FROM disposition_info WHERE receipt_id = ?";
        String deleteStatusesSql = "DELETE FROM device_status WHERE receipt_id = ?";
        String deleteReceiptsSql = "DELETE FROM receipt_events WHERE package_id = ?";
        String deletePackageSql = "DELETE FROM packages WHERE package_id = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInventoryConnection();
            conn.setAutoCommit(false);

            List<Integer> receiptIds = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(getReceiptIdsSql)) {
                stmt.setInt(1, packageId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        receiptIds.add(rs.getInt("receipt_id"));
                    }
                }
            }

            if (!receiptIds.isEmpty()) {
                try (PreparedStatement deleteDispStmt = conn.prepareStatement(deleteDispositionsSql); PreparedStatement deleteStatusStmt = conn.prepareStatement(deleteStatusesSql)) {
                    for (Integer receiptId : receiptIds) {
                        deleteDispStmt.setInt(1, receiptId);
                        deleteDispStmt.addBatch();

                        deleteStatusStmt.setInt(1, receiptId);
                        deleteStatusStmt.addBatch();
                    }
                    deleteDispStmt.executeBatch();
                    deleteStatusStmt.executeBatch();
                }

                try (PreparedStatement stmt = conn.prepareStatement(deleteReceiptsSql)) {
                    stmt.setInt(1, packageId);
                    stmt.executeUpdate();
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(deletePackageSql)) {
                stmt.setInt(1, packageId);
                stmt.executeUpdate();
            }
            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Database error on rollback: ", ex);
                }
            }
            logger.error("Database error deleting package: ", e);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Database error closing connection: ", e);
                }
            }
        }
    }

    private QueryAndParams buildFilteredQuery(boolean forCount, String trackingFilter, LocalDate fromDate, LocalDate toDate) {
        String selectClause = forCount ? "SELECT COUNT(*) " : "SELECT * ";
        String fromClause = "FROM packages";
        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();

        if (trackingFilter != null && !trackingFilter.isEmpty()) {
            whereClause.append(" tracking_number ILIKE ?");
            params.add("%" + trackingFilter + "%");
        }

        if (fromDate != null) {
            if (!whereClause.isEmpty()) whereClause.append(" AND");
            whereClause.append(" receive_date >= ?");
            params.add(fromDate);
        }

        if (toDate != null) {
            if (!whereClause.isEmpty()) whereClause.append(" AND");
            whereClause.append(" receive_date <= ?");
            params.add(toDate);
        }

        String fullQuery = selectClause + fromClause;
        if (!whereClause.isEmpty()) {
            fullQuery += " WHERE" + whereClause;
        }

        if (!forCount) {
            fullQuery += " ORDER BY receive_date DESC LIMIT ? OFFSET ?";
        }

        return new QueryAndParams(fullQuery, params);
    }

    private record QueryAndParams(String sql, List<Object> params) {
    }
}