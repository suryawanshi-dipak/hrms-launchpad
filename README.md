# HRMS Deploy Tool (JavaFX UI Prototype)

This repository contains the interactive UI prototype for the HRMS Deploy Tool. It has been built using **Java 17** and **JavaFX 21** to provide a cross-platform, natively compiled desktop application experience.

## Features
- **Modern Dark Theme**: Custom CSS styling that precisely matches the intended design system.
- **Interactive File Trees**: Custom-rendered JavaFX TreeView cells showing diff statuses (e.g. `[NEW]`, `[OVERWRITE]`).
- **Interactive Prototyping**: Contains "Demo" controls to easily step through the deployment flow (File Validation -> Workspace Planning) before backend logic is hooked up.

## Prerequisites
- Java Development Kit (JDK) 17 or higher.

## How to Run

This project uses the Maven Wrapper, meaning you do not need to install Maven globally to run it.

Open your terminal in the project root directory and run:

**On Windows:**
```cmd
.\mvnw.cmd clean javafx:run
```

**On macOS / Linux:**
```bash
./mvnw clean javafx:run
```

## Project Structure
- `src/main/java/com/hrms/deploytool/ui`: Contains all JavaFX view controllers and layouts.
- `src/main/resources/com/hrms/deploytool/styles.css`: The central stylesheet for the dark theme.
