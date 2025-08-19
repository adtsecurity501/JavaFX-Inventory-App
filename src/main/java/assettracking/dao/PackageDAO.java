package assettracking.dao;

import assettracking.db.DatabaseConnection;
import assettracking.data.Package;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PackageDAO {

    public int addPackage(assettracking.data.Package pkg) {
        String sql = "INSERT INTO Packages (tracking_number, first_name, last_name, city, state, zip_code, receive_date) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, pkg.getTrackingNumber());
            stmt.setString(2, pkg.getFirstName());
            stmt.setString(3, pkg.getLastName());
            stmt.setString(4, pkg.getCity());
            stmt.setString(5, pkg.getState());
            stmt.setString(6, pkg.getZipCode());
            stmt.setString(7, pkg.getReceiveDate().toString());

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1); // Return generated packageId
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding package: " + e.getMessage());
        }
        return -1;
    }

    public List<assettracking.data.Package> getAllPackages() {
        List<assettracking.data.Package> packages = new ArrayList<>();
        String sql = "SELECT * FROM Packages";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                packages.add(new Package(
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
        } catch (SQLException e) {
            System.err.println("Error retrieving packages: " + e.getMessage());
        }
        return packages;
    }
}


