package com.hrms.deploytool.ui;

import com.hrms.deploytool.archive.ZipExtractor;
import com.hrms.deploytool.deploy.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

/** 
 * Page 3 — Workspace layout.
 * Contains the core deployment planner interface including connection settings,
 * the dual-pane file explorer (local vs remote), execution logs, and the deploy bar.
 */
public class WorkspacePage {

    private final MainWindow nav;
    private final StackPane rootContainer = new StackPane();
    private final BorderPane root = new BorderPane();
    private final ExclusionMatcher exclusionMatcher = new ExclusionMatcher();
    
    // UI Panels and buttons
    private VBox connPanel;
    private Button connToggleBtn;
    private HBox verifiedBox;

    // Connection configuration fields
    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private TextField keyField;
    private PasswordField passField;
    private TextField remoteAppRootField;

    // Tree explorers
    private TreeView<String> localTreeView;
    private TreeView<String> remoteTreeView;

    // Status / Stats
    private Label lowerConsoleLabel;
    private CheckBox sendEmailCheckbox;

    // Deploy Progress Overlay fields
    private VBox deployOverlay;
    private ProgressBar progressBar;
    private Label statsLabel;
    private Label fileLabel;
    private TextArea consoleArea;
    private Button overlayCloseBtn;
    private Button overlayRollbackBtn;

    // Stats counters for the local tree
    private int totalFileCount = 0;
    private int excludedCount = 0;
    private long totalSizeBytes = 0;

    /**
     * Constructs the WorkspacePage.
     * @param nav The main window controller used for page transitions.
     */
    public WorkspacePage(MainWindow nav) {
        this.nav = nav;
        build();
        loadSavedConfig();
    }

    /** @return The root node representing the workspace page. */
    public Node getNode() { return rootContainer; }

    /** Builds the full workspace layout. */
    private void build() {
        root.getStyleClass().add("bg-black");

        VBox frame = new VBox(0);
        frame.getStyleClass().addAll("bg-black", "padding-24");

        Button settingsBtn = UI.iconBtn("⚙", "Settings");
        settingsBtn.setOnAction(e -> nav.showSettingsDialog());

        Button logsBtn = UI.iconBtn("📄", "View log");
        logsBtn.setOnAction(e -> nav.showLogsDialog());

        // App window using the centralized UI builder
        VBox win = UI.appWindow(
            UI.buildTopbar("ZIP file extractor", UI.zipIcon(),
                UI.iconBtn("⏱", "History"), logsBtn,
                UI.iconBtn("↻", "Refresh"), settingsBtn
            ),
            buildToolbar(),
            buildConnPanel(),
            buildPkgRow(),
            buildSplit(),
            buildConsole(),
            buildDeployBar()
        );
        VBox.setVgrow(win, Priority.ALWAYS);

        frame.getChildren().add(win);
        root.setCenter(frame);
        
        // Wrap everything inside a StackPane so we can cover it with a progress overlay
        rootContainer.getChildren().addAll(root, buildDeployOverlay());
    }

    /** Builds the secondary toolbar containing the validation status and back buttons. */
    private HBox buildToolbar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        HBox left = new HBox(6); 
        left.setAlignment(Pos.CENTER_LEFT);
        String filename = (nav.getSelectedZip() != null) ? nav.getSelectedZip().getName() : "hrms_update.zip";
        String statsText = "";
        ZipExtractor.ExtractionResult result = nav.getExtractionResult();
        if (result != null) {
            String sizeMb = String.format("%.1f", result.totalBytes() / 1024.0 / 1024.0);
            statsText = " · " + result.fileCount() + " files · " + sizeMb + " mb";
        }
        left.getChildren().addAll(
            UI.checkCircle(15,"#7ec97e"),
            UI.label(filename + " extracted" + statsText + " · comparing against server","toolbar-status")
        );
        
        Region sp = new Region(); 
        HBox.setHgrow(sp, Priority.ALWAYS);

        connToggleBtn = UI.secondaryBtn("Show connection");
        connToggleBtn.setOnAction(e -> toggleConn());
        
        Button backBtn = UI.secondaryBtn("back to page 1");
        backBtn.setOnAction(e -> nav.showLanding());

        bar.getChildren().addAll(left, sp, connToggleBtn, backBtn);
        return bar;
    }

    /** Builds the collapsible connection settings panel (SSH/SFTP profile). */
    private VBox buildConnPanel() {
        connPanel = new VBox(0);
        connPanel.getStyleClass().add("conn-panel");
        // Hidden by default
        connPanel.setVisible(false); 
        connPanel.setManaged(false);

        // Connected status bar
        HBox connBar = new HBox(10); 
        connBar.getStyleClass().add("conn-bar"); 
        connBar.setAlignment(Pos.CENTER_LEFT);
        
        Label dot = new Label("●"); 
        dot.setStyle("-fx-text-fill:#7ec97e;-fx-font-size:8px;");
        Label ct = new Label("Production VM Connection Settings");
        ct.getStyleClass().add("conn-text");
        connBar.getChildren().addAll(dot, ct);

        // Responsive GridPane for form fields
        GridPane form = new GridPane(); 
        form.setHgap(14); 
        form.setVgap(14);
        form.setPadding(new Insets(16,0,0,0));
        form.setMaxWidth(Double.MAX_VALUE);
        
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS); c1.setPercentWidth(40);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setHgrow(Priority.ALWAYS); c2.setPercentWidth(20);
        ColumnConstraints c3 = new ColumnConstraints(); c3.setHgrow(Priority.ALWAYS); c3.setPercentWidth(40);
        form.getColumnConstraints().addAll(c1,c2,c3);
        
        // Initialize Fields
        hostField = UI.textField("");
        portField = UI.textField("22");
        usernameField = UI.textField("ubuntu");
        keyField = UI.textField("");
        keyField.setEditable(false);
        passField = new PasswordField();
        passField.getStyleClass().add("text-input");
        passField.setMaxWidth(Double.MAX_VALUE);
        remoteAppRootField = UI.textField("~/HR_MANAGEMENT_SYSTEM");

        // Form fields layout
        form.add(UI.fieldGroup("Host / IP",  hostField), 0, 0);
        form.add(UI.fieldGroup("Port",       portField), 1, 0);
        form.add(UI.fieldGroup("Username",   usernameField), 2, 0);

        HBox keyRow = new HBox(8); 
        keyRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(keyField, Priority.ALWAYS);
        Button browse = UI.secondaryBtn("Browse"); browse.setMinWidth(70);
        browse.setOnAction(e -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Select Private Key File");
            File file = fileChooser.showOpenDialog(browse.getScene().getWindow());
            if (file != null) {
                keyField.setText(file.getAbsolutePath());
            }
        });
        keyRow.getChildren().addAll(keyField, browse);

        form.add(UI.fieldGroup("Private key file", keyRow), 0, 1, 2, 1);
        form.add(UI.fieldGroup("Key passphrase (optional)", passField), 2, 1);
        form.add(UI.fieldGroup("Remote app root", remoteAppRootField), 0, 2, 3, 1);

        // Action buttons
        HBox actions = new HBox(10); 
        actions.setAlignment(Pos.CENTER_RIGHT); 
        actions.setPadding(new Insets(12,0,0,0));
        
        Button save = UI.secondaryBtn("Save profile");
        save.setOnAction(e -> saveConfig());
        
        Button test = UI.greenBtn("Test connection");
        test.setOnAction(e -> testConnection(false));
        
        actions.getChildren().addAll(save, test);

        // Verification success box
        verifiedBox = new HBox(10); 
        verifiedBox.getStyleClass().add("verified-box");
        verifiedBox.setAlignment(Pos.CENTER_LEFT); 
        verifiedBox.setVisible(false); 
        verifiedBox.setManaged(false);
        verifiedBox.setPadding(new Insets(12,14,12,14));
        
        Label vIcon = new Label("✓"); 
        vIcon.setStyle("-fx-text-fill:#7ec97e;-fx-font-size:18px;");
        Label vt = new Label("Connection verified — Host key trusted · remote app root found");
        vt.getStyleClass().add("verified-text"); 
        HBox.setHgrow(vt, Priority.ALWAYS);
        Label vc = new Label("  ✓ Verified  "); 
        vc.getStyleClass().add("btn-connected");
        
        verifiedBox.getChildren().addAll(vIcon, vt, vc);

        connPanel.getChildren().addAll(connBar, form, actions, verifiedBox);
        return connPanel;
    }

    /** Builds the package status row directly above the file explorers. */
    private HBox buildPkgRow() {
        HBox row = new HBox(8); 
        row.getStyleClass().add("pkg-row"); 
        row.setAlignment(Pos.CENTER_LEFT);
        
        HBox chk = new HBox(4); 
        chk.setAlignment(Pos.CENTER_LEFT);
        chk.getChildren().addAll(UI.checkCircle(12,"#7ec97e"), UI.label("validated","pkg-check"));
        
        Region sp = new Region(); 
        HBox.setHgrow(sp, Priority.ALWAYS);
        
        Label choose = new Label("Choose another zip...");
        choose.getStyleClass().add("pkg-choose"); 
        choose.setOnMouseClicked(e -> nav.showLanding());
        
        String filename = (nav.getSelectedZip() != null) ? nav.getSelectedZip().getName() : "hrms_update.zip";

        // Show wrapper folder info if detected
        ZipExtractor.ExtractionResult result = nav.getExtractionResult();
        String wrapperText = (result != null && result.wrapperFolderName() != null) 
            ? "wrapper folder '" + result.wrapperFolderName() + "' detected → mapped to app root"
            : "no wrapper folder";

        row.getChildren().addAll(
            UI.zipIcon(),
            UI.label("update package","pkg-dim"),
            UI.bold(filename,"pkg-name"),
            UI.label("/","pkg-dim"), chk,
            UI.label("·","pkg-dim"), UI.label(wrapperText,"pkg-dim"),
            sp, choose
        );
        return row;
    }

    /** Builds the dual-pane file explorer view (Local vs Remote). */
    private HBox buildSplit() {
        HBox split = new HBox(0);
        VBox.setVgrow(split, Priority.ALWAYS);
        split.setMinHeight(280);

        VBox localExplorer  = buildExplorer(false);
        VBox arrowCol       = buildArrowCol();
        VBox serverExplorer = buildExplorer(true);
        HBox.setHgrow(localExplorer,  Priority.ALWAYS);
        HBox.setHgrow(serverExplorer, Priority.ALWAYS);

        split.getChildren().addAll(localExplorer, arrowCol, serverExplorer);
        return split;
    }

    /** Generic builder for either the local or remote side of the file explorer. */
    private VBox buildExplorer(boolean server) {
        VBox ex = new VBox(0); 
        ex.setStyle("-fx-background-color:#252526;");

        HBox hdr = new HBox(7); 
        hdr.getStyleClass().add("explorer-header"); 
        hdr.setAlignment(Pos.CENTER_LEFT);
        
        if (!server) {
            hdr.getChildren().addAll(UI.packageIcon(), UI.label("Update package","explorer-header-label"));
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            
            // Build the local tree first to load stats
            TreeView<String> tree = buildLocalTree();
            localTreeView = tree;

            String statsLabel = totalFileCount + " files · " + formatFileSize(totalSizeBytes) + " · " + excludedCount + " excluded";
            hdr.getChildren().addAll(sp, UI.label(statsLabel,"explorer-header-sub"));
            VBox.setVgrow(tree, Priority.ALWAYS);
            ex.getChildren().addAll(hdr, tree);
        } else {
            hdr.getChildren().addAll(UI.serverIcon(), UI.label("Production server","explorer-header-label"));
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            hdr.getChildren().addAll(sp, UI.label("SFTP · destination","explorer-header-sub"));
            
            TreeView<String> tree = buildServerTree();
            remoteTreeView = tree;
            
            VBox.setVgrow(tree, Priority.ALWAYS);
            ex.getChildren().addAll(hdr, tree);
        }

        return ex;
    }

    /** Builds the local file tree from the extracted zip, applying exclusion tags. */
    private TreeView<String> buildLocalTree() {
        ZipExtractor.ExtractionResult result = nav.getExtractionResult();
        TreeItem<String> rootNode;

        if (result != null && result.extractedRoot() != null && result.extractedRoot().exists()) {
            totalFileCount = 0;
            excludedCount = 0;
            totalSizeBytes = 0;

            rootNode = buildTreeWithExclusions(result.extractedRoot(), result.extractedRoot().toPath());
            String filename = (nav.getSelectedZip() != null) ? nav.getSelectedZip().getName() : "update package";
            rootNode.setValue("📁 " + filename);
        } else {
            rootNode = treeFolder("hrms_update (no data)");
        }

        TreeView<String> tv = new TreeView<>(rootNode);
        tv.getStyleClass().add("tree-view");
        tv.setShowRoot(false);
        tv.setCellFactory(v -> new FileTreeCell());
        return tv;
    }

    /**
     * Recursively builds a TreeItem tree from an extracted directory,
     * applying the ExclusionMatcher to tag files as [EXCLUDED] or [NEW].
     */
    private TreeItem<String> buildTreeWithExclusions(File dir, Path rootPath) {
        TreeItem<String> item = treeFolder(dir.getName());
        File[] files = dir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File file : files) {
                Path relativePath = rootPath.relativize(file.toPath());
                boolean excluded = exclusionMatcher.isExcluded(relativePath);

                if (file.isDirectory()) {
                    if (excluded) {
                        item.getChildren().add(treeFile(file.getName(), "EXCLUDED", null));
                        excludedCount++;
                    } else {
                        item.getChildren().add(buildTreeWithExclusions(file, rootPath));
                    }
                } else {
                    totalFileCount++;
                    totalSizeBytes += file.length();

                    if (excluded) {
                        excludedCount++;
                        item.getChildren().add(treeFile(file.getName(), "EXCLUDED", null));
                    } else {
                        String size = formatFileSize(file.length());
                        item.getChildren().add(treeFile(file.getName(), "NEW", size));
                    }
                }
            }
        }
        return item;
    }

    /** Mocks the initial remote SFTP server TreeView data before verification. */
    private TreeView<String> buildServerTree() {
        TreeItem<String> rootNode = treeFolder("HR_MANAGEMENT_SYSTEM [dest]");
        
        // Initial clean state shows placeholder
        TreeItem<String> placeholder = new TreeItem<>("📄 [Click 'Test Connection' to read VM files]");
        rootNode.getChildren().add(placeholder);

        TreeView<String> tv = new TreeView<>(rootNode);
        tv.getStyleClass().add("tree-view");
        tv.setShowRoot(true);
        tv.setCellFactory(v -> new FileTreeCell());
        return tv;
    }

    /** Helper to create a standardized folder TreeItem. */
    private TreeItem<String> treeFolder(String name) {
        TreeItem<String> item = new TreeItem<>("📁 " + name);
        item.setExpanded(true);
        return item;
    }
    
    /** Helper to create a standardized file TreeItem. */
    private TreeItem<String> treeFile(String name, String tag, String meta) {
        String label = "📄 " + name;
        if (tag != null) label += "\t[" + tag + "]";
        if (meta != null) label += "\t" + meta;
        return new TreeItem<>(label);
    }

    /** Builds the middle column containing manual copy/revert buttons. */
    private VBox buildArrowCol() {
        VBox col = new VBox(10); 
        col.getStyleClass().add("arrow-col"); 
        col.setAlignment(Pos.CENTER);
        col.setMinWidth(40); 
        col.setMaxWidth(40);
        
        Button right = UI.arrowBtn("▶","Copy to server");
        Button left  = UI.arrowBtn("◀","Revert to local");
        col.getChildren().addAll(right, left);
        return col;
    }

    /** Builds the terminal output readout. */
    private HBox buildConsole() {
        HBox c = new HBox();
        c.setStyle("-fx-background-color:#181818;-fx-border-color:#333333 transparent transparent transparent;-fx-border-width:1;-fx-padding:8 12 8 12;-fx-min-height:52;-fx-max-height:52;");
        lowerConsoleLabel = new Label("[SYSTEM] ready to deploy · awaiting connection confirmation");
        lowerConsoleLabel.getStyleClass().add("console-text");
        c.getChildren().add(lowerConsoleLabel);
        return c;
    }

    /** Builds the final deployment trigger bar. */
    private HBox buildDeployBar() {
        HBox bar = new HBox(14); 
        bar.getStyleClass().add("deploybar"); 
        bar.setAlignment(Pos.CENTER_LEFT);

        HBox left = new HBox(14); 
        left.setAlignment(Pos.CENTER_LEFT);
        
        Label ready = new Label("✓ Ready"); 
        ready.getStyleClass().add("deploybar-text");
        
        sendEmailCheckbox = new CheckBox("Send release email on success");
        sendEmailCheckbox.setSelected(true); 
        sendEmailCheckbox.getStyleClass().add("checkbox-white");
        
        Label rollback = new Label("rollback last deploy"); 
        rollback.getStyleClass().add("deploybar-link");
        rollback.setOnMouseClicked(e -> {
            if (!nav.isConnectionVerified()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Not Connected");
                alert.setContentText("Please verify the connection first to perform a rollback.");
                alert.showAndWait();
                return;
            }
            showDeployOverlay();
            triggerManualRollback();
        });
        
        left.getChildren().addAll(ready, sendEmailCheckbox, rollback);

        Region sp = new Region(); 
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox right = new HBox(10); 
        right.setAlignment(Pos.CENTER_RIGHT);
        
        Button testConn = UI.outlineBtn("Test Connection");
        testConn.setOnAction(e -> testConnection(false));

        Button deploy = UI.greenBtn("Deploy to Server");
        deploy.setOnAction(e -> startDeployment());

        right.getChildren().addAll(testConn, deploy);

        bar.getChildren().addAll(left, sp, right);
        return bar;
    }

    /** Builds the dark deploy/rollback full screen console overlay. */
    private VBox buildDeployOverlay() {
        deployOverlay = new VBox(15);
        deployOverlay.setStyle("-fx-background-color:rgba(13,13,13,0.92);-fx-padding:30;");
        deployOverlay.setAlignment(Pos.CENTER);
        deployOverlay.setVisible(false);
        deployOverlay.setManaged(false);

        Label title = UI.bold("DEPLOYMENT STATUS","p1-title");
        title.setStyle("-fx-text-fill:white;-fx-font-size:18px;");

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("progress-bar");
        progressBar.setPrefHeight(12);

        statsLabel = new Label("Initializing...");
        statsLabel.setStyle("-fx-text-fill:#cfe9d4;-fx-font-size:13px;-fx-font-weight:bold;");

        fileLabel = new Label("");
        fileLabel.setStyle("-fx-text-fill:#7a7a7a;-fx-font-size:11px;-fx-font-family: 'Consolas', monospace;");

        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setWrapText(true);
        consoleArea.setStyle("-fx-control-inner-background:#181818;-fx-text-fill:#cccccc;-fx-font-family:'Consolas',monospace;-fx-font-size:11px;-fx-border-color:#333333;");
        VBox.setVgrow(consoleArea, Priority.ALWAYS);

        HBox btns = new HBox(12);
        btns.setAlignment(Pos.CENTER_RIGHT);
        
        overlayCloseBtn = UI.primaryBtn("Close Overlay");
        overlayCloseBtn.setOnAction(e -> hideDeployOverlay());
        overlayCloseBtn.setVisible(false);

        overlayRollbackBtn = UI.outlineBtn("Rollback Deployment");
        overlayRollbackBtn.setStyle("-fx-border-color:#e06c75;-fx-text-fill:#e06c75;");
        overlayRollbackBtn.setOnAction(e -> triggerManualRollback());
        overlayRollbackBtn.setVisible(false);

        btns.getChildren().addAll(overlayRollbackBtn, overlayCloseBtn);

        deployOverlay.getChildren().addAll(title, progressBar, statsLabel, fileLabel, consoleArea, btns);
        return deployOverlay;
    }

    private void showDeployOverlay() {
        deployOverlay.setVisible(true);
        deployOverlay.setManaged(true);
    }

    private void hideDeployOverlay() {
        deployOverlay.setVisible(false);
        deployOverlay.setManaged(false);
    }

    /** Toggles the connection settings panel visibility. */
    private void toggleConn() {
        boolean show = !connPanel.isVisible();
        connPanel.setVisible(show); 
        connPanel.setManaged(show);
        connToggleBtn.setText(show ? "Hide connection" : "Show connection");
    }

    /** Loads connection configuration values from config manager. */
    private void loadSavedConfig() {
        Properties config = ConfigManager.loadConfig();
        hostField.setText(config.getProperty("host", ""));
        portField.setText(config.getProperty("port", "22"));
        usernameField.setText(config.getProperty("username", "ubuntu"));
        keyField.setText(config.getProperty("privateKeyPath", ""));
        remoteAppRootField.setText(config.getProperty("remoteAppRoot", "~/HR_MANAGEMENT_SYSTEM"));
    }

    /** Saves connection configuration settings to properties file. */
    private void saveConfig() {
        Properties config = ConfigManager.loadConfig();
        config.setProperty("host", hostField.getText().trim());
        config.setProperty("port", portField.getText().trim());
        config.setProperty("username", usernameField.getText().trim());
        config.setProperty("privateKeyPath", keyField.getText().trim());
        config.setProperty("remoteAppRoot", remoteAppRootField.getText().trim());
        
        ConfigManager.saveConfig(config);
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText("Profile configuration saved successfully.");
        alert.showAndWait();
    }

    /**
     * Connects via SSH to verify connection credentials.
     *
     * @param silent if true, does not show successful alerts
     */
    private void testConnection(boolean silent) {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String username = usernameField.getText().trim();
        String keyPath = keyField.getText().trim();
        String passphrase = passField.getText();
        String remoteRoot = remoteAppRootField.getText().trim();

        if (host.isEmpty() || portStr.isEmpty() || username.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Missing Configuration");
            alert.setHeaderText("Incomplete Settings");
            alert.setContentText("Please configure Host, Port, and Username.");
            alert.showAndWait();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid Port");
            alert.setContentText("Port must be a numeric integer value.");
            alert.showAndWait();
            return;
        }

        lowerConsoleLabel.setText("[SYSTEM] Testing SSH connection to " + host + "...");
        
        Task<Void> testTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (SshClient client = new SshClient()) {
                    client.connect(host, port, username, keyPath, passphrase, WorkspacePage.this::showHostKeyTOFU);
                    boolean dirExists = client.checkDirExists(remoteRoot);
                    if (!dirExists) {
                        throw new IOException("Remote application directory '" + remoteRoot + "' does not exist on target VM.");
                    }
                }
                return null;
            }
        };

        testTask.setOnSucceeded(event -> {
            nav.setConnectionVerified(true);
            nav.setSessionPassphrase(passphrase); // Cache validated passphrase
            
            verifiedBox.setVisible(true);
            verifiedBox.setManaged(true);
            lowerConsoleLabel.setText("[SYSTEM] Connection tested and verified successfully.");

            if (!silent) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Connection Successful");
                alert.setHeaderText(null);
                alert.setContentText("Verified connection to remote VM host! Host key approved and remote root verified.");
                alert.showAndWait();
            }
            refreshRemoteFileTree();
        });

        testTask.setOnFailed(event -> {
            nav.setConnectionVerified(false);
            Throwable e = testTask.getException();
            lowerConsoleLabel.setText("[SYSTEM] SSH connection check failed: " + e.getMessage());
            
            verifiedBox.setVisible(false);
            verifiedBox.setManaged(false);

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection Verification Failed");
            alert.setHeaderText("SSH / SFTP authentication error");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        });

        new Thread(testTask).start();
    }

    /**
     * Pops up a confirmation dialog for SSH TOFU (Trust-On-First-Use) Host Key check.
     */
    private boolean showHostKeyTOFU(String message) {
        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("SSH Host Key Verification (TOFU)");
            alert.setHeaderText("Unrecognized Remote Host Key");
            alert.setContentText(message + "\n\nDo you trust this host key and wish to save it to your known_hosts list?");
            
            ButtonType yesButton = new ButtonType("Trust & Save", ButtonBar.ButtonData.YES);
            ButtonType noButton = new ButtonType("Reject Connection", ButtonBar.ButtonData.NO);
            alert.getButtonTypes().setAll(yesButton, noButton);

            Optional<ButtonType> option = alert.showAndWait();
            future.complete(option.isPresent() && option.get() == yesButton);
        });
        try {
            return future.get();
        } catch (Exception e) {
            return false;
        }
    }

    /** Runs remote scan task to query files structure and compare. */
    private void refreshRemoteFileTree() {
        if (!nav.isConnectionVerified()) return;

        lowerConsoleLabel.setText("[SYSTEM] Fetching remote file structure from VM...");
        
        Task<List<String>> listTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                String host = hostField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                String username = usernameField.getText().trim();
                String keyPath = keyField.getText().trim();
                String passphrase = nav.getSessionPassphrase();
                String remoteRoot = remoteAppRootField.getText().trim();

                try (SshClient client = new SshClient()) {
                    client.connect(host, port, username, keyPath, passphrase, WorkspacePage.this::showHostKeyTOFU);
                    return client.listRemoteFilesRecursive(remoteRoot);
                }
            }
        };

        listTask.setOnSucceeded(event -> {
            List<String> remotePaths = listTask.getValue();
            updateTreesWithRemoteData(remotePaths);
            lowerConsoleLabel.setText("[SYSTEM] Remote file sync completed. NEW / OVERWRITE statuses resolved.");
        });

        listTask.setOnFailed(event -> {
            Throwable e = listTask.getException();
            lowerConsoleLabel.setText("[SYSTEM] Sync failed: " + e.getMessage());
        });

        new Thread(listTask).start();
    }

    /** Compares files and rebuilds the TreeViews. */
    private void updateTreesWithRemoteData(List<String> remotePaths) {
        Set<String> remoteFiles = new HashSet<>();
        for (String remoteItem : remotePaths) {
            if (remoteItem.startsWith("📄 ")) {
                String val = remoteItem.substring(2);
                if (val.contains("\t")) {
                    val = val.substring(0, val.indexOf('\t'));
                }
                remoteFiles.add(val);
            }
        }

        // 1. Rebuild Local TreeView with actual comparisons
        ZipExtractor.ExtractionResult ext = nav.getExtractionResult();
        if (ext != null && ext.extractedRoot() != null && ext.extractedRoot().exists()) {
            TreeItem<String> localRoot = buildLocalTreeWithComparison(ext.extractedRoot(), ext.extractedRoot().toPath(), remoteFiles);
            String zipName = (nav.getSelectedZip() != null) ? nav.getSelectedZip().getName() : "update package";
            localRoot.setValue("📁 " + zipName);
            localTreeView.setRoot(localRoot);
        }

        // 2. Rebuild Remote TreeView hierarchically from list
        String remoteRootName = remoteAppRootField.getText();
        if (remoteRootName.contains("/")) {
            remoteRootName = remoteRootName.substring(remoteRootName.lastIndexOf('/') + 1);
        }
        TreeItem<String> remoteRoot = buildTreeFromRelativePaths(remoteRootName, remotePaths);
        remoteTreeView.setRoot(remoteRoot);
    }

    private TreeItem<String> buildLocalTreeWithComparison(File dir, Path rootPath, Set<String> remoteFiles) {
        TreeItem<String> item = treeFolder(dir.getName());
        File[] files = dir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File file : files) {
                Path relativePath = rootPath.relativize(file.toPath());
                String relPathStr = relativePath.toString().replace('\\', '/');
                boolean excluded = exclusionMatcher.isExcluded(relativePath);

                if (file.isDirectory()) {
                    if (excluded) {
                        item.getChildren().add(treeFile(file.getName(), "EXCLUDED", null));
                    } else {
                        item.getChildren().add(buildLocalTreeWithComparison(file, rootPath, remoteFiles));
                    }
                } else {
                    if (excluded) {
                        item.getChildren().add(treeFile(file.getName(), "EXCLUDED", null));
                    } else {
                        String size = formatFileSize(file.length());
                        boolean exists = remoteFiles.contains(relPathStr);
                        String tag = exists ? "OVERWRITE" : "NEW";
                        item.getChildren().add(treeFile(file.getName(), tag, size));
                    }
                }
            }
        }
        return item;
    }

    private static TreeItem<String> buildTreeFromRelativePaths(String rootName, List<String> remotePaths) {
        TreeItem<String> rootItem = new TreeItem<>("📁 " + rootName);
        rootItem.setExpanded(true);
        
        Map<String, TreeItem<String>> pathMap = new HashMap<>();
        pathMap.put("", rootItem);
        
        for (String itemLine : remotePaths) {
            boolean isDir = itemLine.startsWith("📁 ");
            String content = itemLine.substring(2);
            String relPath = content;
            String sizeMeta = null;
            
            if (!isDir && content.contains("\t")) {
                String[] parts = content.split("\t");
                relPath = parts[0];
                sizeMeta = parts[1];
            }
            
            String[] segments = relPath.split("/");
            String parentPath = "";
            
            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];
                String currentPath = parentPath.isEmpty() ? segment : parentPath + "/" + segment;
                
                if (!pathMap.containsKey(currentPath)) {
                    TreeItem<String> newItem;
                    if (i == segments.length - 1 && !isDir) {
                        String label = "📄 " + segment;
                        if (sizeMeta != null) {
                            try {
                                long sz = Long.parseLong(sizeMeta);
                                label += "\t" + formatFileSizeStatic(sz);
                            } catch (NumberFormatException ignored) {}
                        }
                        newItem = new TreeItem<>(label);
                    } else {
                        newItem = new TreeItem<>("📁 " + segment);
                        newItem.setExpanded(true);
                    }
                    
                    TreeItem<String> parentItem = pathMap.get(parentPath);
                    if (parentItem != null) {
                        parentItem.getChildren().add(newItem);
                    }
                    pathMap.put(currentPath, newItem);
                }
                parentPath = currentPath;
            }
        }
        return rootItem;
    }

    /** Triggers end-to-end deployment. */
    private void startDeployment() {
        if (!nav.isConnectionVerified()) {
            testConnection(true); // Implicit verification check
            return;
        }

        if (nav.getExtractionResult() == null || nav.getSelectedZip() == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Missing Archive");
            alert.setContentText("No validated zip package extracted. Please return to dashboard.");
            alert.showAndWait();
            return;
        }

        // Show progress overlay
        showDeployOverlay();
        consoleArea.clear();
        progressBar.setProgress(0);
        statsLabel.setText("Initializing deployment...");
        fileLabel.setText("");
        overlayCloseBtn.setVisible(false);
        overlayRollbackBtn.setVisible(false);

        // Prepopulate post deploy actions list
        List<String> postCommands = new ArrayList<>();
        ZipExtractor.ExtractionResult extResult = nav.getExtractionResult();
        String remoteAppRoot = remoteAppRootField.getText().trim();

        if (extResult != null) {
            // Suggest backend command if package.json in zip
            File bPackage = new File(extResult.extractedRoot(), "backend/package.json");
            if (bPackage.exists()) {
                postCommands.add("cd " + remoteAppRoot + "/backend && npm install");
            }
            // Suggest frontend build if package.json/sources changed
            File fPackage = new File(extResult.extractedRoot(), "frontend/package.json");
            if (fPackage.exists()) {
                postCommands.add("cd " + remoteAppRoot + "/frontend && npm install && npm run build");
            }
        }
        
        // Suggest pm2/systemctl service restart
        postCommands.add("pm2 restart hrms || sudo systemctl restart hrms");

        Task<Boolean> deployTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                Properties config = ConfigManager.loadConfig();
                config.setProperty("host", hostField.getText().trim());
                config.setProperty("port", portField.getText().trim());
                config.setProperty("username", usernameField.getText().trim());
                config.setProperty("privateKeyPath", keyField.getText().trim());
                config.setProperty("remoteAppRoot", remoteAppRoot);

                DeployEngine engine = new DeployEngine(
                    config,
                    nav.getExtractionResult().extractedRoot(),
                    nav.getSelectedZip(),
                    sendEmailCheckbox.isSelected(),
                    postCommands,
                    new DeployEngine.DeployListener() {
                        @Override
                        public void onLog(String message) {
                            Platform.runLater(() -> {
                                consoleArea.appendText(message + "\n");
                                lowerConsoleLabel.setText(message);
                            });
                        }

                        @Override
                        public void onProgress(double percent, long bytesUploaded, long totalBytes, String currentFile, double speedKbps) {
                            Platform.runLater(() -> {
                                progressBar.setProgress(percent);
                                String totalMb = String.format("%.1f", totalBytes / 1024.0 / 1024.0);
                                String uploadedMb = String.format("%.1f", bytesUploaded / 1024.0 / 1024.0);
                                String speedStr = speedKbps > 0 ? String.format(" · %.1f KB/s", speedKbps) : "";
                                statsLabel.setText(String.format("%.0f%% · %s MB / %s MB%s", percent * 100, uploadedMb, totalMb, speedStr));
                                fileLabel.setText(currentFile);
                            });
                        }

                        @Override
                        public void onStatusChanged(String status) {
                            Platform.runLater(() -> {
                                // optional update
                            });
                        }
                    }
                );

                return engine.runDeploy(nav.getSessionPassphrase(), WorkspacePage.this::showHostKeyTOFU);
            }
        };

        deployTask.setOnSucceeded(event -> {
            boolean success = deployTask.getValue();
            overlayCloseBtn.setVisible(true);
            if (success) {
                statsLabel.setText("DEPLOYMENT SUCCESSFUL!");
                progressBar.setProgress(1.0);
                fileLabel.setText("All changes applied successfully.");
                
                // Fetch the newest files after deployment
                refreshRemoteFileTree();
            } else {
                statsLabel.setText("DEPLOYMENT FAILED");
                progressBar.setProgress(0);
                fileLabel.setText("Deployment aborted or failed during file copy / backup.");
                overlayRollbackBtn.setVisible(true);
            }
        });

        deployTask.setOnFailed(event -> {
            Throwable ex = deployTask.getException();
            consoleArea.appendText("[ERROR] Thread exception: " + ex.getMessage() + "\n");
            statsLabel.setText("DEPLOYMENT ERROR");
            fileLabel.setText(ex.getMessage());
            overlayCloseBtn.setVisible(true);
            overlayRollbackBtn.setVisible(true);
        });

        new Thread(deployTask).start();
    }

    /** Triggers manual rollback using last backup recorded in properties. */
    private void triggerManualRollback() {
        overlayRollbackBtn.setVisible(false);
        consoleArea.appendText("Starting rollback process...\n");
        
        Properties config = ConfigManager.loadConfig();
        String lastBackup = config.getProperty("lastBackupName");
        
        if (lastBackup == null || lastBackup.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Rollback");
            alert.setHeaderText("Specify Rollback Backup File");
            alert.setContentText("No last backup recorded in local profile. Enter backup tarball file name on VM:");
            
            TextField backupInput = new TextField("HRMS_backup_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".tar.gz");
            alert.getDialogPane().setContent(backupInput);
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                lastBackup = backupInput.getText().trim();
            } else {
                consoleArea.appendText("Rollback aborted by user.\n");
                overlayCloseBtn.setVisible(true);
                overlayRollbackBtn.setVisible(true);
                return;
            }
        }

        final String backupName = lastBackup;
        Task<Boolean> rollbackTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                Properties config = ConfigManager.loadConfig();
                config.setProperty("host", hostField.getText().trim());
                config.setProperty("port", portField.getText().trim());
                config.setProperty("username", usernameField.getText().trim());
                config.setProperty("privateKeyPath", keyField.getText().trim());
                config.setProperty("remoteAppRoot", remoteAppRootField.getText().trim());

                DeployEngine engine = new DeployEngine(
                    config, null, null, false, null,
                    new DeployEngine.DeployListener() {
                        @Override
                        public void onLog(String message) {
                            Platform.runLater(() -> consoleArea.appendText(message + "\n"));
                        }
                        @Override
                        public void onProgress(double percent, long b, long t, String f, double s) {}
                        @Override
                        public void onStatusChanged(String s) {}
                    }
                );
                return engine.performManualRollback(nav.getSessionPassphrase(), WorkspacePage.this::showHostKeyTOFU, backupName);
            }
        };

        rollbackTask.setOnSucceeded(event -> {
            boolean success = rollbackTask.getValue();
            overlayCloseBtn.setVisible(true);
            if (success) {
                statsLabel.setText("ROLLBACK SUCCESSFUL");
                fileLabel.setText("Restored VM directory structure from " + backupName);
                
                // Re-sync file trees
                refreshRemoteFileTree();
            } else {
                statsLabel.setText("ROLLBACK FAILED");
                fileLabel.setText("Failed to extract backup file on VM.");
                overlayRollbackBtn.setVisible(true);
            }
        });

        rollbackTask.setOnFailed(event -> {
            consoleArea.appendText("[ERROR] Rollback failed: " + rollbackTask.getException().getMessage() + "\n");
            statsLabel.setText("ROLLBACK ERROR");
            overlayCloseBtn.setVisible(true);
            overlayRollbackBtn.setVisible(true);
        });

        new Thread(rollbackTask).start();
    }

    private String formatFileSize(long bytes) {
        return formatFileSizeStatic(bytes);
    }

    private static String formatFileSizeStatic(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    /** 
     * Custom JavaFX TreeCell that renders specialized UI tags (e.g. EXCLUDED, NEW, OVERWRITE) 
     * inline next to the file names by parsing the tab-delimited strings.
     */
    static class FileTreeCell extends TreeCell<String> {
        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { 
                setGraphic(null); 
                setText(null); 
                return; 
            }
            if (item.contains("\t")) {
                String[] parts = item.split("\t");
                HBox row = new HBox(8); 
                row.setAlignment(Pos.CENTER_LEFT);
                
                Label name = new Label(parts[0]);
                name.getStyleClass().add(item.contains("[EXCLUDED]") ? "tree-node-excluded" : "tree-node-normal");
                row.getChildren().add(name);
                
                for (int i = 1; i < parts.length; i++) {
                    String p = parts[i].trim();
                    if (p.startsWith("[") && p.endsWith("]")) {
                        String tag = p.substring(1, p.length()-1);
                        Label badge = new Label(tag);
                        badge.getStyleClass().add(switch(tag) {
                            case "OVERWRITE" -> "tag-overwrite";
                            case "NEW"       -> "tag-new";
                            case "EXCLUDED"  -> "tag-excluded";
                            default -> "tag-current";
                        });
                        row.getChildren().add(badge);
                    } else if (!p.isEmpty()) {
                        Label meta = new Label(p);
                        meta.getStyleClass().add("tree-meta");
                        row.getChildren().add(meta);
                    }
                }
                setGraphic(row); setText(null);
            } else {
                setText(item); 
                setGraphic(null);
                getStyleClass().add(item.startsWith("📁") ? "tree-folder" : "tree-file");
            }
        }
    }
}
