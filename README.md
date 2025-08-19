# Asset Tracking & Inventory Management System

A comprehensive desktop application built with JavaFX for managing the lifecycle of internal IT assets. This tool provides a robust solution for tracking devices from initial package intake to final deployment, complete with an integrated ZPL label printing module for streamlined workflows.

---

## 🌟 Key Features

This application is designed to centralize and simplify the tasks of an IT depot or refurbishment center.

*   **📊 Real-Time Dashboard:** Get an at-a-glance overview of key performance indicators, including weekly intake volumes, inventory status breakdowns, and progress toward processing goals.
*   **📦 Package Intake & Management:** A streamlined process for receiving packages, capturing sender information with an automatic ZIP code lookup, and preventing duplicate entries by checking for existing tracking numbers.
*   **💻 Device Status Tracking:** The core of the application. A powerful, searchable, and filterable view of every asset in the inventory. Track devices through various statuses (WIP, Processed, Flagged, etc.) and view the complete history of any individual asset.
*   **🖨️ Integrated ZPL Label Printing:** A dedicated module for all on-demand label printing needs, built to work with Zebra (ZPL) printers. It includes:
    *   **Core Workflow Tools:** Grouped sections for high-volume tasks like Bulk Deploy (SKU + Serial), printing multiple SKU or serial labels, and bulk image labels.
    *   **Utility Printing:** On-demand tools for printing single asset tags or generic barcodes.
    *   **Database Integration:** SKU fields feature an autocomplete search, pulling data directly from the inventory to reduce errors and speed up data entry.
*   **⚙️ SKU Management:** A simple interface to add, edit, and remove SKUs from the database, ensuring all product information is up-to-date.
*   **⬆️ Data Import/Export:**
    *   Import flagged device lists directly from Excel (`.xlsx`) files.
    *   Export comprehensive inventory and package reports to CSV.

---

## 🛠️ Tech Stack

*   **Language:** Java 21
*   **Framework:** JavaFX 21
*   **UI Theme:** AtlantaFX (Dracula theme)
*   **Database:** SQLite
*   **Build Tool:** Apache Maven
*   **Labeling:** ZPL (Zebra Programming Language)
*   **File I/O:** Apache POI for Excel file reading
*   **JSON Parsing:** Jackson for handling label design templates

---

## 🚀 Getting Started

Follow these steps to get the application running on your local machine.

### Prerequisites

*   **Java JDK 21** or higher.
*   **Apache Maven** installed and configured on your PATH.
*   Access to the network location where the SQLite database is stored.

### Configuration

The database connection path is hardcoded. Before running, you may need to update the UNC path in the following file:

*   **File:** `src/main/java/assettracking/db/DatabaseConnection.java`
*   **Variable:** `DATABASE_PATHS_OR_UNCS`

Update the path to point to your `inventorybackup.db` file.

### Running the Application

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/adtsecurity501/JavaFX-Inventory-App.git
    cd JavaFX-Inventory-App
    ```

2.  **Run with Maven:**
    The project includes the JavaFX Maven plugin, which makes it easy to run from the command line.

    ```bash
    mvn javafx:run
    ```

### Building the Executable JAR

You can build a single, executable JAR file (an "uber JAR") that contains all dependencies.

1.  **Run the Maven package command:**
    ```bash
    mvn clean package
    ```

2.  The executable JAR will be located in the `target/` directory (e.g., `ATLegMav-1.0-SNAPSHOT.jar`). You can run this file on any machine with Java 21 installed.

---

## 📋 Core Workflows

A quick overview of how to use the application for a standard task.

#### Receiving and Processing a New Device

1.  **Package Intake:** Go to the "Package Intake" tab and scan the package's tracking number. The system will look up return label information. Fill in any remaining details and click "Start Package Intake."
2.  **Add Assets:** In the "Package Details" window that appears, click "Receive Asset(s)." Enter the serial number and other device details. The system will automatically create receipt events and link them to the package.
3.  **Update Status:** Go to the "Device Status Tracking" tab. Find the device you just added. You can now update its status as it moves through the refurbishment process (e.g., from "WIP" to "Processed").
4.  **Print Labels:**
    *   **During Status Update:** When you set a device's status to "Ready for Deployment" in the "Scan Update" window, the application will automatically prompt you to print the required SKU and Serial labels.
    *   **On-Demand:** Go to the "Label Printing" tab. Select the "Bulk Deploy" tool, enter the SKU, and scan the serial number to print the same labels.

---

## 🤝 Contributing

This is an internal tool, but suggestions and improvements are always welcome. Feel free to fork the repository, make your changes, and submit a pull request.

## 📄 License

This project is licensed under the MIT License - see the `LICENSE` file for details.