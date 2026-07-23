package com.hrms.deploytool.deploy;

/**
 * Indicates the deployment status of a file in the plan (LLD §3).
 */
public enum MappingStatus {
    /** File does not exist on the remote server — will be created. */
    NEW,
    /** File already exists on the remote server — will be overwritten. */
    OVERWRITE,
    /** File matches an exclusion rule and will not be deployed. */
    EXCLUDED
}
