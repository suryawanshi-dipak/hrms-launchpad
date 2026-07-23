package com.hrms.deploytool.archive;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;

/**
 * Validates a ZIP file before extraction, performing all pre-flight checks
 * required by the deployment safety specification (LLD §6).
 *
 * Checks are performed in order:
 * 1. File exists, readable, size > 0, .zip extension
 * 2. Corruption detection via zip4j
 * 3. Encryption detection via zip4j
 * 4. Zip Slip path traversal scan
 * 5. Zip bomb guard (entry count + uncompressed size caps)
 * 6. Structure sanity (warn if no backend/frontend found)
 */
public class ZipValidator {

    /** Maximum number of entries allowed in a zip (guard against zip bombs). */
    private static final int MAX_ENTRY_COUNT = 50_000;

    /** Maximum total uncompressed size in bytes (2 GB). */
    private static final long MAX_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024;

    /** Known top-level directories/files that indicate a valid HRMS structure. */
    private static final Set<String> KNOWN_ROOT_ENTRIES = Set.of(
        "backend", "frontend", "backend/", "frontend/"
    );

    /**
     * Result of a ZIP validation operation.
     *
     * @param valid            true if the zip passed all checks
     * @param errorMessage     human-readable error if invalid, null if valid
     * @param structureWarning true if no backend/frontend found at top level
     * @param structureWarningMsg warning message text, null if no warning
     * @param entryCount       total number of file entries in the zip
     * @param totalUncompressedBytes total uncompressed size of all entries
     */
    public record ValidationResult(
        boolean valid,
        String errorMessage,
        boolean structureWarning,
        String structureWarningMsg,
        int entryCount,
        long totalUncompressedBytes
    ) {
        /** Creates a successful result. */
        public static ValidationResult success(boolean structWarn, String structMsg,
                                                int count, long bytes) {
            return new ValidationResult(true, null, structWarn, structMsg, count, bytes);
        }

        /** Creates a failed result. */
        public static ValidationResult failure(String error) {
            return new ValidationResult(false, error, false, null, 0, 0);
        }
    }

    /**
     * Validates a ZIP file through all safety checks.
     *
     * @param zipFile      the ZIP file to validate
     * @param stepCallback optional callback invoked with step index (0–4) as each check begins
     * @return ValidationResult indicating pass/fail with details
     */
    public static ValidationResult validate(File zipFile, Consumer<Integer> stepCallback) {
        // Step 0: Reading archive — basic file checks
        if (stepCallback != null) stepCallback.accept(0);

        if (zipFile == null || !zipFile.exists()) {
            return ValidationResult.failure("File does not exist.");
        }
        if (!zipFile.canRead()) {
            return ValidationResult.failure("File is not readable.");
        }
        if (zipFile.length() == 0) {
            return ValidationResult.failure("File is empty (0 bytes).");
        }
        if (!zipFile.getName().toLowerCase().endsWith(".zip")) {
            return ValidationResult.failure("File does not have a .zip extension.");
        }

        // Step 1: Checking for corruption (via zip4j)
        if (stepCallback != null) stepCallback.accept(1);

        try {
            ZipFile z4j = new ZipFile(zipFile);
            if (!z4j.isValidZipFile()) {
                return ValidationResult.failure(
                    "Archive is corrupted — the file could not be read as a valid ZIP."
                );
            }
        } catch (Exception e) {
            return ValidationResult.failure("Archive is corrupted: " + e.getMessage());
        }

        // Step 2: Checking for encryption (via zip4j)
        if (stepCallback != null) stepCallback.accept(2);

        try {
            ZipFile z4j = new ZipFile(zipFile);
            if (z4j.isEncrypted()) {
                return ValidationResult.failure(
                    "Password-protected archives are not supported. "
                    + "Please provide an unencrypted ZIP file."
                );
            }
        } catch (Exception e) {
            return ValidationResult.failure("Could not check encryption status: " + e.getMessage());
        }

        // Step 3: Scanning for unsafe paths (Zip Slip) + counting entries
        if (stepCallback != null) stepCallback.accept(3);

        int fileCount = 0;
        long totalBytes = 0;
        Set<String> topLevelEntries = new HashSet<>();

        try (java.util.zip.ZipFile javaZip = new java.util.zip.ZipFile(zipFile)) {
            Path syntheticRoot = Files.createTempDirectory("hrms_zipslip_check_");
            try {
                Enumeration<? extends ZipEntry> entries = javaZip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();

                    // Reject absolute paths
                    if (name.startsWith("/") || name.startsWith("\\")) {
                        return ValidationResult.failure(
                            "Security violation: absolute path detected in entry '" + name + "'."
                        );
                    }

                    // Reject path traversal (.. components)
                    if (name.contains("..")) {
                        return ValidationResult.failure(
                            "Security violation: path traversal detected in entry '" + name + "'."
                        );
                    }

                    // Normalized Zip Slip check
                    Path resolved = syntheticRoot.resolve(name).normalize();
                    if (!resolved.startsWith(syntheticRoot)) {
                        return ValidationResult.failure(
                            "Security violation: Zip Slip detected on entry '" + name + "'."
                        );
                    }

                    // Track top-level entries for structure check
                    String topLevel = name.contains("/") ? name.substring(0, name.indexOf('/')) : name;
                    topLevelEntries.add(topLevel);

                    if (!entry.isDirectory()) {
                        fileCount++;
                        totalBytes += entry.getSize();

                        // Zip bomb: entry count check
                        if (fileCount > MAX_ENTRY_COUNT) {
                            return ValidationResult.failure(
                                "Archive contains more than " + MAX_ENTRY_COUNT
                                + " entries — possible zip bomb. Aborting."
                            );
                        }
                        // Zip bomb: total size check
                        if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                            return ValidationResult.failure(
                                "Archive uncompressed size exceeds 2 GB — possible zip bomb. Aborting."
                            );
                        }
                    }
                }
            } finally {
                // Clean up the synthetic check directory
                Files.deleteIfExists(syntheticRoot);
            }
        } catch (IOException e) {
            return ValidationResult.failure("Failed to scan archive entries: " + e.getMessage());
        }

        // Step 4: Structure sanity check
        if (stepCallback != null) stepCallback.accept(4);

        boolean structureWarning = false;
        String structureMsg = null;

        // Check if any top-level entry matches known structure.
        // If a wrapper folder exists (single top-level dir), check inside it.
        boolean hasKnownStructure = topLevelEntries.stream()
            .anyMatch(e -> KNOWN_ROOT_ENTRIES.contains(e) || KNOWN_ROOT_ENTRIES.contains(e + "/"));

        if (!hasKnownStructure && topLevelEntries.size() == 1) {
            // Might be a wrapper folder — check if its children have backend/frontend
            String wrapper = topLevelEntries.iterator().next();
            try (java.util.zip.ZipFile javaZip = new java.util.zip.ZipFile(zipFile)) {
                Enumeration<? extends ZipEntry> entries = javaZip.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(wrapper + "/")) {
                        String inner = name.substring(wrapper.length() + 1);
                        String innerTop = inner.contains("/") ? inner.substring(0, inner.indexOf('/')) : inner;
                        if (KNOWN_ROOT_ENTRIES.contains(innerTop) || KNOWN_ROOT_ENTRIES.contains(innerTop + "/")) {
                            hasKnownStructure = true;
                            break;
                        }
                    }
                }
            } catch (IOException ignored) {
                // Structure check is non-blocking
            }
        }

        if (!hasKnownStructure) {
            structureWarning = true;
            structureMsg = "No 'backend/' or 'frontend/' directory found at the top level. "
                + "This archive may not be a valid HRMS update package.";
        }

        return ValidationResult.success(structureWarning, structureMsg, fileCount, totalBytes);
    }
}
