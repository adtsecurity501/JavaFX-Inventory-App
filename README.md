# JavaFX Asset Tracking & Provisioning System

A powerful desktop application built with **JavaFX** to manage the complete lifecycle of IT assets. This tool
streamlines tracking from package intake to deployment, featuring a high-efficiency **bulk provisioning module** and a
comprehensive suite of data management tools.

![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Java](https://img.shields.io/badge/Java-21-blue)
![JavaFX](https://img.shields.io/badge/JavaFX-21-orange)

## Overview

Designed for **IT depots** and **refurbishment centers**, this application centralizes asset management with a
user-friendly interface and robust, cross-platform functionality. It is built to handle both the routine tracking of
individual assets and the complex logistics of provisioning large batches of devices for new hires or deployments.

## Key Features

- **Real-Time Dashboard**: Monitor key metrics like weekly intake, inventory status, and processing goals at a glance.
- **iPad Bulk Provisioning**:
    - Import and persistently store master device lists from Excel.
    - Update existing device records (e.g., SIM card changes) automatically on re-import.
    - Streamlined UI for rapidly matching devices to employees from a roster file.
    - Manual override for on-the-fly SIM card updates.
    - Export finalized assignments to a pre-formatted Excel template for service providers.
- **Package Intake**: Simplified package receiving with automatic ZIP code lookup and duplicate tracking prevention.
- **Device Status Tracking**: Searchable, filterable asset view with status tracking and a secure, **permanent deletion
  ** option for erroneous entries.
- **Unified Flag Management**: A dedicated interface to view, add, edit, and permanently remove flagged devices.
  Includes a bulk-import feature from Excel files.
- **Box ID Management**: Group disposed assets into virtual boxes, print box labels, and export contents.
- **ZPL Label Printing**:
    - **Core Tools**: Supports bulk tasks like SKU + Serial label printing and bulk image labels.
    - **Utility Printing**: On-demand single asset tags or generic barcodes.
- **Robust Data Operations**:
    - Export comprehensive inventory reports to **XLSX (with summary dashboards and pivot tables)** or CSV, accurately
      reflecting only the latest status of each device.
    - Bulk-import device autofill data from spreadsheets.
    - Import and overwrite Master Equipment List (MEL) rules from Excel.

## Tech Stack

- **Language**: Java 21
- **Framework**: JavaFX 21
- **UI Theme**: AtlantaFX (Dracula theme)
- **Database**: H2 Database (File-based on network share with auto-server mode)
- **Database Connection Pooling**: HikariCP
- **Logging**: Logback
- **Build Tool**: Apache Maven
- **Labeling**: ZPL (Zebra Programming Language)
- **File I/O**: Apache POI & excel-streaming-reader for Excel processing

## Deployment & Running

This application uses a client-server model where the user's machine runs the client application, and the database
resides on a central network share.

### Database Setup

The application connects to a central H2 database file located on a network share. It uses H2's `AUTO_SERVER=TRUE`
feature, which means the first user to connect automatically starts a temporary server process, allowing other users to
connect simultaneously.

- **Location**: The database files (`inventorybackup.mv.db`) must be accessible on a network path.
- **Configuration**: The path is set in the `config.properties` file.

    ```properties
    # config.properties
    # The URL points to a file on a network share.
    # AUTO_SERVER=TRUE allows multiple users to connect.
    db.url=jdbc:h2:file:////UTSPRJ2C2333/Server/inventorybackup;AUTO_SERVER=TRUE
    db.user=sa
    db.password=
    ```
- **`H2 Server` Folder**: This folder, containing `Start_H2_Server.bat`, is **not required** for the standard setup to
  work. It is an alternative for administrators to run the database in a dedicated TCP server mode, which can be more
  robust but is not necessary for daily operation.

### Client Setup (for End Users)

To run the application, a user needs a folder containing the following four items. This folder can be located on a
shared drive like OneDrive.

1. **`ATLegMav-1.0-SNAPSHOT.jar`**: The main application file.
2. **`jre`**: A folder containing the specific Java Runtime Environment needed to run the application. This means users
   do not need to install Java on their own machines.
3. **`config.properties`**: The configuration file that tells the application where to find the database.
4. **`Run Inventory App.bat`**: The script that users double-click to start the application.

The `Run Inventory App.bat` script is designed to be robust and user-friendly:

- It copies the application files to the user's local `Temp` folder to avoid issues with running from a network drive.
- It uses the bundled `jre` to ensure the correct Java version is always used.
- It launches the application silently in the background, so no command prompt window is left open.

```batch
@echo off
:: This script copies the application locally and launches it silently.

:: 1. Define Paths
SET "SCRIPT_DIR=%~dp0"
SET "ONEDRIVE_JAR=%SCRIPT_DIR%ATLegMav-1.0-SNAPSHOT.jar"
SET "ONEDRIVE_CONFIG=%SCRIPT_DIR%config.properties"
SET "LOCAL_JAR=%TEMP%\ATLegMav-1.0-SNAPSHOT.jar"
SET "LOCAL_CONFIG=%TEMP%\config.properties"
SET "BUNDLED_JAVA=%SCRIPT_DIR%jre\bin\java.exe"

:: 2. Copy files to the local temp folder.
COPY /Y "%ONEDRIVE_JAR%" "%LOCAL_JAR%" > NUL
COPY /Y "%ONEDRIVE_CONFIG%" "%LOCAL_CONFIG%" > NUL

:: 3. Launch the application silently using PowerShell.
powershell.exe -WindowStyle Hidden -Command "& '%BUNDLED_JAVA%' --enable-native-access=ALL-UNNAMED -jar '%LOCAL_JAR%'"
```

### For Developers

1. Clone the repository:
   ```bash
   git clone https://github.com/adtsecurity501/JavaFX-Inventory-App.git
   cd JavaFX-Inventory-App
   ```
2. Ensure you have access to the network share where the H2 database file is located.
3. Run the application using Maven:
   ```bash
   mvn javafx:run
   ```

#### The strat for AI assistance

1. Run this code in PS in the root of the project
   ```
    Remove-Item -Path all_code.txt -ErrorAction SilentlyContinue
    tree /F /A > all_code.txt
    Add-Content -Path all_code.txt -Value "`n--- CODE ---`n"
    Get-ChildItem -Recurse -Include "*.java", "*.fxml", "*.xml", "*.css" | Where-Object { $_.FullName -notlike '*\jre\*' } | ForEach-Object { $relativePath = $_.FullName.Substring($PWD.Path.Length); Add-Content -Path all_code.txt -Value "`n>> .$relativePath`n"; Get-Content $_.FullName | Out-File -Append -FilePath all_code.txt }

2. It will generate an all_code.txt file that you can copy and paste the contents into Google AI Studio. You could ask
   it to help you add a new feature or debug code, etc.... The more context given to AI the
   better. https://aistudio.google.com/u/0/prompts/new_chat

### Building the Executable JAR

Create a standalone JAR with all dependencies:

1. Run the package command:
   ```bash
   mvn clean package
   ```
2. Locate the JAR (e.g., `ATLegMav-1.0-SNAPSHOT.jar`) in the `target/` directory.

## Core Workflows

### Flag Management Workflow

1. Navigate to the **Data Management** tab.
2. Click **Manage / Import Flags...**.
3. In the new window, you can:
    - **View and search** all currently flagged devices.
    - **Manually add** a new flag using the form at the bottom.
    - **Double-click** a reason in the table to edit it directly.
    - Select a device and click **Remove Selected Flag**.
    - Click **Import from File...** to bulk-add or update flags from an Excel sheet.

### iPad Bulk Provisioning Workflow

1. **Import Devices**: On the "iPad Provisioning" tab, click "Import Device List..." and select your "Device Information
   Full" Excel file.
2. **Load Roster**: Click "Import Roster..." and select the current "Sales Readiness Roster" file.
3. **Scan & Assign**:
    - Scan an iPad's serial number.
    - Type the last 4 digits of the employee's SN Ref # and press Enter.
    - The device is automatically assigned and added to the staging table.
4. **Export**: Once all devices are staged, click "Export to Template..." to generate the final Excel file.

### Individual Device Processing Workflow

1. **Package Intake**: Scan a tracking number, complete details, and click "Start Package Intake."
2. **Add Assets**: In the "Package Details" window, select "Receive Asset(s)," enter the serial number and device
   details, then save.
3. **Update Status**: In the "Device Status Tracking" tab, locate the device and update its status (e.g., from WIP to
   Processed).

## Contributing

We welcome contributions to enhance this internal tool. To contribute:

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit changes: `git commit -m 'Add your feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a pull request.

## License

Licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

## Contact

For questions or feedback, open an issue on this repository.

*Fueled by a drive to be efficient, powered by coffee.*
