# JavaFX Inventory Management System

A powerful desktop application built with JavaFX to manage the lifecycle of IT assets. This tool streamlines tracking from package intake to deployment, featuring integrated ZPL label printing for efficient workflows.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-orange)](https://openjfx.io/)

## Overview

Designed for IT depots and refurbishment centers, this application centralizes asset management with a user-friendly interface and robust functionality.

### Key Features

- **Real-Time Dashboard**: Monitor key metrics like weekly intake, inventory status, and processing goals at a glance.
- **Package Intake**: Streamlined package receiving with automatic ZIP code lookup and duplicate tracking prevention.
- **Device Status Tracking**: Searchable, filterable asset view with status tracking (e.g., WIP, Processed, Flagged) and full history.
- **ZPL Label Printing**:
    - **Core Tools**: Supports bulk tasks like SKU + Serial label printing and bulk image labels.
    - **Utility Printing**: On-demand single asset tags or generic barcodes.
    - **Database Integration**: Autocomplete SKU fields to minimize errors.
- **SKU Management**: Easily add, edit, or remove SKUs to keep product data current.
- **Data Import/Export**:
    - Import flagged device lists from Excel (.xlsx).
    - Export inventory and package reports to CSV.

## Tech Stack

- **Language**: Java 21
- **Framework**: JavaFX 21
- **UI Theme**: AtlantaFX (Dracula theme)
- **Database**: SQLite
- **Build Tool**: Apache Maven
- **Labeling**: ZPL (Zebra Programming Language)
- **File I/O**: Apache POI for Excel processing
- **JSON Parsing**: Jackson for label design templates

## Getting Started

Set up and run the application locally with these steps.

### Prerequisites

- Java JDK 21 or higher
- Apache Maven installed and configured in your PATH
- Access to the network location hosting the SQLite database

### Configuration

Update the hardcoded database connection path:

- **File**: `src/main/java/assettracking/db/DatabaseConnection.java`
- **Variable**: `DATABASE_PATHS_OR_UNCS`
- Set to your `inventorybackup.db` file location.

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/adtsecurity501/JavaFX-Inventory-App.git
   cd JavaFX-Inventory-App
   ```

2. Run the application:
   ```bash
   mvn javafx:run
   ```

### Building the Executable JAR

Create a standalone JAR with all dependencies:

1. Run the package command:
   ```bash
   mvn clean package
   ```

2. Locate the JAR (e.g., `ATLegMav-1.0-SNAPSHOT.jar`) in the `target/` directory. Run it on any machine with Java 21.

## Core Workflows

### Processing a New Device

1. **Package Intake**: In the "Package Intake" tab, scan the tracking number, complete details, and click "Start Package Intake."
2. **Add Assets**: In the "Package Details" window, select "Receive Asset(s)," enter serial number and details, then save.
3. **Update Status**: In the "Device Status Tracking" tab, locate the device and update its status (e.g., WIP to Processed).
4. **Print Labels**:
    - **During Update**: Set status to "Ready for Deployment" to prompt SKU and Serial label printing.
    - **On-Demand**: Use the "Label Printing" tab, select "Bulk Deploy," enter SKU, scan serial, and print.

## Contributing

We welcome contributions to enhance this internal tool. To contribute:

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit changes: `git commit -m 'Add your feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a pull request.

Ensure code adheres to project standards and includes tests.

## License

Licensed under the [MIT License](LICENSE). See the `LICENSE` file for details.

## Contact

For questions or feedback, open an [issue](https://github.com/adtsecurity501/JavaFX-Inventory-App/issues) on this repository.

---

*Built with 💡 for efficient IT asset management.*