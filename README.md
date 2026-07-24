# HRMS Deploy Tool

An automated JavaFX desktop application designed to streamline and secure deployment of HRMS updates to production environments over SSH/SFTP with pre-deployment backups, staging directories, log streaming, and rollback mechanisms.

## Core Features
* **Atomic Staging Deployments**: Updates are uploaded to a remote staging directory (`~/.hrms_deploy_staging_<ts>`) before moving them into place, preventing partial updates.
* **Pre-deployment VM Backups**: Compresses and verifies the active remote directory prior to overwrite, saving backups as `~/HRMS_backup_<timestamp>.tar.gz`.
* **Dynamic File Comparison**: Queries the VM's file tree and compares it to the local zip package to determine `NEW` vs `OVERWRITE` file statuses in real-time.
* **Sensitive File Exclusions**: Automatically flags and skips deployment of sensitive/heavy folders like `node_modules/`, `.env`, `backend/uploads/`, and logs.
* **Real-time Log Streaming**: Streams SSH command outputs and build feedback in real-time directly inside the UI.
* **SMTP Release Notifications**: Announce successful deployments to your dev/ops team, complete with statistics and recent Git commits.
* **One-Click Rollbacks**: Automated rollback on failures, and manual rollback from the UI for previous deploys.
* **Security First**: Passphrases and SSH key contents are held in memory only and never written to configuration properties or logs.

---

## Setup & Running Instructions

### Prerequisites
* **Java Development Kit (JDK)**: Version 17 or higher.
* **Maven**: Built-in wrapper `mvnw` is included in the project.

### Configuration
1. **Launch the application** (see run commands below).
2. **SMTP Settings**: Click the **Gear (Settings)** icon to configure SMTP host, port, authentication credentials, and notifications recipient lists.
3. **Connection Profile**: Choose a validated update package, then click **Show Connection** to enter your host IP, username, private key path, and remote root path. Click **Save Profile** to persist these credentials locally.

### How to Run

**On Windows:**
```cmd
.\mvnw.cmd javafx:run
```

**On macOS / Linux:**
```bash
chmod +x mvnw
./mvnw javafx:run
```

---

## File Structure & Directory Layout
* **App Data Folder**: Config and host keys are saved under `%APPDATA%\HRMSDeployTool` (Windows) or OS-specific equivalent.
* **Local Run Logs**: Details of files, exit codes, and timestamps are saved per deployment run under `%APPDATA%\HRMSDeployTool\logs\deploy_<timestamp>.log`.
* **Remote Log Summary**: A deployment summary line is appended to `~/logs/deploy.log` on the target VM.

