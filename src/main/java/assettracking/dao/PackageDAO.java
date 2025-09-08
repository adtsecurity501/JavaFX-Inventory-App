package assettracking.dao;

import assettracking.db.DatabaseConnection;
import assettracking.data.Package;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PackageDAO {

    // A private record to bundle query details, keeping it internal to the DAO.
    private record QueryAndParams(String sql, List<Object> params) {}

    public int addPackage(Package pkg) {
        return addPackage(pkg.getTrackingNumber(), pkg.getFirstName(), pkg.getLastName(), pkg.getCity(), pkg.getState(), pkg.getZipCode(), pkg.getReceiveDate());
    }

    public int addPackage(String tracking, String firstName, String lastName, String city, String state, String zip, LocalDate date) {
        String sql = "INSERT INTO Packages (tracking_number, first_name, last_name, city, state, zip_code, receive_date) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, tracking);
            stmt.setString(2, firstName);
            stmt.setString(3, lastName);
            stmt.setString(4, city);
            stmt.setString(5, state);
            stmt.setString(6, zip);
            // THIS IS THE CORRECTED LINE: Using setObject for the date
            stmt.setObject(7, date);

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding package: " + e.getMessage());
        }
        return -1;
    }

    public int countFilteredPackages(String trackingFilter, LocalDate fromDate, LocalDate toDate) throws SQLException {
        QueryAndParams queryAndParams = buildFilteredQuery(true, trackingFilter, fromDate, toDate);
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql)) {
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

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(queryAndParams.sql)) {

            int paramIndex = 1;
            for (Object param : queryAndParams.params) {
                stmt.setObject(paramIndex++, param);
            }
            stmt.setInt(paramIndex++, rowsPerPage);
            stmt.setInt(paramIndex, pageIndex * rowsPerPage);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                packageList.add(new Package(
                        rs.getInt("package_id"),
                        rs.getString("tracking_number"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("zip_code"),
                        LocalDate.parse(rs.getString("receive_date"))
                ));
            }
        }
        return packageList;
    }

    public List<Package> searchPackagesByTracking(String trackingFilter) throws SQLException {
        List<Package> packageList = new ArrayList<>();
        String sql = "SELECT * FROM Packages WHERE tracking_number LIKE ? ORDER BY receive_date DESC LIMIT 50";

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + trackingFilter + "%");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                packageList.add(new Package(
                        rs.getInt("package_id"),
                        rs.getString("tracking_number"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("zip_code"),
                        LocalDate.parse(rs.getString("receive_date"))
                ));
            }
        }
        return packageList;
    }
    public int getAssetCountForPackage(int packageId) throws SQLException {
        // This new query is much smarter. It only counts devices in the package
        // whose most recent status is NOT 'Deleted (Mistake)'.
        String sql = """
        SELECT COUNT(re.serial_number)
        FROM Receipt_Events re
        JOIN (
            SELECT serial_number, MAX(receipt_id) as max_receipt_id
            FROM Receipt_Events
            GROUP BY serial_number
        ) latest ON re.serial_number = latest.serial_number AND re.receipt_id = latest.max_receipt_id
        JOIN Device_Status ds ON re.receipt_id = ds.receipt_id
        WHERE re.package_id = ? AND (ds.sub_status IS NULL OR ds.sub_status != 'Deleted (Mistake)')
    """;

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        // We need to delete in the correct order to respect foreign key constraints.
        String getReceiptIdsSql = "SELECT receipt_id FROM Receipt_Events WHERE package_id = ?";
        String deleteDispositionsSql = "DELETE FROM Disposition_Info WHERE receipt_id = ?";
        String deleteStatusesSql = "DELETE FROM Device_Status WHERE receipt_id = ?";
        String deleteReceiptsSql = "DELETE FROM Receipt_Events WHERE package_id = ?";
        String deletePackageSql = "DELETE FROM Packages WHERE package_id = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInventoryConnection();
            // Start a transaction
            conn.setAutoCommit(false);

            // 1. Find all receipt IDs associated with this package
            List<Integer> receiptIds = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(getReceiptIdsSql)) {
                stmt.setInt(1, packageId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        receiptIds.add(rs.getInt("receipt_id"));
                    }
                }
            }

            // Only proceed if there are receipts to delete
            if (!receiptIds.isEmpty()) {
                // 2. Delete all associated Disposition_Info and Device_Status records
                try (PreparedStatement deleteDispStmt = conn.prepareStatement(deleteDispositionsSql);
                     PreparedStatement deleteStatusStmt = conn.prepareStatement(deleteStatusesSql)) {
                    for (Integer receiptId : receiptIds) {
                        deleteDispStmt.setInt(1, receiptId);
                        deleteDispStmt.addBatch();

                        deleteStatusStmt.setInt(1, receiptId);
                        deleteStatusStmt.addBatch();
                    }
                    deleteDispStmt.executeBatch();
                    deleteStatusStmt.executeBatch();
                }

                // 3. Now it's safe to delete the Receipt_Events
                try (PreparedStatement stmt = conn.prepareStatement(deleteReceiptsSql)) {
                    stmt.setInt(1, packageId);
                    stmt.executeUpdate();
                }
            }

            // 4. Finally, it's safe to delete the Package itself
            try (PreparedStatement stmt = conn.prepareStatement(deletePackageSql)) {
                stmt.setInt(1, packageId);
                stmt.executeUpdate();
            }

            // If all operations succeeded, commit the transaction
            conn.commit();
            return true;

        } catch (SQLException e) {
            // If anything went wrong, roll back the entire transaction
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private QueryAndParams buildFilteredQuery(boolean forCount, String trackingFilter, LocalDate fromDate, LocalDate toDate) {
        String selectClause = forCount ? "SELECT COUNT(*) " : "SELECT * ";
        String fromClause = "FROM Packages";

        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();

        if (trackingFilter != null && !trackingFilter.isEmpty()) {
            whereClause.append(" tracking_number LIKE ?");
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
            fullQuery += " WHERE" + whereClause.toString();
        }

        if (!forCount) {
            fullQuery += " ORDER BY receive_date DESC LIMIT ? OFFSET ?";
        }

        return new QueryAndParams(fullQuery, params);
    }
}