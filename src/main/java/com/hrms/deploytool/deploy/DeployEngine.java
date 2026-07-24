package com.hrms.deploytool.deploy;

import com.jcraft.jsch.SftpProgressMonitor;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Manages the deployment lifecycle, orchestrating the file uploads, backups,
 * verification steps, swaps, post-deploy commands, rollback actions, logging,
 * and email notifications.
 */
public class DeployEngine {

    private final Properties profile;
    private final File extractedRoot;
    private final File zipFile;
    private final boolean sendEmailOnSuccess;
    private final List<String> postDeployCommands;
    private final DeployListener listener;

    private String backupFileName;
    private String stagingDirName;
    private SshClient ssh;
    
    // Logging fields
    private final List<String> logLines = new ArrayList<>();
    private final String timestamp;

    public interface DeployListener {
        void onLog(String message);
        void onProgress(double percent, long bytesUploaded, long totalBytes, String currentFile, double speedKbps);
        void onStatusChanged(String status);
    }

    public DeployEngine(Properties profile, File extractedRoot, File zipFile,
                        boolean sendEmailOnSuccess, List<String> postDeployCommands,
                        DeployListener listener) {
        this.profile = profile;
        this.extractedRoot = extractedRoot;
        this.zipFile = zipFile;
        this.sendEmailOnSuccess = sendEmailOnSuccess;
        this.postDeployCommands = postDeployCommands;
        this.listener = listener;
        this.timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
    }

    /**
     * Executes the entire deployment pipeline.
     * Runs in the calling thread (which should be a background thread).
     *
     * @param keyPassphrase private key passphrase if required, kept in memory
     * @param hostKeyCallback callback to trust new host keys
     * @return true if deploy succeeded, false if aborted or failed
     */
    public boolean runDeploy(String keyPassphrase, SshClient.HostKeyCallback hostKeyCallback) {
        log("Deployment started at " + new Date());
        log("Zip file: " + zipFile.getName());
        log("Extracted root: " + extractedRoot.getAbsolutePath());

        ssh = new SshClient();
        boolean success = false;
        boolean rollbackNeeded = false;
        
        String host = profile.getProperty("host");
        int port = Integer.parseInt(profile.getProperty("port", "22"));
        String username = profile.getProperty("username");
        String keyPath = profile.getProperty("privateKeyPath");
        String remoteAppRoot = profile.getProperty("remoteAppRoot");

        try {
            listener.onStatusChanged("CONNECTING");
            log("Connecting to " + username + "@" + host + ":" + port + "...");
            ssh.connect(host, port, username, keyPath, keyPassphrase, hostKeyCallback);
            log("SSH Connection established successfully.");

            listener.onStatusChanged("STAGING_UPLOADS");
            stagingDirName = "~/.hrms_deploy_staging_" + timestamp;
            log("Creating remote staging directory: " + stagingDirName);
            ssh.createRemoteDir(stagingDirName);

            // Scan files to upload
            List<File> filesToUpload = new ArrayList<>();
            long totalBytes = scanFilesForUpload(extractedRoot, filesToUpload);
            log("Files to upload: " + filesToUpload.size() + " (" + formatSize(totalBytes) + ")");

            if (filesToUpload.isEmpty()) {
                log("No files to deploy. Staging skipped.");
            } else {
                uploadFiles(filesToUpload, totalBytes);
            }

            listener.onStatusChanged("BACKING_UP");
            backupFileName = "HRMS_backup_" + timestamp + ".tar.gz";
            log("Creating remote pre-deployment backup: " + backupFileName + " in home directory...");
            createBackup(remoteAppRoot);

            listener.onStatusChanged("APPLYING");
            rollbackNeeded = true; // Any failure during applying can be rolled back
            applyStaging(remoteAppRoot);

            listener.onStatusChanged("RUNNING_POST_DEPLOY");
            runPostDeployCommands();

            if (sendEmailOnSuccess) {
                listener.onStatusChanged("SENDING_NOTIFICATION");
                sendReleaseEmail();
            }

            // Append VM deploy summary
            appendRemoteSummary(remoteAppRoot);

            log("Deployment completed successfully!");
            listener.onStatusChanged("SUCCESS");
            success = true;
            rollbackNeeded = false;

        } catch (Exception e) {
            log("[ERROR] Deployment failed: " + e.getMessage());
            e.printStackTrace();
            listener.onStatusChanged("FAILED");

            if (rollbackNeeded) {
                log("[WARNING] Initiating automatic rollback...");
                performRollback(remoteAppRoot);
            }
        } finally {
            ssh.disconnect();
            writeLocalLog(success ? "SUCCESS" : (rollbackNeeded ? "ROLLED BACK" : "FAILED"));
        }

        return success;
    }

    /**
     * Triggers manual rollback using the specified backup file.
     */
    public boolean performManualRollback(String keyPassphrase, SshClient.HostKeyCallback hostKeyCallback, String backupName) {
        log("Manual Rollback started at " + new Date());
        ssh = new SshClient();
        boolean success = false;
        
        String host = profile.getProperty("host");
        int port = Integer.parseInt(profile.getProperty("port", "22"));
        String username = profile.getProperty("username");
        String keyPath = profile.getProperty("privateKeyPath");
        String remoteAppRoot = profile.getProperty("remoteAppRoot");

        try {
            listener.onStatusChanged("CONNECTING");
            log("Connecting to " + username + "@" + host + ":" + port + "...");
            ssh.connect(host, port, username, keyPath, keyPassphrase, hostKeyCallback);
            
            listener.onStatusChanged("ROLLING_BACK");
            log("Restoring from backup " + backupName + "...");
            
            String command = "tar -xzf ~/" + backupName + " -C " + remoteAppRoot;
            log("Running remote command: " + command);
            int exitCode = ssh.executeCommand(command, this::log, 120000); // 2 min timeout
            if (exitCode == 0) {
                log("Rollback completed successfully.");
                listener.onStatusChanged("ROLLED_BACK");
                success = true;
            } else {
                log("[ERROR] Rollback failed with exit code: " + exitCode);
                listener.onStatusChanged("ROLLBACK_FAILED");
            }
        } catch (Exception e) {
            log("[ERROR] Manual Rollback failed: " + e.getMessage());
            listener.onStatusChanged("ROLLBACK_FAILED");
        } finally {
            ssh.disconnect();
            writeLocalLog(success ? "ROLLED_BACK" : "ROLLBACK_FAILED");
        }
        return success;
    }

    private void performRollback(String remoteAppRoot) {
        try {
            listener.onStatusChanged("ROLLING_BACK");
            log("Restoring from pre-deployment backup ~/" + backupFileName + "...");
            String command = "tar -xzf ~/" + backupFileName + " -C " + remoteAppRoot;
            int exitCode = ssh.executeCommand(command, this::log, 180000); // 3 min timeout
            if (exitCode == 0) {
                log("Rollback restored VM to previous state.");
                listener.onStatusChanged("ROLLED_BACK");
            } else {
                log("[ERROR] Rollback extraction failed with exit code: " + exitCode);
                listener.onStatusChanged("FAILED");
            }
        } catch (Exception e) {
            log("[ERROR] Failed to run rollback command: " + e.getMessage());
            listener.onStatusChanged("FAILED");
        }
    }

    private void createBackup(String remoteAppRoot) throws Exception {
        log("Verifying remote app directory exists...");
        ssh.createRemoteDir(remoteAppRoot);

        // Run backup command if remote app directory has files.
        // We exclude node_modules, uploads, .git and logs directories to save space.
        String command = "if [ -d " + remoteAppRoot + " ] && [ -n \"$(ls -A " + remoteAppRoot + ")\" ]; then "
                + "tar -czf ~/" + backupFileName + " -C " + remoteAppRoot 
                + " --exclude=node_modules --exclude=uploads --exclude=.git --exclude=logs . ; "
                + "else touch ~/empty_backup_dummy && tar -czf ~/" + backupFileName + " -T /dev/null && rm ~/empty_backup_dummy; fi";
        
        log("Running remote backup command: " + command);
        int exitCode = ssh.executeCommand(command, this::log, 300000); // 5 min timeout
        if (exitCode != 0) {
            throw new IOException("Remote backup command failed with exit code " + exitCode);
        }

        // Verify size
        long backupSize = ssh.getRemoteFileSize("~/" + backupFileName);
        if (backupSize == 0) {
            throw new IOException("Pre-deployment backup verification failed: backup file is empty (0 bytes).");
        }
        log("Backup verified. Size: " + formatSize(backupSize));
    }

    private void applyStaging(String remoteAppRoot) throws Exception {
        log("Moving files from staging into place: " + remoteAppRoot);
        // Ensure remote app directory is created
        ssh.createRemoteDir(remoteAppRoot);

        // Copy and overwrite
        String copyCmd = "cp -rf " + stagingDirName + "/* " + remoteAppRoot + "/";
        log("Running remote apply: " + copyCmd);
        int exitCode = ssh.executeCommand(copyCmd, this::log, 120000);
        if (exitCode != 0) {
            throw new IOException("Apply staging command failed with exit code " + exitCode);
        }

        // Clean up remote staging dir
        log("Cleaning up remote staging directory: " + stagingDirName);
        String cleanupCmd = "rm -rf " + stagingDirName;
        ssh.executeCommand(cleanupCmd, this::log, 30000);
    }

    private void runPostDeployCommands() throws Exception {
        if (postDeployCommands == null || postDeployCommands.isEmpty()) {
            log("No post-deploy commands configured.");
            return;
        }

        log("Executing post-deployment commands...");
        for (String cmd : postDeployCommands) {
            log("Executing remote: " + cmd);
            long timeout = 600000; // 10 minutes timeout default (e.g. npm install)
            int exitCode = ssh.executeCommand(cmd, this::log, timeout);
            log("Exit code: " + exitCode);
            if (exitCode != 0) {
                log("[WARNING] Post-deploy command failed with exit code " + exitCode);
            }
        }
    }

    private void sendReleaseEmail() {
        try {
            log("Fetching release notes from Git on VM...");
            String remoteAppRoot = profile.getProperty("remoteAppRoot");
            StringBuilder gitNotes = new StringBuilder();
            
            // Run git log in remote app root
            String gitCmd = "cd " + remoteAppRoot + " && git log -n 5 --oneline";
            int exitCode = ssh.executeCommand(gitCmd, line -> gitNotes.append(line).append("\n"), 10000);
            
            String notesText = (exitCode == 0 && gitNotes.length() > 0) ? gitNotes.toString() 
                    : "No release notes available (Git command failed or no commits found).";

            String subject = "HRMS Release Update — " + timestamp;
            String body = "HRMS Deployment completed successfully!\n\n"
                    + "Deployment Details:\n"
                    + "-------------------\n"
                    + "Timestamp: " + timestamp + "\n"
                    + "Target Host: " + profile.getProperty("host") + "\n"
                    + "App Directory: " + remoteAppRoot + "\n"
                    + "Zip File: " + zipFile.getName() + "\n\n"
                    + "Recent Release Notes / Commits:\n"
                    + "-----------------------------\n"
                    + notesText + "\n\n"
                    + "Regards,\nHRMS Deployment Manager";

            log("Sending release email notification...");
            MailSender.sendEmail(profile, subject, body);
            log("Email notification sent successfully.");
        } catch (Exception e) {
            log("[WARNING] Failed to send release email: " + e.getMessage());
        }
    }

    private void appendRemoteSummary(String remoteAppRoot) {
        try {
            log("Appending summary line to remote deployment log on VM...");
            String logMsg = timestamp + " | " + System.getProperty("user.name") + " | " + zipFile.getName() + " | SUCCESS";
            String cmd = "mkdir -p ~/logs && echo \"" + logMsg + "\" >> ~/logs/deploy.log";
            ssh.executeCommand(cmd, this::log, 10000);
        } catch (Exception e) {
            log("[WARNING] Could not append remote summary log: " + e.getMessage());
        }
    }

    private long scanFilesForUpload(File dir, List<File> fileList) {
        ExclusionMatcher exclusionMatcher = new ExclusionMatcher();
        long totalBytes = 0;
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                Path relPath = extractedRoot.toPath().relativize(file.toPath());
                if (exclusionMatcher.isExcluded(relPath)) {
                    continue; // Skip excluded items
                }
                if (file.isDirectory()) {
                    totalBytes += scanFilesForUpload(file, fileList);
                } else {
                    fileList.add(file);
                    totalBytes += file.length();
                }
            }
        }
        return totalBytes;
    }

    private void uploadFiles(List<File> fileList, long totalBytes) throws Exception {
        log("Uploading files to remote staging...");
        
        final long[] bytesUploaded = {0};
        final long[] lastNotificationTime = {System.currentTimeMillis()};
        final long[] lastBytesUploaded = {0};

        for (int i = 0; i < fileList.size(); i++) {
            File localFile = fileList.get(i);
            Path relPath = extractedRoot.toPath().relativize(localFile.toPath());
            
            // Format remote target path
            String remoteTarget = stagingDirName + "/" + relPath.toString().replace('\\', '/');
            
            // Ensure remote directory exists
            String remoteParent = remoteTarget.substring(0, remoteTarget.lastIndexOf('/'));
            ssh.createRemoteDir(remoteParent);

            final String fname = localFile.getName();
            final int fileIndex = i + 1;
            final int totalFiles = fileList.size();

            // SftpProgressMonitor to track byte progress
            SftpProgressMonitor sftpMonitor = new SftpProgressMonitor() {
                private long fileMax = 0;
                private long fileUploaded = 0;

                @Override
                public void init(int op, String src, String dest, long max) {
                    this.fileMax = max;
                    this.fileUploaded = 0;
                }

                @Override
                public boolean count(long count) {
                    this.fileUploaded += count;
                    bytesUploaded[0] += count;

                    long now = System.currentTimeMillis();
                    long elapsed = now - lastNotificationTime[0];
                    if (elapsed >= 500) {
                        double percent = (double) bytesUploaded[0] / totalBytes;
                        double speedKbps = ((double) (bytesUploaded[0] - lastBytesUploaded[0]) / 1024.0) / (elapsed / 1000.0);
                        
                        listener.onProgress(percent, bytesUploaded[0], totalBytes, fname + " (" + fileIndex + "/" + totalFiles + ")", speedKbps);
                        
                        lastNotificationTime[0] = now;
                        lastBytesUploaded[0] = bytesUploaded[0];
                    }
                    return true;
                }

                @Override
                public void end() {
                    // Update progress when file finishes
                    double percent = (double) bytesUploaded[0] / totalBytes;
                    listener.onProgress(percent, bytesUploaded[0], totalBytes, fname + " (" + fileIndex + "/" + totalFiles + ")", 0);
                }
            };

            ssh.uploadFile(localFile.getAbsolutePath(), remoteTarget, sftpMonitor);
            
            // Preserve file permissions: check execute bit
            if (localFile.canExecute()) {
                ssh.chmod(0755, remoteTarget);
            } else {
                ssh.chmod(0644, remoteTarget);
            }
        }
    }

    private void log(String message) {
        logLines.add(message);
        listener.onLog(message);
    }

    private void writeLocalLog(String status) {
        File logFile = new File(ConfigManager.getLogsDir(), "deploy_" + timestamp + ".log");
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile))) {
            writer.println("==================================================");
            writer.println("HRMS DEPLOYMENT LOG - STATUS: " + status);
            writer.println("Timestamp: " + timestamp);
            writer.println("==================================================");
            for (String line : logLines) {
                writer.println(line);
            }
        } catch (IOException e) {
            System.err.println("Failed to write local deployment log file: " + e.getMessage());
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
