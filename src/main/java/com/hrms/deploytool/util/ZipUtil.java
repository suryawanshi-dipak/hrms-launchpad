package com.hrms.deploytool.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipUtil {

    public static class ZipStats {
        public final File extractedDir;
        public final int fileCount;
        public final long totalBytes;

        public ZipStats(File extractedDir, int fileCount, long totalBytes) {
            this.extractedDir = extractedDir;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
        }
    }

    /**
     * Extracts a ZIP file to a temporary directory with background validation steps.
     * @param zipFile The ZIP file to extract.
     * @param stepCallback A callback invoked when each validation step is started (0 to 3).
     * @return ZipStats containing the extracted directory, file count, and size.
     * @throws IOException If an IO error occurs or validation fails.
     */
    public static ZipStats extractToTempDir(File zipFile, Consumer<Integer> stepCallback) throws IOException {
        // Step 0: Reading archive
        if (stepCallback != null) stepCallback.accept(0);
        sleepForEffect();

        int fileCount = 0;
        long totalBytes = 0;

        // Pre-flight check
        // Step 1: Checking for corruption
        if (stepCallback != null) stepCallback.accept(1);
        sleepForEffect();

        // Step 2: Scanning for unsafe paths
        if (stepCallback != null) stepCallback.accept(2);
        sleepForEffect();

        // Check if zip opens successfully and count files
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            Path checkDir = Files.createTempDirectory("hrms_deploy_tool_check");
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    fileCount++;
                    totalBytes += entry.getSize();
                }
                
                // Prevent Zip Slip vulnerability (scan path)
                File testDestFile = new File(checkDir.toFile(), entry.getName());
                String destDirPath = checkDir.toFile().getCanonicalPath();
                String destFilePath = testDestFile.getCanonicalPath();
                if (!destFilePath.startsWith(destDirPath + File.separator)) {
                    throw new IOException("Security violation: Zip Slip detected on entry " + entry.getName());
                }
            }
        }

        // Step 3: Extracting files
        if (stepCallback != null) stepCallback.accept(3);
        sleepForEffect();

        Path tempDir = Files.createTempDirectory("hrms_deploy_tool_" + System.currentTimeMillis());
        File destDir = tempDir.toFile();

        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File destFile = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    destFile.getParentFile().mkdirs();
                    try (InputStream is = zip.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = is.read(buffer)) >= 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
            }
        }

        return new ZipStats(destDir, fileCount, totalBytes);
    }

    private static void sleepForEffect() {
        try {
            Thread.sleep(600); // Same delay as original timeline mock to allow UI reading
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
