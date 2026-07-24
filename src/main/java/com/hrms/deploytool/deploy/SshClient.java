package com.hrms.deploytool.deploy;

import com.jcraft.jsch.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Handles all SSH and SFTP communication using JSch.
 * Implements AutoCloseable to ensure sessions and channels are properly shut down.
 */
public class SshClient implements AutoCloseable {

    private Session session;
    private ChannelSftp sftp;

    /** Callback interface for Host Key trust verification. */
    public interface HostKeyCallback {
        /**
         * Invoked when the server key is untrusted.
         *
         * @param message verification question from JSch
         * @return true if the user trusts the key, false otherwise
         */
        boolean approve(String message);
    }

    /** UserInfo adapter for JSch to handle key passphrase and TOFU host key confirmations. */
    private static class SshUserInfo implements UserInfo {
        private final String passphrase;
        private final HostKeyCallback callback;

        public SshUserInfo(String passphrase, HostKeyCallback callback) {
            this.passphrase = passphrase;
            this.callback = callback;
        }

        @Override
        public String getPassphrase() { return passphrase; }
        @Override
        public String getPassword() { return null; }
        @Override
        public boolean promptPassword(String message) { return false; }
        @Override
        public boolean promptPassphrase(String message) { return true; }
        @Override
        public boolean promptYesNo(String message) {
            if (callback != null) {
                return callback.approve(message);
            }
            return false;
        }
        @Override
        public void showMessage(String message) {
            System.out.println("SSH Message: " + message);
        }
    }

    /**
     * Establishes an SSH connection and initializes SFTP channel.
     */
    public void connect(String host, int port, String username, String keyPath,
                        String passphrase, HostKeyCallback callback) throws Exception {
        disconnect();

        JSch jsch = new JSch();

        // Configure known hosts path
        File knownHosts = ConfigManager.getKnownHostsFile();
        jsch.setKnownHosts(knownHosts.getAbsolutePath());

        // Load private key if path is provided
        if (keyPath != null && !keyPath.isEmpty()) {
            File kf = new File(keyPath);
            if (!kf.exists() || !kf.canRead()) {
                throw new IllegalArgumentException("Private key file not found or unreadable: " + keyPath);
            }
            if (passphrase != null && !passphrase.isEmpty()) {
                jsch.addIdentity(keyPath, passphrase);
            } else {
                jsch.addIdentity(keyPath);
            }
        }

        session = jsch.getSession(username, host, port);
        session.setUserInfo(new SshUserInfo(passphrase, callback));
        session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
        
        // Connect session with a 15-second timeout
        session.connect(15000);

        // Open and connect SFTP channel
        sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(15000);
    }

    /** Checks whether the connection is active. */
    public boolean isConnected() {
        return session != null && session.isConnected() && sftp != null && sftp.isConnected();
    }

    /** Disconnects the active SFTP channel and SSH session. */
    public void disconnect() {
        if (sftp != null && sftp.isConnected()) {
            sftp.disconnect();
        }
        sftp = null;
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        session = null;
    }

    @Override
    public void close() {
        disconnect();
    }

    /**
     * Checks if a directory exists on the remote VM.
     */
    public boolean checkDirExists(String remotePath) {
        if (!isConnected()) return false;
        try {
            SftpATTRS attrs = sftp.stat(remotePath);
            return attrs.isDir();
        } catch (SftpException e) {
            return false;
        }
    }

    /**
     * Checks if a file exists on the remote VM.
     */
    public boolean checkFileExists(String remotePath) {
        if (!isConnected()) return false;
        try {
            SftpATTRS attrs = sftp.stat(remotePath);
            return !attrs.isDir();
        } catch (SftpException e) {
            return false;
        }
    }

    /**
     * Gets the remote file size in bytes, or returns 0 if error or missing.
     */
    public long getRemoteFileSize(String remotePath) {
        if (!isConnected()) return 0;
        try {
            SftpATTRS attrs = sftp.stat(remotePath);
            return attrs.getSize();
        } catch (SftpException e) {
            return 0;
        }
    }

    /**
     * Creates a directory recursively on the remote VM (like mkdir -p).
     */
    public void createRemoteDir(String remotePath) throws SftpException {
        if (!isConnected()) throw new IllegalStateException("Not connected");
        
        // Replace backslashes and normalize
        String path = remotePath.replace('\\', '/');
        String[] folders = path.split("/");
        
        String currentPath = "";
        for (String folder : folders) {
            if (folder.isEmpty()) {
                currentPath += "/";
                continue;
            }
            if (currentPath.equals("/")) {
                currentPath += folder;
            } else if (currentPath.isEmpty()) {
                currentPath = folder;
            } else {
                currentPath += "/" + folder;
            }
            
            try {
                sftp.stat(currentPath);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    sftp.mkdir(currentPath);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Uploads a file to the remote path.
     */
    public void uploadFile(String localPath, String remotePath, SftpProgressMonitor monitor) throws SftpException {
        if (!isConnected()) throw new IllegalStateException("Not connected");
        sftp.put(localPath, remotePath, monitor);
    }

    /**
     * Chmods a remote path.
     */
    public void chmod(int permissions, String remotePath) throws SftpException {
        if (!isConnected()) throw new IllegalStateException("Not connected");
        sftp.chmod(permissions, remotePath);
    }

    /**
     * Executes a remote command and streams output line-by-line.
     * Supports a timeout period.
     *
     * @param command the command string to run
     * @param lineConsumer callback invoked for every line of stdout or stderr
     * @param timeoutMs max time in milliseconds for the command to execute
     * @return exit code of the command
     */
    public int executeCommand(String command, Consumer<String> lineConsumer, long timeoutMs) throws Exception {
        if (!isConnected()) throw new IllegalStateException("Not connected");

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        
        // Merge error stream with standard output
        channel.setErrStream(null); 
        InputStream in = channel.getInputStream();
        InputStream err = channel.getErrStream();

        channel.connect();

        try (BufferedReader inReader = new BufferedReader(new InputStreamReader(in));
             BufferedReader errReader = new BufferedReader(new InputStreamReader(err))) {
            
            long startTime = System.currentTimeMillis();
            while (!channel.isClosed() || in.available() > 0 || err.available() > 0) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    channel.disconnect();
                    throw new TimeoutException("Command timed out: " + command);
                }

                // Read standard output
                while (in.available() > 0) {
                    String line = inReader.readLine();
                    if (line != null) {
                        lineConsumer.accept(line);
                    }
                }

                // Read error output
                while (err.available() > 0) {
                    String line = errReader.readLine();
                    if (line != null) {
                        lineConsumer.accept("[ERR] " + line);
                    }
                }

                // Small sleep to prevent CPU spinning
                Thread.sleep(50);
            }

            return channel.getExitStatus();
        } finally {
            channel.disconnect();
        }
    }

    /**
     * Performs a fast remote directory scan. Returns relative paths of directories and files.
     * Skips heavy dirs like node_modules, uploads, .git, logs.
     */
    public List<String> listRemoteFilesRecursive(String baseDir) throws SftpException {
        List<String> list = new ArrayList<>();
        if (!isConnected()) return list;
        
        listRemoteRecursive(baseDir, "", list);
        return list;
    }

    @SuppressWarnings("unchecked")
    private void listRemoteRecursive(String baseDir, String relativePath, List<String> list) throws SftpException {
        String currentPath = baseDir;
        if (!relativePath.isEmpty()) {
            currentPath = baseDir + "/" + relativePath;
        }

        Vector<ChannelSftp.LsEntry> entries = sftp.ls(currentPath);
        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (name.equals(".") || name.equals("..")) {
                continue;
            }

            // Exclude common large folders to avoid scan freezes
            if (name.equals("node_modules") || name.equals("uploads") || 
                name.equals(".git") || name.equals("logs")) {
                continue;
            }

            String entryRelPath = relativePath.isEmpty() ? name : relativePath + "/" + name;
            SftpATTRS attrs = entry.getAttrs();
            if (attrs.isDir()) {
                list.add("📁 " + entryRelPath);
                listRemoteRecursive(baseDir, entryRelPath, list);
            } else {
                list.add("📄 " + entryRelPath + "\t" + attrs.getSize());
            }
        }
    }
}
