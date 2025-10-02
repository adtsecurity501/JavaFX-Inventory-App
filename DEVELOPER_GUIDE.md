# Asset Tracking System - Developer's Guide

Welcome to the Asset Tracking & Provisioning System! This guide is the primary onboarding document for developers who
will be maintaining or extending this application. It provides a deep dive into the architecture, project structure,
core concepts, and common workflows.

## 1. High-Level Architecture: Modified MVC

The application follows a pattern similar to **Model-View-Controller (MVC)**, adapted for JavaFX and scaled for
complexity. The goal is a strong separation of concerns, making the code easier to test, maintain, and extend.

- **Model (The Data & Business Logic Layer)**: This is the core of the application. It is further subdivided to enforce
  structure.
    - **`data` packages**: These are simple Java objects (POJOs/Beans) that purely hold data (e.g., `AssetInfo`,
      `Package`, `Sku`). They have no logic.
    - **`dao` packages (Data Access Objects)**: **All SQL queries and direct database communication MUST live here.**
      This is a strict rule. DAOs are responsible for CRUD (Create, Read, Update, Delete) operations. This isolation
      means that if the database schema changes, the DAOs are the only classes that need modification.
    - **`manager` packages (Service Layer)**: This is the "business logic" layer. Managers orchestrate complex
      operations by calling one or more DAOs. They act as the brain of the application, ensuring that operations happen
      in the correct order and within transactions. For example, `IntakeService` coordinates the multi-step process of
      creating an asset, logging a receipt, and setting its initial status.

- **View (The UI Layer)**: This is what the user sees.
    - **`.fxml` files** (`src/main/resources`): These XML-based files define the layout, components, and structure of
      the UI. They are designed to be edited with tools like Scene Builder, though they can be written by hand.
    - **`style.css`**: The single stylesheet that defines the application's visual theme, including custom colors for
      table rows and component styling. It builds on the AtlantaFX theme.

- **Controller (The UI Logic Layer)**: This is the "glue" that connects the View to the Model.
    - **`controller` packages**: Each FXML file has a corresponding controller class. The controller's job is to handle
      user events (e.g., button clicks, text input), gather data from the UI, and then **delegate all business logic to
      a `manager` class**. After the manager completes its work, the controller is responsible for updating the UI with
      the result (e.g., refreshing a table, showing a feedback message).

## 2. Development Environment Setup

### Prerequisites

- **IDE**: IntelliJ IDEA is recommended, but Eclipse or VS Code with Java extensions will work.
- **Java Version**: **JDK 21** is required. Ensure your IDE is configured to use this version.
- **Maven**: The project is managed by Maven. The `pom.xml` file defines all dependencies. Your IDE should automatically
  detect and download these.

### Database Setup

The application uses an H2 database file on a network share, configured for multi-user access via H2's "auto-server"
mode.

1. **Network Access**: Ensure you have read/write access to the network location where the `inventorybackup.mv.db` file
   resides.
2. **Configuration File**: The connection path is defined in `src/main/resources/config.properties`. This file is *
   *external** to the final JAR, meaning it can be changed without rebuilding the application.
   ```properties
   # The AUTO_SERVER=TRUE flag is critical. It tells the first client that connects
   # to automatically start a temporary TCP server that other clients can then join.
   db.url=jdbc:h2:file:////UTSPRJ2C2333/Server/inventorybackup;AUTO_SERVER=TRUE
   db.user=sa
   db.password=
   ```
3. **No Manual Server Needed**: For standard development, you do not need to run `Start_H2_Server.bat`. The
   `AUTO_SERVER` mode handles this automatically. The batch file is a fallback for administrators to run a dedicated,
   persistent server if needed.

### Running the Application from the IDE

1. Open the project in your IDE and let Maven resolve all dependencies.
2. Locate the `assettracking.MainApp.java` class.
3. Run the `main` method. The application should launch.

## 3. Project Structure Deep Dive

Understanding the package structure is key to finding code quickly.

```
src/main/java/assettracking/
├── controller/       # UI Logic - Handles events from the FXML views. One per FXML file.
│   └── handler/      # Helper classes to break down very large controllers (e.g., AddAssetDialog).
├── dao/              # Data Access Objects - All SQL queries live here.
│   └── bulk/         # DAOs specific to the iPad provisioning module.
├── data/             # Model/Data Objects - Simple classes representing database tables or UI rows.
│   └── bulk/         # Data objects for the iPad provisioning module.
├── db/               # Database connection management. Manages the HikariCP connection pool.
├── label/            # ZPL Label Printing Logic.
│   ├── model/        # Data models for dynamic label templates (JSON-based).
│   └── service/      # Services for generating ZPL code and sending it to a printer.
├── manager/          # Business Logic - Orchestrates DAOs and complex, multi-step tasks.
└── ui/               # Reusable custom JavaFX UI components (e.g., AutoCompletePopup, ExcelReader).

src/main/resources/
├── *.fxml            # FXML files that define the UI layout for each screen/dialog.
├── style.css         # Application-wide stylesheet.
├── config.properties # External database connection settings.
├── logback.xml       # Configuration for the logging framework.
└── template/         # Excel templates used for data import/export.
```

## 4. Core Concepts & Architectural Patterns

### Concurrency: The UI Must Never Freeze

This is the most important principle in the application. Any long-running operation (database query, file I/O) **must**
be executed on a background thread.

- **Pattern**: We use `javafx.concurrent.Task` for all background work.
- **Execution Flow**:
    1. The code that takes time is placed inside the `call()` method of a `Task`.
    2. The `Task` is started on a `new Thread()`.
    3. UI updates (e.g., showing results, updating labels) are placed inside the `setOnSucceeded()` or `setOnFailed()`
       event handlers. These handlers are guaranteed to run on the main JavaFX Application Thread, making them safe for
       UI modifications.

**Canonical Example (`DashboardController.java`):**

```java
// 1. Create the Task
Task<Map<String, Integer>> task = new Task<>() {
            @Override
            protected Map<String, Integer> call() throws Exception {
                // This runs on a background thread (SAFE FOR LONG OPERATIONS)
                return dataService.getGranularMetrics(...);
            }
        };

// 2. Define what happens on success (on the UI thread)
task.

setOnSucceeded(e ->{
Map<String, Integer> metrics = task.getValue();

animateLabelUpdate(laptopsIntakenLabel, metrics.get("laptopsIntaken").

toString());
        });

// 3. Start the task
        new

Thread(task).

start();
```

### Database Connection Pooling with HikariCP

To avoid the high cost of opening a new database connection for every query, we use HikariCP, a high-performance
connection pool.

- **`DatabaseConnection.java`**: This class manages a **singleton** `HikariDataSource`.
- **Usage**: Any method needing a connection calls `DatabaseConnection.getInventoryConnection()`. This doesn't create a
  new connection; it borrows an existing, open one from the pool.
- **`try-with-resources`**: It is **mandatory** to wrap all `Connection` objects in a `try-with-resources` block. This
  ensures that the connection is automatically returned to the pool, even if an exception occurs.
  ```java
  // Correct usage
  try (Connection conn = DatabaseConnection.getInventoryConnection()) {
      // ... use the connection ...
  } catch (SQLException e) {
      // ... handle error ...
  }
  // The connection is automatically closed (returned to the pool) here.
  ```

### Transaction Management

For operations that involve multiple database writes (e.g., adding a device, its receipt, and its status), it's critical
that all writes succeed or none of them do.

- **Pattern**: We use manual transaction management.
    1. Get a connection from the pool.
    2. Set `conn.setAutoCommit(false)`.
    3. Execute all `INSERT`, `UPDATE`, `DELETE` statements.
    4. If all succeed, call `conn.commit()`.
    5. If any exception occurs, call `conn.rollback()` in the `catch` block.
    6. Finally, set `conn.setAutoCommit(true)` and close the connection in a `finally` block.

This pattern is implemented in methods like `IntakeService.processFromTextArea()`.

## 5. Key Workflow Walkthroughs & Code Locations

#### A) Adding a New Device (Standard Intake)

1. **UI Entry**: `AddAssetDialog.fxml` / `AddAssetDialogController.java`.
2. **Event**: The user clicks the "Save and Process Receipts" button, triggering `handleSave()`.
3. **Business Logic**: `handleSave()` calls `standardIntakeHandler.handleSave()`, which creates and runs a `Task`.
4. **Orchestration**: Inside the `Task`, an `IntakeService` instance is used. `IntakeService.processFromTextArea()`
   begins a database transaction.
5. **Database Operations**:
    - `IntakeService` calls `AssetDAO.addAsset()` to create the `Physical_Assets` record.
    - It then calls `ReceiptEventDAO.addReceiptEvent()` to create the historical intake record.
    - Finally, it calls a private method `createInitialStatus()` to insert the first record into the `Device_Status`
      table.
6. **Completion**: If all steps succeed, the transaction is committed. If any fails, it's rolled back.

#### B) Displaying the Main Device Status Table

1. **UI Entry**: `DeviceStatusTracking.fxml` / `DeviceStatusTrackingController.java`.
2. **Initialization**: The controller initializes `DeviceStatusManager`.
3. **Pagination**: `DeviceStatusManager.resetPagination()` is called. This triggers a background `Task` to run a
   `COUNT(*)` query via `DeviceStatusDAO.fetchPageCount()`. This determines the total number of pages.
4. **Data Fetching**: The `Pagination` control's "page factory" calls `DeviceStatusManager.createPage()`, which in turn
   calls `DeviceStatusDAO.updateTableForPage()`.
5. **The Query**: `updateTableForPage()` builds a complex SQL query that:
    - Finds the **single most recent** `Receipt_Event` for every unique serial number using a subquery.
    - `JOIN`s this result with the `Packages`, `Physical_Assets`, and `Device_Status` tables.
    - Applies all user-selected filters (`serial`, `status`, `category`, etc.).
    - Applies `LIMIT` and `OFFSET` clauses for pagination.
6. **UI Update**: The results are used to populate the `deviceStatusList` `ObservableList`, which automatically updates
   the `TableView`.

## 6. Building & Deployment

### Build Process

The project is built into a single, self-contained "uber JAR" using the **Maven Shade Plugin**. This bundles all
dependencies (JavaFX, HikariCP, etc.) into one executable file.

- **Command**: `mvn clean package`
- **Output**: A file named `ATLegMav-1.0-SNAPSHOT.jar` is created in the `target/` directory.

### End-User Deployment

The application is deployed as a folder containing four key components:

1. **`ATLegMav-1.0-SNAPSHOT.jar`**: The application itself.
2. **`jre/`**: A bundled Java Runtime Environment. This is critical as it guarantees the application runs with the
   correct Java version (JDK 21) without requiring the user to install Java system-wide.
3. **`config.properties`**: The external configuration file. This allows the database URL to be changed without
   rebuilding the application.
4. **`Run Inventory App.bat`**: A startup script that provides a simple double-click experience for users. It copies the
   application to a local directory before running to avoid issues with network drives and then launches the app
   silently in the background.

## 7. Troubleshooting Common Issues

- **`SQLException: No suitable driver found` or `Connection timeout`**:
    - **Cause**: The application cannot reach the database file.
    - **Solution**:
        1. Verify you have network access to the path specified in `config.properties`.
        2. Ensure the path is correct and there are no typos.
        3. Check for firewall issues blocking the connection.

- **`IOException: Resource not found` (e.g., for Excel templates)**:
    - **Cause**: The code is trying to access a file inside the JAR using a `File` path, which is invalid.
    - **Solution**: Resources inside a JAR must be read as an `InputStream`.
        - **Incorrect**: `new File(getClass().getResource("/path/to/file.xlsx").getFile())`
        - **Correct**: `getClass().getResourceAsStream("/path/to/file.xlsx")`

- **The UI Freezes or Becomes Unresponsive**:
    - **Cause**: A long-running operation (database query, file I/O) is being performed on the main JavaFX Application
      Thread.
    - **Solution**: Find the responsible code block and wrap it in a `javafx.concurrent.Task` as described in the
      Concurrency section.

- **SLF4J "Multiple Bindings" Warning in Console**:
    - **Cause**: The `pom.xml` has dependencies on both `logback-classic` and `slf4j-simple`.
    - **Solution**: Open `pom.xml` and remove the `<dependency>` block for `slf4j-simple`. The project is configured to
      use Logback.