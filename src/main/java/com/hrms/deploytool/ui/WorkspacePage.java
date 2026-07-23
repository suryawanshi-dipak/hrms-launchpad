package com.hrms.deploytool.ui;

import com.hrms.deploytool.archive.ZipExtractor;
import com.hrms.deploytool.deploy.ExclusionMatcher;

import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.nio.file.Path;

/** 
 * Page 3 — Workspace layout.
 * Contains the core deployment planner interface including connection settings,
 * the dual-pane file explorer (local vs remote), execution logs, and the deploy bar.
 */
public class WorkspacePage {

    private final MainWindow nav;
    private final BorderPane root = new BorderPane();
    private final ExclusionMatcher exclusionMatcher = new ExclusionMatcher();
    
    // Kept as instance variables to allow toggling visibility
    private VBox connPanel;
    private Button connToggleBtn;
    private HBox verifiedBox;

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
    }

    /** @return The root node representing the workspace page. */
    public Node getNode() { return root; }

    /** Builds the full workspace layout. */
    private void build() {
        root.getStyleClass().add("bg-black");

        VBox frame = new VBox(0);
        frame.getStyleClass().addAll("bg-black", "padding-24");

        // App window using the centralized UI builder
        VBox win = UI.appWindow(
            UI.buildTopbar("ZIP file extractor", UI.zipIcon(),
                UI.iconBtn("⏱", "History"), UI.iconBtn("📄", "View log"),
                UI.iconBtn("↻", "Refresh"),  UI.iconBtn("✉", "Send mail")
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
        Label ct = new Label("Production VM — connected · ubuntu@161.118.171.230:22 · ~/HR_MANAGEMENT_SYSTEM");
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

        // Form fields
        form.add(UI.fieldGroup("Host / IP",  UI.textField("161.118.171.230")), 0, 0);
        form.add(UI.fieldGroup("Port",       UI.textField("22")), 1, 0);
        form.add(UI.fieldGroup("Username",   UI.textField("ubuntu")), 2, 0);

        HBox keyRow = new HBox(8); 
        keyRow.setAlignment(Pos.CENTER_LEFT);
        TextField keyField = UI.textField("C:\\Users\\surya\\.ssh\\ssh-key-2026-01-06.key");
        keyField.setEditable(false); 
        HBox.setHgrow(keyField, Priority.ALWAYS);
        Button browse = UI.secondaryBtn("Browse"); browse.setMinWidth(70);
        keyRow.getChildren().addAll(keyField, browse);

        PasswordField passField = new PasswordField(); 
        passField.setText("password");
        passField.getStyleClass().add("text-input"); 
        passField.setMaxWidth(Double.MAX_VALUE);

        form.add(UI.fieldGroup("Private key file", keyRow), 0, 1, 2, 1);
        form.add(UI.fieldGroup("Key passphrase (optional)", passField), 2, 1);
        form.add(UI.fieldGroup("Remote app root", UI.textField("~/HR_MANAGEMENT_SYSTEM")), 0, 2, 3, 1);

        // CLI Equivalent readout
        Label equiv = new Label("equivalent to   ssh -i C:\\Users\\surya\\.ssh\\key.key ubuntu@161.118.171.230");
        equiv.getStyleClass().add("equiv-row"); 
        equiv.setMaxWidth(Double.MAX_VALUE);
        equiv.setPadding(new Insets(9,11,9,11));

        // Action buttons
        HBox actions = new HBox(10); 
        actions.setAlignment(Pos.CENTER_RIGHT); 
        actions.setPadding(new Insets(12,0,0,0));
        Button save = UI.secondaryBtn("Save profile");
        Button test = UI.greenBtn("Test connection");
        test.setOnAction(e -> { 
            verifiedBox.setVisible(true); 
            verifiedBox.setManaged(true); 
        });
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
        Label vt = new Label("Connection verified — Authenticated as ubuntu · host key trusted · remote app root found");
        vt.getStyleClass().add("verified-text"); 
        HBox.setHgrow(vt, Priority.ALWAYS);
        Label vc = new Label("  ✓ Connected  "); 
        vc.getStyleClass().add("btn-connected");
        
        verifiedBox.getChildren().addAll(vIcon, vt, vc);

        connPanel.getChildren().addAll(connBar, form, equiv, actions, verifiedBox);
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
            // Stats will be populated after tree is built
            String statsLabel = "local · read-only";
            if (totalFileCount > 0) {
                String sizeMb = String.format("%.1f MB", totalSizeBytes / 1024.0 / 1024.0);
                statsLabel = totalFileCount + " files · " + sizeMb + " · " + excludedCount + " excluded";
            }
            hdr.getChildren().addAll(sp, UI.label(statsLabel,"explorer-header-sub"));
        } else {
            hdr.getChildren().addAll(UI.serverIcon(), UI.label("Production server","explorer-header-label"));
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            hdr.getChildren().addAll(sp, UI.label("SFTP · destination","explorer-header-sub"));
        }

        TreeView<String> tree = server ? buildServerTree() : buildLocalTree();
        VBox.setVgrow(tree, Priority.ALWAYS);
        ex.getChildren().addAll(hdr, tree);
        return ex;
    }

    /** Builds the local file tree from the extracted zip, applying exclusion tags. */
    @SuppressWarnings("unchecked")
    private TreeView<String> buildLocalTree() {
        ZipExtractor.ExtractionResult result = nav.getExtractionResult();
        TreeItem<String> rootNode;

        if (result != null && result.extractedRoot() != null && result.extractedRoot().exists()) {
            // Real extracted data — build tree with exclusion tags
            totalFileCount = 0;
            excludedCount = 0;
            totalSizeBytes = 0;

            rootNode = buildTreeWithExclusions(result.extractedRoot(), result.extractedRoot().toPath());
            String filename = (nav.getSelectedZip() != null) ? nav.getSelectedZip().getName() : "update package";
            rootNode.setValue("📁 " + filename);
        } else {
            // Fallback mock data
            rootNode = treeFolder("hrms_update (no data)");
            Label placeholder = new Label("Select and validate a zip file to see contents");
            placeholder.getStyleClass().add("text-secondary");
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
            // Sort: directories first, then files alphabetically
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
                        // Show excluded directories as leaf nodes with [EXCLUDED] tag
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
                        // Tag as NEW since we can't compare with server yet (SSH not implemented)
                        item.getChildren().add(treeFile(file.getName(), "NEW", size));
                    }
                }
            }
        }
        return item;
    }

    /** Formats file size in human-readable form. */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    /** Mocks the remote SFTP server TreeView data. */
    @SuppressWarnings("unchecked")
    private TreeView<String> buildServerTree() {
        TreeItem<String> root = treeFolder("HR_MANAGEMENT_SYSTEM [dest]");
        TreeItem<String> backend = treeFolder("backend");
        backend.getChildren().addAll(
            treeFile("server.js", null, "13 KB · Jan 28"),
            treeFile("node_modules", null, null),
            treeFile("uploads", null, "312 files")
        );
        TreeItem<String> frontend = treeFolder("frontend");
        frontend.getChildren().addAll(
            treeFile("dist", null, "Jan 28"),
            treeFile("package.json", null, "2 KB")
        );
        root.getChildren().addAll(backend, frontend,
            treeFile("HRMS_backup_2026-01-28_18-07-08.tar.gz", null, "44 KB")
        );

        TreeView<String> tv = new TreeView<>(root);
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
    
    /** Helper to create a standardized file TreeItem, packing tag and metadata into the string via tabs. */
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
        Label log = new Label("[12:04:01] ready to deploy · awaiting confirmation");
        log.getStyleClass().add("console-text");
        c.getChildren().add(log);
        return c;
    }

    /** Builds the final deployment trigger bar. */
    private HBox buildDeployBar() {
        HBox bar = new HBox(14); 
        bar.getStyleClass().add("deploybar"); 
        bar.setAlignment(Pos.CENTER_LEFT);

        HBox left = new HBox(14); 
        left.setAlignment(Pos.CENTER_LEFT);
        
        Label ready = new Label("✓ extracted · ready"); 
        ready.getStyleClass().add("deploybar-text");
        
        CheckBox email = new CheckBox("Send release email on success");
        email.setSelected(true); 
        email.getStyleClass().add("checkbox-white");
        
        Label rollback = new Label("rollback last deploy"); 
        rollback.getStyleClass().add("deploybar-link");
        
        left.getChildren().addAll(ready, email, rollback);

        Region sp = new Region(); 
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox right = new HBox(10); 
        right.setAlignment(Pos.CENTER_RIGHT);
        
        Button testConn = UI.outlineBtn("Test Connection");
        Button deploy   = UI.greenBtn("Deploy to Server");
        right.getChildren().addAll(testConn, deploy);

        bar.getChildren().addAll(left, sp, right);
        return bar;
    }

    /** Toggles the connection settings panel visibility. */
    private void toggleConn() {
        boolean show = !connPanel.isVisible();
        connPanel.setVisible(show); 
        connPanel.setManaged(show);
        connToggleBtn.setText(show ? "Hide connection" : "Show connection");
    }

    /** 
     * Custom JavaFX TreeCell that renders specialized UI tags (e.g. EXCLUDED, NEW, OVERWRITE) 
     * inline next to the file names by parsing the tab-delimited strings provided by treeFile().
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
