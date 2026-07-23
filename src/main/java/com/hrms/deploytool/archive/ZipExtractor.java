package com.hrms.deploytool.archive;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts a validated ZIP file to a temporary directory.
 * Handles wrapper folder detection (LLD §6) and provides extraction statistics.
 *
 * This class should only be called AFTER {@link ZipValidator#validate} returns a
 * successful result.
 */
public class ZipExtractor {

    /**
     * Result of a ZIP extraction operation.
     *
     * @param extractedRoot     effective root directory (unwrapped if wrapper detected)
     * @param rawTempDir        the actual temp directory created (for cleanup)
     * @param wrapperFolderName name of the wrapper folder if detected, null otherwise
     * @param fileCount         number of files extracted
     * @param totalBytes        total size of extracted files in bytes
     */
    public record ExtractionResult(
        File extractedRoot,
        File rawTempDir,
        String wrapperFolderName,
        int fileCount,
        long totalBytes
    ) {}

    /**
     * Extracts a ZIP file to a temporary directory.
     *
     * <p>After extraction, performs wrapper folder detection: if the zip contains a
     * single top-level directory (e.g., {@code hrms_update/backend/...}), the returned
     * {@code extractedRoot} points to that directory's contents rather than the raw
     * temp dir.</p>
     *
     * @param zipFile      the ZIP file to extract (must have passed validation)
     * @param stepCallback optional callback invoked when extraction begins (step index provided)
     * @return ExtractionResult with paths, stats, and wrapper folder info
     * @throws IOException if extraction fails
     */
    public static ExtractionResult extract(File zipFile, Consumer<Integer> stepCallback) throws IOException {
        if (stepCallback != null) stepCallback.accept(0);

        Path tempDir = Files.createTempDirectory("hrms-deploy-" + System.currentTimeMillis());
        File destDir = tempDir.toFile();

        // Register for cleanup on JVM shutdown as a safety net
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(destDir)));

        int fileCount = 0;
        long totalBytes = 0;

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
                    fileCount++;
                    totalBytes += entry.getSize();
                }
            }
        } catch (Exception e) {
            deleteRecursively(destDir);
            throw new IOException("Extraction failed", e);
        }

        // Wrapper folder detection
        String wrapperName = detectWrapperFolder(destDir);
        File effectiveRoot = destDir;

        if (wrapperName != null) {
            File wrapperDir = new File(destDir, wrapperName);
            if (wrapperDir.isDirectory()) {
                effectiveRoot = wrapperDir;
            }
        }

        return new ExtractionResult(effectiveRoot, destDir, wrapperName, fileCount, totalBytes);
    }

    /**
     * Detects if the extracted content has a single top-level wrapper directory
     * containing the actual application files (backend/, frontend/, etc.).
     *
     * <p>Common pattern: zips named {@code hrms_update2.zip} containing
     * {@code hrms_update2/backend/...} — the {@code hrms_update2/} dir is the wrapper.</p>
     *
     * @param extractedDir the root directory of the extraction
     * @return the wrapper folder name if detected, null otherwise
     */
    private static String detectWrapperFolder(File extractedDir) {
        File[] topLevelFiles = extractedDir.listFiles();
        if (topLevelFiles == null || topLevelFiles.length != 1) {
            return null; // Not a single wrapper
        }

        File candidate = topLevelFiles[0];
        if (!candidate.isDirectory()) {
            return null; // Single file, not a wrapper
        }

        // Check if the candidate contains known HRMS structure entries
        Set<String> knownDirs = Set.of("backend", "frontend");
        File[] innerFiles = candidate.listFiles();
        if (innerFiles != null) {
            for (File inner : innerFiles) {
                if (inner.isDirectory() && knownDirs.contains(inner.getName().toLowerCase())) {
                    return candidate.getName();
                }
            }
        }

        // Even without backend/frontend, a single top-level dir is treated as a wrapper
        // if it has any children (the zip is structured as wrapper/contents)
        if (innerFiles != null && innerFiles.length > 0) {
            return candidate.getName();
        }

        return null;
    }

    /**
     * Recursively deletes a directory and all its contents.
     * Used for cleanup of temporary extraction directories.
     *
     * @param file the file or directory to delete
     */
    public static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
