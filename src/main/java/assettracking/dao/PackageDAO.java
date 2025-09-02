package assettracking.dao;

import assettracking.db.DatabaseConnection;
import assettracking.data.Package;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PackageDAO {

    /**
     * Original method that takes a Package object.
     */
    public int addPackage(Package pkg) {
        return addPackage(pkg.getTrackingNumber(), pkg.getFirstName(), pkg.getLastName(), pkg.getCity(), pkg.getState(), pkg.getZipCode(), pkg.getReceiveDate());
    }

    /**
     * NEW OVERLOADED METHOD: Inserts a new package using raw data.
     * This is a more robust way to handle package creation from controllers.
     * @return The generated packageId, or -1 on failure.
     */
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
            stmt.setString(7, date.toString());

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

    public List<Package> getAllPackages() {
        List<Package> packages = new ArrayList<>();
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