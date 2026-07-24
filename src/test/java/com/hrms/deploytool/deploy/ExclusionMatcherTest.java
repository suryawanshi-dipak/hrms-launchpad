package com.hrms.deploytool.deploy;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

public class ExclusionMatcherTest {

    @Test
    public void testDefaultExclusions() {
        ExclusionMatcher matcher = new ExclusionMatcher();

        // Should exclude node_modules
        assertTrue(matcher.isExcluded(Paths.get("node_modules/index.js")));
        assertTrue(matcher.isExcluded(Paths.get("backend/node_modules/package.json")));

        // Should exclude .env files
        assertTrue(matcher.isExcluded(Paths.get(".env")));
        assertTrue(matcher.isExcluded(Paths.get("backend/.env")));

        // Should exclude key files
        assertTrue(matcher.isExcluded(Paths.get("ssh-key.key")));
        assertTrue(matcher.isExcluded(Paths.get("backend/src/prod-key.key")));

        // Should exclude uploads directory
        assertTrue(matcher.isExcluded(Paths.get("backend/uploads/image.png")));
        assertTrue(matcher.isExcluded(Paths.get("backend/src/uploads/avatar.jpg")));

        // Should exclude logs
        assertTrue(matcher.isExcluded(Paths.get("logs/app.log")));
        assertTrue(matcher.isExcluded(Paths.get("backend/logs/server.log")));

        // Should exclude .git
        assertTrue(matcher.isExcluded(Paths.get(".git/config")));

        // Should NOT exclude standard files
        assertFalse(matcher.isExcluded(Paths.get("backend/server.js")));
        assertFalse(matcher.isExcluded(Paths.get("frontend/package.json")));
        assertFalse(matcher.isExcluded(Paths.get("README.md")));
    }
}
