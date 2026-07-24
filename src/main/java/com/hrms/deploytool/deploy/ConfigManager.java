package com.hrms.deploytool.deploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Manages local application configuration persistence and directory structure
 * across different operating systems.
 */
public class ConfigManager {

    private static final String CONFIG_FILE_NAME = "config.properties";
    private static final String KNOWN_HOSTS_FILE_NAME = "known_hosts";
    private static final String LOGS_DIR_NAME = "logs";

    /**
     * Determines and returns the OS-specific application data directory.
     * Creates the directory if it does not exist.
     *
     * @return the File handle of the app data directory
     */
    public static File getAppDataDir() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        File appDataDir;

        if (os.contains("win")) {
            String appDataEnv = System.getenv("APPDATA");
            if (appDataEnv != null) {
                appDataDir = new File(appDataEnv, "HRMSDeployTool");
            } else {
                appDataDir = new File(userHome, "AppData\\Roaming\\HRMSDeployTool");
            }
        } else if (os.contains("mac")) {
            appDataDir = new File(userHome, "Library/Application Support/HRMSDeployTool");
        } else {
            appDataDir = new File(userHome, ".config/hrmsdeploytool");
        }

        if (!appDataDir.exists()) {
            appDataDir.mkdirs();
        }
        return appDataDir;
    }

    /**
     * Returns the File pointer to the SSH known_hosts file.
     * Ensures the file is created if it does not exist.
     *
     * @return the File of the known_hosts file
     */
    public static File getKnownHostsFile() {
        File file = new File(getAppDataDir(), KNOWN_HOSTS_FILE_NAME);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.err.println("Could not create known_hosts file: " + e.getMessage());
            }
        }
        return file;
    }

    /**
     * Returns the File directory where deployment logs are kept.
     * Ensures the folder is created.
     *
     * @return the directory File for logs
     */
    public static File getLogsDir() {
        File logsDir = new File(getAppDataDir(), LOGS_DIR_NAME);
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        return logsDir;
    }

    /**
     * Loads saved configuration settings from properties file.
     *
     * @return loaded Properties object, possibly empty
     */
    public static Properties loadConfig() {
        Properties props = new Properties();
        File configFile = new File(getAppDataDir(), CONFIG_FILE_NAME);
        if (configFile.exists() && configFile.canRead()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("Failed to load config: " + e.getMessage());
            }
        }
        // Set default values if not present
        if (!props.containsKey("port")) props.setProperty("port", "22");
        if (!props.containsKey("username")) props.setProperty("username", "ubuntu");
        if (!props.containsKey("remoteAppRoot")) props.setProperty("remoteAppRoot", "~/HR_MANAGEMENT_SYSTEM");
        
        return props;
    }

    /**
     * Persists settings to the local properties file.
     *
     * @param props configuration settings to save
     */
    public static void saveConfig(Properties props) {
        File configFile = new File(getAppDataDir(), CONFIG_FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "HRMS Deploy Tool Configuration Settings");
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }
}
