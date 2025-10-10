# JavaFX Asset Tracking & Provisioning System

A powerful desktop application built with **JavaFX** to manage the complete lifecycle of IT assets. This tool
streamlines tracking from package intake to deployment, featuring a high-efficiency **bulk provisioning module**, an *
*AD/SCCM integration tool**, and a
comprehensive suite of data management utilities.

![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Java](https://img.shields.io/badge/Java-21-blue)
![JavaFX](https://img.shields.io/badge/JavaFX-21-orange)

---

## Overview

Designed for **IT depots** and **refurbishment centers**, this application centralizes asset management with a
user-friendly interface and robust, cross-platform functionality. It is built to handle both the routine tracking of
individual assets and the complex logistics of provisioning large batches of devices for new hires or deployments.

---

## Key Features

- **Real-Time Dashboard**: Monitor key metrics like weekly intake, inventory status, and processing goals at a glance.
- **AD & SCCM Machine Removal**: A dedicated tab to find and delete computer objects from Active Directory and SCCM,
  powered by PowerShell automation in the background.
- **iPad Bulk Provisioning**:
    - Import and persistently store master device lists from Excel.
    - Update existing device records (e.g., SIM card changes) automatically on re-import.
    - Streamlined UI for rapidly matching devices to employees from a roster file.
    - Manual override for on-the-fly SIM card updates.
    - Export finalized assignments to a pre-formatted Excel template for service providers.
- **Package Intake**: Simplified package receiving with automatic ZIP code lookup and duplicate tracking prevention.
- **Device Status Tracking**: Searchable, filterable asset view with status tracking and a secure **permanent deletion**
  option for erroneous entries.
- **Unified Flag Management**: A dedicated interface to view, add, edit, and permanently remove flagged devices.
  Includes a bulk-import feature from Excel files.
- **Box ID Management**: Group disposed assets into virtual boxes, print box labels, and export contents.
- **ZPL Label Printing**:
    - **Core Tools**: Supports bulk tasks like SKU + Serial label printing and bulk image labels.
    - **Utility Printing**: On-demand single asset tags or generic barcodes.
- **Robust Data Operations**:
    - Export comprehensive inventory reports to **XLSX (with summary dashboards and pivot tables)** or CSV, reflecting
      only the latest status of each device.
    - Bulk-import device autofill data from spreadsheets.
    - Import and overwrite Master Equipment List (MEL) rules from Excel.

---

## Tech Stack

- **Language**: Java 21
- **Framework**: JavaFX 21
- **UI Theme**: AtlantaFX (Dracula theme)
- **Database**: H2 Database (File-based on network share with auto-server mode)
- **Database Connection Pooling**: HikariCP
- **Logging**: Logback
- **Build Tool**: Apache Maven
- **Integration**: PowerShell (for AD/SCCM automation)
- **Labeling**: ZPL (Zebra Programming Language)
- **File I/O**: Apache POI & excel-streaming-reader for Excel processing

---

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
  work. It is an alternative for administrators to run the database in a dedicated TCP server mode.

---

### Client Setup (for End Users)

To run the application, a user needs a folder containing the following four items. This folder can be located on a
shared drive like OneDrive:

1. **`ATLegMav-1.0-SNAPSHOT.jar`** – Main application file
2. **`jre`** – Bundled Java Runtime Environment (no need to install Java)
3. **`config.properties`** – Points to the central database
4. **`Run Inventory App.bat`** – Script that launches the app

The `Run Inventory App.bat` script:

- Copies the app files to the user’s local `Temp` folder
- Uses the bundled JRE to guarantee correct Java version
- Launches the application silently in the background

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

---

## For Developers

### Clone the repository

```bash
git clone https://github.com/adtsecurity501/JavaFX-Inventory-App.git
cd JavaFX-Inventory-App
```

Ensure you have access to the network share where the H2 database file is located.

### Run with Maven

```bash
mvn javafx:run
```

---

### AI Assistance Workflow

Run this in **PowerShell** at the project root to generate a single file with all source code:

```powershell
Remove-Item -Path all_code.txt -ErrorAction SilentlyContinue
tree /F /A > all_code.txt
Add-Content -Path all_code.txt -Value "`n--- CODE ---`n"
Get-ChildItem -Recurse -Include "*.java", "*.fxml", "*.xml", "*.css" | Where-Object { $_.FullName -notlike '*\jre\*' } | ForEach-Object {
    $relativePath = $_.FullName.Substring($PWD.Path.Length)
    Add-Content -Path all_code.txt -Value "`n>> .$relativePath`n"
    Get-Content $_.FullName | Out-File -Append -FilePath all_code.txt
}
```

This creates an `all_code.txt` file you can paste into Google AI Studio for code assistance.  
👉 [Google AI Studio](https://aistudio.google.com/u/0/prompts/new_chat)

---

## Building the Executable JAR

Create a standalone JAR with all dependencies:

```bash
mvn clean package
```

Locate the JAR (e.g., `ATLegMav-1.0-SNAPSHOT.jar`) in the `target/` directory.

---

## Core Workflows

### AD & SCCM Machine Removal

1. Navigate to the **Machine Removal** tab
2. Enter one or more computer names/serials (one per line)
3. Select search source (AD, SCCM, or Both)
4. Click **Search** → results appear color-coded
5. Select computers → click **Remove Selected**
6. Confirm permanent deletion
7. Review log for details

---

### Flag Management

1. Navigate to **Data Management → Manage / Import Flags...**
2. In the new window:
    - View/search flagged devices
    - Add new flags
    - Edit by double-clicking
    - Remove flags
    - Bulk import/update from Excel

---

### iPad Bulk Provisioning

1. **Import Devices** → "Device Information Full" Excel
2. **Load Roster** → Current "Sales Readiness Roster"
3. **Scan & Assign**:
    - Scan serial
    - Enter last 4 digits of SN Ref #
    - Device auto-assigns
4. **Export** → Generate final Excel for service provider

---

### Individual Device Processing

1. **Package Intake** → Scan tracking number and details
2. **Add Assets** → Enter serial + device info
3. **Update Status** → Manage device state (WIP → Processed)

---

## Contributing

We welcome contributions!

1. Fork repo
2. Create feature branch:
   ```bash
   git checkout -b feature/your-feature
   ```
3. Commit changes:
   ```bash
   git commit -m 'Add your feature'
   ```
4. Push branch:
   ```bash
   git push origin feature/your-feature
   ```
5. Open a pull request

---

## License

Licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## Contact

For questions or feedback, open an issue on this repository.

_Fueled by a drive to be efficient, powered by coffee._
