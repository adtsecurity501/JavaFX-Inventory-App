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

### Prerequisites

-   Java JDK 21 or higher (for developers)
-   Apache Maven installed and configured in your `PATH` (for developers)

### Database Setup & Configuration

The application is designed to be highly resilient and requires zero manual database configuration for end-users.

**How it Works:**
1.  **Primary Connection (Network):** The application will first attempt to connect to the central, shared database located at `\\UTSPRJ2C2333\Server\inventorybackup.db`. This is the main source of truth.
2.  **Secondary Connection (Local Cache):** If the network share is unavailable, it will try to use a local copy of the database stored in the user's application data folder (`%APPDATA%\AssetTracking`).
3.  **Automatic Creation (First-Time Setup):** If neither the network nor the local database is found, the application will **automatically create a new, blank SQLite database** (`inventory.db`) in the user's local application data folder with the complete, correct table schema.

This means a new user can start the application for the first time without any database setup, and it will work out-of-the-box with a fresh, local database.

### Installation & Running

#### For Standard Users

1.  Download and run the `AssetTrackingInstaller.msi` file from the latest release.
2.  Launch the application from the Start Menu shortcut.
3.  The application will automatically connect to the network database or create a new local one if this is your first time running it.

#### For Developers

1.  Clone the repository:
    ```bash
    git clone https://github.com/adtsecurity501/JavaFX-Inventory-App.git
    cd JavaFX-Inventory-App
    ```

2.  **On macOS**: Before running, you must connect to the network share. Open Finder, press `Cmd`+`K`, and connect to `smb://UTSPRJ2C2333/Server`.

3.  Run the application using Maven:
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

*Fueled by a drive to be efficient, powered by coffee*