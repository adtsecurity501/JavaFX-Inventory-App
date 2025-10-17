package assettracking.data;

import java.time.LocalDate;

public class Package {
    private int packageId;
    private String trackingNumber;
    private String firstName;
    private String lastName;
    private String city;
    private String state;
    private String zipCode;
    private LocalDate receiveDate;

    /**
     * The main constructor used when loading data FROM the database.
     * This is the constructor that was missing.
     */
    public Package(int packageId, String trackingNumber, String firstName, String lastName, String city, String state, String zipCode, LocalDate receiveDate) {
        this.packageId = packageId;
        this.trackingNumber = trackingNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.receiveDate = receiveDate;
    }

    // Getters and Setters
    public int getPackageId() {
        return packageId;
    }

    public void setPackageId(int packageId) {
        this.packageId = packageId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public LocalDate getReceiveDate() {
        return receiveDate;
    }

    public void setReceiveDate(LocalDate receiveDate) {
        this.receiveDate = receiveDate;
    }
}