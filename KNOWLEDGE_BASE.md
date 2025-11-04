# Asset Tracking System - Project Knowledge Base

This document is the "cookbook" for the Asset Tracking System. It explains the business logic, architectural decisions,
and known quirks that are not immediately obvious from reading the code. It is intended to be read alongside the
`DEVELOPER_GUIDE.md`.

## 1. Core Business Logic & The "Why"

This section explains the business processes that the application is designed to model.

### iPad Provisioning: Why Two Separate Files?

The iPad Provisioning workflow requires two distinct Excel files: the "Device Information Full" list and the "Sales
Readiness Roster."

- **The Problem**: The depot receives a master list of all available hardware (iPads) from IT/procurement. Separately,
  the Sales Readiness department provides a roster of new hires who need devices. The challenge is to efficiently and
  accurately match a physical device from inventory to a specific person on the roster.
- **The Solution**:
    1. The **Device List** is imported once and stored persistently in the `Bulk_Devices` table. This becomes the
       application's master inventory. The app is smart enough to update existing records (like a new SIM card number)
       if a device is re-imported.
    2. The **Roster File** is loaded into memory for the current session. It represents the "work order" for that day.
    3. The **`SN Reference Number`** is the critical linking key. The workflow is optimized for speed: the user scans a
       physical device's serial number, then types the last few digits of the employee's `SN Reference Number`. The
       application filters the roster down to the single matching employee and stages the assignment.

### The Purpose of Flagged Devices

- **The Problem**: Certain devices must be prevented from entering the standard refurbishment and deployment workflow.
  This could be because they were reported lost/stolen, failed a critical diagnostic, or are part of a special program.
- **The Solution**: The `Flag_Devices` table acts as a "quarantine" list. When a device is processed during intake (
  `IntakeService`), the first thing the system does is check if the serial number exists in this table.
    - If a match is found, the device's status is immediately set to `Flag! / Requires Review`, overriding all other
      logic.
    - This prevents a potentially problematic device from being accidentally processed and shipped. The "Manage / Import
      Flags" screen provides a centralized UI to control this list.

### Bulk Move Serials Between Boxes

-   **The Problem**: During the disposal process, it's common for a large number of devices to be scanned into the wrong physical or virtual box. Manually moving dozens or hundreds of items one-by-one is tedious and error-prone.
-   **The Solution**: The "Bulk Move Serials" feature provides a high-speed interface to correct these mistakes. A user provides the source box, the destination box, and a pasted list of serial numbers. The system validates which serials actually exist in the source box and moves only those, providing a clear report of which serials were moved and which were not found. This ensures both speed and data integrity.

### Excel Export: Snapshot vs. Audit Log

- **The Requirement**: The main Excel export needs to answer the question: "What is the status of every asset in our
  inventory *right now*?"
- **The Implementation**: The query in `ReportingService` is intentionally designed to find the **single most recent**
  `Receipt_Event` for every unique serial number. This ensures each device appears only once, representing its current
  state.
- **The Distinction**: This export is a **snapshot**, not a historical audit log. To see the full history of a device (
  e.g., it was "Intake" at 9 AM and "Processed" at 3 PM), the user must use the "View Full History" feature in the UI,
  which queries all `Receipt_Events` for that specific serial.

### Database Choice: H2 with Auto-Server

- **The Problem**: The application needed a simple, file-based database that could be stored on a network share and
  accessed by multiple users simultaneously without requiring a dedicated database administrator.
- **The Solution**: H2's file-based mode with the `AUTO_SERVER=TRUE` connection string was chosen.
    - **How it Works**: The first user who connects to the database file automatically starts a lightweight TCP server
      process in the background. Subsequent users connect to this temporary server instead of the file directly,
      preventing file-locking errors. When the last user disconnects, the server shuts down.
    - **Benefit**: This provides the simplicity of a file-based database with the power of multi-user access, making it
      perfect for this depot environment. The `Start_H2_Server.bat` is an optional tool for running a more permanent,
      dedicated server if performance becomes an issue.

## 2. "Gotchas" and Known Quirks

This is the "tribal knowledge" that will save the next developer hours of debugging.

- **Initial Slowness**: The very first database connection of the day can sometimes take a few seconds longer than
  subsequent ones. This is normal, as the `AUTO_SERVER` process is starting up.
- **Adding New UI Tabs**: To add a new main tab to the application, you must edit `src/main/resources/MainView.fxml` and
  add a new `<Tab>` element with an `<fx:include>` pointing to your new FXML file.
- **Log File Location**: The application's logs are the first place to look for errors. They are automatically created
  in the user's home directory: `%USERPROFILE%/.asset_tracker_logs/app.log`.
- **JAR Resources vs. Files**: When accessing internal files (like Excel templates), never use `new File()`. These files
  are embedded in the JAR. You **must** use `getClass().getResourceAsStream("/path/to/resource")` to read them as an
  `InputStream`.
- **SLF4J "Multiple Bindings" Warning**: If you see this during a build, it means a second logging library has been
  added to the `pom.xml`. The project is configured to use `logback-classic`. You should find and remove the dependency for
  `slf4j-simple`.

## 3. Database Schema Documentation

| Table Name             | Purpose                                                                                                                                                | Critical Columns / Relations                                                          |
|:-----------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------|
| `Packages`             | Stores one record for each physical box/package received at the depot.                                                                                 | `package_id` (Primary Key)                                                            |
| `Receipt_Events`       | An **immutable audit log**. A new row is created every time a device is processed or its status is updated. This table should never have rows updated. | `receipt_id` (Primary Key), `serial_number`, `package_id` (Foreign Key to `Packages`) |
| `Device_Status`        | Stores the **current status** of a given `Receipt_Event`. This is the table that gets updated frequently.                                              | `receipt_id` (FK to `Receipt_Events`), `status`, `sub_status`, `box_id`               |
| `Physical_Assets`      | The "master record" for a device's physical attributes (make, model, etc.). It should ideally have only one row per unique `serial_number`.            | `serial_number` (Primary Key)                                                         |
| `Device_Autofill_Data` | A lookup table used to pre-populate device details based on serial number during intake.                                                               | `serial_number` (Primary Key)                                                         |
| `Flag_Devices`         | A simple lookup table to quarantine specific serial numbers.                                                                                           | `serial_number` (Primary Key)                                                         |
| `Mel_Rules`            | A lookup table for Master Equipment List rules, used to suggest actions (e.g., "Dispose") for certain models.                                          | `model_number` (Primary Key)                                                          |
| `Bulk_Devices`         | Master inventory list for the iPad Provisioning module. Persists data from imported device lists.                                                      | `SerialNumber` (Primary Key)                                                          |
| `Device_Assignments`   | An audit log of which employee was assigned which iPad during the provisioning workflow.                                                               | `SerialNumber` (FK to `Bulk_Devices`)                                                 |

## 4. Key Contacts

*This section should be filled in with the relevant names and contact information.*

| Role                 | Name                 | Contact       | Notes                                                                                                    |
|:---------------------|:---------------------|:--------------|:---------------------------------------------------------------------------------------------------------|
| **Primary User(s)**  | [Name of Depot Lead] | [Email/Phone] | The main user and subject matter expert for day-to-day workflows.                                        |
| **Stakeholder(s)**   | [Name of Manager]    | [Email/Phone] | The person who cares about the data and reports from this application.                                   |
| **IT/Network Admin** | [Name of IT Contact] | [Email/Phone] | Manages the network share where the database is located. Contact them for access or connectivity issues. |
