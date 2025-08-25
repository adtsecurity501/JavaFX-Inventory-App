# JavaFX Asset Tracking & Provisioning System

A powerful desktop application built with JavaFX to manage the complete lifecycle of IT assets. This tool streamlines tracking from package intake to deployment and now includes a high-efficiency bulk provisioning module for devices like iPads, featuring integrated ZPL label printing and persistent data management.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-orange)](https://openjfx.io/)

## Overview

Designed for IT depots and refurbishment centers, this application centralizes asset management with a user-friendly interface and robust, cross-platform functionality. It is built to handle both the routine tracking of individual assets and the complex logistics of provisioning large batches of devices for new hires or deployments.

### Key Features

-   **Real-Time Dashboard**: Monitor key metrics like weekly intake, inventory status, and processing goals at a glance.
-   **iPad Bulk Provisioning**:
    -   Import and persistently store master device lists from Excel.
    -   Update existing device records (e.g., SIM card changes) automatically on re-import.
    -   Streamlined UI for rapidly matching devices to employees from a roster file.
    -   Manual override for on-the-fly SIM card updates.
    -   Export finalized assignments to a pre-formatted Excel template for service providers.
    -   Creates a permanent, auditable history of all device assignments.
-   **Package Intake**: Simplified package receiving with automatic ZIP code lookup and duplicate tracking prevention.
-   **Device Status Tracking**: Searchable, filterable asset view with status tracking (e.g., WIP, Processed, Flagged) and full history for individual devices.
-   **ZPL Label Printing**:
    -   **Core Tools**: Supports bulk tasks like SKU + Serial label printing and bulk image labels.
    -   **Utility Printing**: On-demand single asset tags or generic barcodes.
    -   **Database Integration**: Autocomplete SKU fields to minimize errors.
-   **SKU Management**: Easily add, edit, or remove SKUs to keep product data current.
-   **Data Import/Export**:
    -   Import flagged device lists from Excel (`.xlsx`).
    -   Export inventory and package reports to CSV.

## Tech Stack

-   **Language**: Java 21
-   **Framework**: JavaFX 21
-   **UI Theme**: AtlantaFX (Dracula theme)
-   **Database**: SQLite (with support for both network share and local fallback)
-   **Build Tool**: Apache Maven
-   **Labeling**: ZPL (Zebra Programming Language)
-   **File I/O**: Apache POI for Excel processing

## Getting Started

Set up and run the application locally with these steps.

### Prerequisites

-   Java JDK 21 or higher
-   Apache Maven installed and configured in your `PATH`
-   **For Windows**: Access to the network share hosting the SQLite database.
-   **For macOS**: Ability to mount the Windows network share via SMB.

### Configuration

The database connection path is now **dynamically configured** in the code to be cross-platform.

-   **File**: `src/main/java/assettracking/db/DatabaseConnection.java`
-   The code will automatically attempt to connect to:
    -   `\\UTSPRJ2C2333\Server\inventorybackup.db` on **Windows**.
    -   `/Volumes/Server/inventorybackup.db` on **macOS**.
-   **On macOS**, you must first connect to the server via Finder (`Cmd`+`K`) using the address `smb://UTSPRJ2C2333/Server`.

### Installation & Running

1.  Clone the repository:
    ```bash
    git clone https://github.com/adtsecurity501/JavaFX-Inventory-App.git
    cd JavaFX-Inventory-App
    ```

2.  Run the application using Maven:
    ```bash
    mvn javafx:run
    ```

### Building the Executable JAR

Create a standalone JAR with all dependencies:

1.  Run the package command:
    ```bash
    mvn clean package
    ```

2.  Locate the JAR (e.g., `ATLegMav-1.0-SNAPSHOT.jar`) in the `target/` directory. Run it on any machine with Java 21 installed.

## Core Workflows

### iPad Bulk Provisioning Workflow

1.  **Import Devices**: On the "iPad Provisioning" tab, click **"Import/Update Device List..."** and select your "Device Information Full" Excel file. This only needs to be done once or when new devices are added to inventory.
2.  **Load Roster**: Click **"Load Roster File..."** and select the current "Sales Readiness Roster" file.
3.  **Scan & Assign**:
    -   The cursor will automatically focus on the "Scan Serial Number" field. Scan an iPad.
    -   The cursor moves to the "SN Ref #" field. Type the last 4 digits.
    -   Press **Enter**. If there is one matching employee, the device is automatically added to the staging table.
4.  **Export**: Once all devices are staged, click **"Export to Template..."** to generate the final Excel file for the service provider.

### Individual Device Processing Workflow

1.  **Package Intake**: In the "Package Intake" tab, scan a tracking number, complete details, and click "Start Package Intake."
2.  **Add Assets**: In the "Package Details" window, select "Receive Asset(s)," enter the serial number and device details, then save.
3.  **Update Status**: In the "Device Status Tracking" tab, locate the device and update its status (e.g., from WIP to Processed).
4.  **Print Labels**: Use the "Label Printing" tab for on-demand label creation.

## Contributing

We welcome contributions to enhance this internal tool. To contribute:

1.  Fork the repository.
2.  Create a feature branch: `git checkout -b feature/your-feature`
3.  Commit changes: `git commit -m 'Add your feature'`
4.  Push to the branch: `git push origin feature/your-feature`
5.  Open a pull request.

Ensure code adheres to project standards and includes tests where applicable.

## License

Licensed under the [MIT License](LICENSE). See the `LICENSE` file for details.

## Contact

For questions or feedback, open an [issue](https://github.com/adtsecurity501/JavaFX-Inventory-App/issues) on this repository.

---

*Built with 💡 for efficient IT asset management.*
