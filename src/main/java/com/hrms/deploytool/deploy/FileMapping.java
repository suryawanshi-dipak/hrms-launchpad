package com.hrms.deploytool.deploy;

import java.nio.file.Path;

/**
 * Represents a single file mapping between the local extracted archive
 * and the remote deployment target (LLD §3).
 *
 * @param localPath     path of the file relative to the extraction root
 * @param remoteRelPath the corresponding relative path on the remote server
 * @param sizeBytes     file size in bytes
 * @param status        the deployment mapping status
 */
public record FileMapping(
    Path localPath,
    String remoteRelPath,
    long sizeBytes,
    MappingStatus status
) {}
