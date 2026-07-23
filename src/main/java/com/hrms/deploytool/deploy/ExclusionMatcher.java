package com.hrms.deploytool.deploy;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches file paths against exclusion glob patterns to determine which files
 * should be skipped during deployment (LLD §5).
 *
 * <p>Default exclusions prevent uploading sensitive, generated, or large
 * directories that should not be overwritten on the production server.</p>
 */
public class ExclusionMatcher {

    /** Default exclusion glob patterns per LLD specification. */
    private static final List<String> DEFAULT_GLOBS = List.of(
        "node_modules",
        "node_modules/**",
        "**/node_modules",
        "**/node_modules/**",
        ".env",
        "**/.env",
        "**/uploads",
        "**/uploads/**",
        "*.key",
        "**/*.key",
        ".git",
        ".git/**",
        "**/.git",
        "**/.git/**",
        "logs",
        "logs/**",
        "**/logs",
        "**/logs/**"
    );

    private final List<PathMatcher> matchers;
    private final List<String> patterns;

    /**
     * Creates an ExclusionMatcher with the default HRMS exclusion patterns.
     */
    public ExclusionMatcher() {
        this(DEFAULT_GLOBS);
    }

    /**
     * Creates an ExclusionMatcher with custom glob patterns.
     *
     * @param globs list of glob patterns to exclude
     */
    public ExclusionMatcher(List<String> globs) {
        this.patterns = new ArrayList<>(globs);
        this.matchers = new ArrayList<>();
        for (String glob : globs) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
    }

    /**
     * Checks if a file path matches any exclusion pattern.
     *
     * @param relativePath the file path relative to the extraction root
     * @return true if the file should be excluded from deployment
     */
    public boolean isExcluded(Path relativePath) {
        if (relativePath == null) return false;

        // Check the path itself and each component
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(relativePath)) {
                return true;
            }
        }

        // Also check just the filename for simple patterns like ".env"
        Path fileName = relativePath.getFileName();
        if (fileName != null) {
            for (PathMatcher matcher : matchers) {
                if (matcher.matches(fileName)) {
                    return true;
                }
            }
        }

        // Check if any ancestor directory is excluded (e.g., node_modules/foo/bar.js)
        for (int i = 0; i < relativePath.getNameCount(); i++) {
            Path segment = relativePath.getName(i);
            String segName = segment.toString();
            if (segName.equals("node_modules") || segName.equals(".git")
                || segName.equals("uploads") || segName.equals("logs")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the list of active exclusion patterns.
     *
     * @return unmodifiable list of glob pattern strings
     */
    public List<String> getPatterns() {
        return List.copyOf(patterns);
    }
}
