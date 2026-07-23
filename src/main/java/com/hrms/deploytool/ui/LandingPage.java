package com.hrms.deploytool.ui;

import com.hrms.deploytool.archive.ZipExtractor;
import com.hrms.deploytool.archive.ZipValidator;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;

import java.io.File;
import java.util.List;

/** 
 * Page 1 — Landing / Deployment Overview.
 * Displays the current deployment status, recent backups, deployment history,
 * and provides a drop zone for initiating a new update.
 */
public class LandingPage {

    private final MainWindow nav;
    private final VBox root = new VBox(0);

    /**
     * Constructs the LandingPage and binds the navigation controller.
     * @param nav The main window controller used for page transitions.
     */
    public LandingPage(MainWindow nav) {
        this.nav = nav;
        build();
    }

    /**
     * @return The root JavaFX node for this page.
     */
    public Node getNode() { return root; }

    /**
     * Assembles the full page layout including the demo bar, frame labels, and main application window.
     */
    private void build() {
        root.getStyleClass().addAll("bg-black", "padding-24");

        // App window using the centralized UI builder
        VBox appWindow = UI.appWindow(
            UI.buildTopbar("HRMS deploy tool", UI.cloudIcon(),
                UI.iconBtn("⏱", "History"), UI.iconBtn("📄", "View log"),
                UI.iconBtn("↻", "Refresh"), UI.iconBtn("⚙", "Settings")
            ),
            buildBody()
        );
        VBox.setVgrow(appWindow, Priority.ALWAYS);
        root.getChildren().add(appWindow);
    }


    /**
     * Assembles the scrolling main body of the landing page containing the deployment cards.
     */
    private ScrollPane buildBody() {
        VBox body = new VBox(0);
        body.getStyleClass().add("page-body");
        body.setFillWidth(true);

        // Eyebrow label for branding
        HBox eyebrow = new HBox(7);
        eyebrow.setAlignment(Pos.CENTER_LEFT);
        eyebrow.setPadding(new Insets(0, 0, 6, 0));
        eyebrow.getChildren().addAll(UI.buildingIcon(), UI.eyebrow("HR MANAGEMENT SYSTEM"));

        // Head row showing project context
        HBox headRow = new HBox();
        headRow.setAlignment(Pos.CENTER_LEFT);
        headRow.setPadding(new Insets(0, 0, 18, 0));
        
        Label title = new Label("Deployment overview");
        title.getStyleClass().add("p1-title");
        
        Region sp = new Region(); 
        HBox.setHgrow(sp, Priority.ALWAYS);
        
        Label pill = new Label("  app root ~/HR_MANAGEMENT_SYSTEM  ");
        pill.getStyleClass().add("root-pill");
        headRow.getChildren().addAll(title, sp, pill);

        body.getChildren().addAll(
            eyebrow, headRow,
            buildDeployCard(), UI.vspace(2),
            buildBackupCard(), UI.vspace(2),
            buildHistoryCard()
        );

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    // ── New Deployment card ──────────────────────────────────────────────────
    
    /**
     * Builds the "New deployment" card which contains the file drop zone and the file chip.
     * The file chip is dynamically populated from the last successful validation result,
     * or hidden if no zip has been validated yet.
     */
    private VBox buildDeployCard() {
        VBox card = UI.card();

        HBox hdr = new HBox(); 
        hdr.setAlignment(Pos.CENTER_LEFT);
        
        Label ct = new Label("New deployment"); 
        ct.getStyleClass().add("card-title");
        
        Region sp = new Region(); 
        HBox.setHgrow(sp, Priority.ALWAYS);
        
        HBox envOk = new HBox(6); 
        envOk.setAlignment(Pos.CENTER_LEFT);
        envOk.getChildren().addAll(UI.checkCircle(13, "#7ec97e"), UI.envReady("environment ready"));
        hdr.getChildren().addAll(ct, sp, envOk);

        // Drop zone with real drag-and-drop support
        VBox dz = new VBox(10);
        dz.getStyleClass().add("dropzone");
        dz.setAlignment(Pos.CENTER);
        dz.setMaxWidth(Double.MAX_VALUE);
        
        Label dzIcon  = new Label("⬆");
        dzIcon.setStyle("-fx-font-size:22px;-fx-text-fill:#a0a0a8;");
        
        HBox dzTitle = new HBox(4); 
        dzTitle.setAlignment(Pos.CENTER);
        Label dzT = new Label("Drop an update zip, or "); dzT.getStyleClass().add("dz-title");
        Label dzB = new Label("browse"); dzB.getStyleClass().addAll("dz-title","dz-browse");
        dzTitle.getChildren().addAll(dzT, dzB);
        
        Label dzSub = new Label("hrms_update_*.zip"); dzSub.getStyleClass().add("dz-sub");
        dz.getChildren().addAll(dzIcon, dzTitle, dzSub);

        // Click to browse
        dz.setOnMouseClicked(e -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Select Update Zip File");
            fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("ZIP Files", "*.zip"));
            File selectedFile = fileChooser.showOpenDialog(dz.getScene().getWindow());
            if (selectedFile != null) {
                nav.setSelectedZip(selectedFile);
                nav.showValidation();
            }
        });

        // Drag-and-drop support
        dz.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        dz.setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles()) {
                dz.setStyle(dz.getStyle() + "-fx-border-color:#0e639c;-fx-background-color:#162030;");
            }
        });
        dz.setOnDragExited(event -> {
            // Reset style by removing inline overrides (CSS class handles default)
            dz.setStyle("");
        });
        dz.setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles();
            if (files != null) {
                File zipFile = files.stream()
                    .filter(f -> f.getName().toLowerCase().endsWith(".zip"))
                    .findFirst()
                    .orElse(null);
                if (zipFile != null) {
                    nav.setSelectedZip(zipFile);
                    nav.showValidation();
                    event.setDropCompleted(true);
                } else {
                    event.setDropCompleted(false);
                }
            }
            event.consume();
        });

        // Dynamic file chip — populated from validation/extraction results
        HBox chip = buildFileChip();

        card.getChildren().addAll(hdr, UI.vspace(14), dz, UI.vspace(14), chip);
        VBox.setMargin(card, new Insets(0, 0, 2, 0));
        return card;
    }

    /**
     * Builds the file chip showing the last validated zip info.
     * If no zip has been validated, shows a placeholder prompt.
     */
    private HBox buildFileChip() {
        HBox chip = new HBox(10);
        chip.getStyleClass().add("file-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setMaxWidth(Double.MAX_VALUE);

        ZipExtractor.ExtractionResult extraction = nav.getExtractionResult();
        ZipValidator.ValidationResult validation = nav.getValidationResult();

        if (extraction != null && nav.getSelectedZip() != null) {
            // Real data from the last successful validation
            String filename = nav.getSelectedZip().getName();
            String sizeMb = String.format("%.1f MB", nav.getSelectedZip().length() / 1024.0 / 1024.0);

            chip.getChildren().addAll(
                UI.zipIcon(),
                UI.bold(filename, "fname"),
                UI.label("· " + sizeMb, "fsize"),
                UI.checkPill("✓ no path traversal"),
                UI.checkPill("✓ structure matches")
            );
            chip.setOnMouseClicked(e -> nav.showWorkspace());
        } else {
            // Placeholder — no zip validated yet
            Label placeholder = new Label("No update package validated yet — select a zip above");
            placeholder.getStyleClass().add("fsize");
            chip.getChildren().addAll(UI.zipIcon(), placeholder);
            chip.setOpacity(0.5);
        }

        return chip;
    }

    // ── Backup card ─────────────────────────────────────────────────────────
    
    /**
     * Builds the backup card. Shows placeholder when no backup data exists.
     * (Will be populated from config/SSH data in a future phase.)
     */
    private VBox buildBackupCard() {
        VBox card = UI.card();
        Label lbl  = new Label("LAST BACKUP"); lbl.getStyleClass().add("backup-label");

        // Placeholder — no real backup data available yet
        Label name = new Label("No backup data available"); 
        name.getStyleClass().add("backup-name");
        name.setOpacity(0.5);
        
        HBox meta  = new HBox(5); 
        meta.setAlignment(Pos.CENTER_LEFT);
        Label metaText = new Label("Connect to the server to view backup history");
        metaText.getStyleClass().add("backup-meta");
        metaText.setOpacity(0.5);
        meta.getChildren().addAll(UI.shieldIcon(), metaText);

        Separator sep = new Separator(); 
        sep.setPadding(new Insets(14,0,0,0));

        HBox logRow = new HBox(); 
        logRow.setAlignment(Pos.CENTER_LEFT);
        logRow.setPadding(new Insets(14,0,0,0));
        
        HBox logL = new HBox(5); 
        logL.setAlignment(Pos.CENTER_LEFT);
        logL.getChildren().addAll(UI.docIcon(), UI.link("View deploy log"));
        
        Region sp2 = new Region(); 
        HBox.setHgrow(sp2, Priority.ALWAYS);
        
        Label arr = new Label("↗"); arr.setStyle("-fx-text-fill:#9a9a9a;-fx-font-size:13px;");
        logRow.getChildren().addAll(logL, sp2, arr);

        card.getChildren().addAll(lbl, UI.vspace(6), name, UI.vspace(6), meta, sep, logRow);
        VBox.setMargin(card, new Insets(0,0,2,0));
        return card;
    }

    // ── History card ────────────────────────────────────────────────────────
    
    /**
     * Builds the deployment history table.
     * Shows a placeholder when no deployment history exists.
     */
    private VBox buildHistoryCard() {
        VBox card = UI.card();

        HBox hdr = new HBox(); 
        hdr.setAlignment(Pos.CENTER_LEFT);
        
        HBox hl  = new HBox(6); 
        hl.setAlignment(Pos.CENTER_LEFT);
        hl.getChildren().addAll(UI.clockIcon(), UI.label("Deployment history","card-title"));
        
        Region sp = new Region(); 
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label placeholder = new Label("no deployments yet"); 
        placeholder.getStyleClass().add("text-secondary");
        hdr.getChildren().addAll(hl, sp, placeholder);

        // Empty state label
        Label emptyMsg = new Label("Deployment history will appear here after your first deployment.");
        emptyMsg.getStyleClass().add("text-secondary");
        emptyMsg.setOpacity(0.5);
        emptyMsg.setPadding(new Insets(20, 0, 20, 0));

        card.getChildren().addAll(hdr, UI.vspace(12), emptyMsg);
        return card;
    }

    /** Data model for the History TableView. */
    public static class HistRow {
        private final String timestamp, version, status, size;
        public HistRow(String ts, String v, String st, String sz) { timestamp=ts; version=v; status=st; size=sz; }
        public String getTimestamp(){ return timestamp; }
        public String getVersion()  { return version; }
        public String getStatus()   { return status; }
        public String getSize()     { return size; }
    }
}
