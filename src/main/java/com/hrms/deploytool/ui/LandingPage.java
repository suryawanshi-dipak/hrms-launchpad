package com.hrms.deploytool.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

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

        // Demo controls
        root.getChildren().add(buildDemoBar());
        root.getChildren().add(UI.vspace(8));

        // Frame label
        root.getChildren().add(UI.frameLabel("PAGE 1 OF 3 — LANDING"));

        // App window using the centralized UI builder
        VBox appWindow = UI.appWindow(
            UI.buildTopbar("HRMS deploy tool", UI.cloudIcon(), "page 1 of 3",
                UI.iconBtn("⏱", "History"), UI.iconBtn("📄", "View log"),
                UI.iconBtn("↻", "Refresh"), UI.iconBtn("⚙", "Settings")
            ),
            buildBody()
        );
        VBox.setVgrow(appWindow, Priority.ALWAYS);
        root.getChildren().add(appWindow);
    }

    /**
     * Builds the demo control bar for UI prototyping interactions.
     */
    private HBox buildDemoBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        
        Label demo = new Label("demo:");
        demo.setStyle("-fx-text-fill:#6a6a6a;-fx-font-size:11px;");
        
        Button validBtn  = UI.demoBtn("select valid zip");
        Button corruptBtn= UI.demoBtn("select corrupt zip");
        Button resetBtn  = UI.demoBtn("reset to page 1");
        
        // Navigation triggers
        validBtn.setOnAction(e  -> nav.showValidation(false));
        corruptBtn.setOnAction(e-> nav.showValidation(true));
        resetBtn.setOnAction(e  -> nav.showLanding());
        
        bar.getChildren().addAll(demo, validBtn, corruptBtn, resetBtn);
        return bar;
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
     * Builds the "New deployment" card which contains the file drop zone and currently staged file chip.
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

        // Drop zone mimicking HTML 5 drag-and-drop area
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
        dz.setOnMouseClicked(e -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Select Update Zip File");
            fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("ZIP Files", "*.zip"));
            java.io.File selectedFile = fileChooser.showOpenDialog(dz.getScene().getWindow());
            if (selectedFile != null) {
                nav.setSelectedZip(selectedFile);
                nav.showValidation(false);
            }
        });
        // Staged file chip preview
        HBox chip = new HBox(10);
        chip.getStyleClass().add("file-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setMaxWidth(Double.MAX_VALUE);
        chip.getChildren().addAll(
            UI.zipIcon(),
            UI.bold("hrms_update_v1.8.3.zip", "fname"),
            UI.label("· 44.2 MB", "fsize"),
            UI.checkPill("✓ no path traversal"),
            UI.checkPill("✓ structure matches")
        );
        chip.setOnMouseClicked(e -> nav.showValidation(false));

        card.getChildren().addAll(hdr, UI.vspace(14), dz, UI.vspace(14), chip);
        VBox.setMargin(card, new Insets(0, 0, 2, 0));
        return card;
    }

    // ── Backup card ─────────────────────────────────────────────────────────
    
    /**
     * Builds the backup card showing the last successfully generated rollback archive.
     */
    private VBox buildBackupCard() {
        VBox card = UI.card();
        Label lbl  = new Label("LAST BACKUP"); lbl.getStyleClass().add("backup-label");
        Label name = new Label("HRMS_backup_2026-07-20_09-14-02.tar.gz"); name.getStyleClass().add("backup-name");
        
        HBox meta  = new HBox(5); 
        meta.setAlignment(Pos.CENTER_LEFT);
        meta.getChildren().addAll(UI.shieldIcon(), UI.label("verified · 41.8 MB","backup-meta"));

        Separator sep = new Separator(); 
        sep.setPadding(new Insets(14,0,0,0));

        HBox logRow = new HBox(); 
        logRow.setAlignment(Pos.CENTER_LEFT);
        logRow.setPadding(new Insets(14,0,0,0));
        
        HBox logL = new HBox(5); 
        logL.setAlignment(Pos.CENTER_LEFT);
        logL.getChildren().addAll(UI.docIcon(), UI.link("View deploy log"));
        
        Region sp = new Region(); 
        HBox.setHgrow(sp, Priority.ALWAYS);
        
        Label arr = new Label("↗"); arr.setStyle("-fx-text-fill:#9a9a9a;-fx-font-size:13px;");
        logRow.getChildren().addAll(logL, sp, arr);

        card.getChildren().addAll(lbl, UI.vspace(6), name, UI.vspace(6), meta, sep, logRow);
        VBox.setMargin(card, new Insets(0,0,2,0));
        return card;
    }

    // ── History card ────────────────────────────────────────────────────────
    
    /**
     * Builds the deployment history table.
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
        Label last4 = new Label("last 4 releases"); last4.getStyleClass().add("text-secondary");
        hdr.getChildren().addAll(hl, sp, last4);

        TableView<HistRow> table = buildHistTable();
        table.setMaxHeight(200);
        table.setFixedCellSize(50);

        card.getChildren().addAll(hdr, UI.vspace(12), table);
        return card;
    }

    /**
     * Creates and configures the History TableView.
     */
    @SuppressWarnings("unchecked")
    private TableView<HistRow> buildHistTable() {
        TableView<HistRow> table = new TableView<>();
        table.getStyleClass().add("hist-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setSelectionModel(null);

        TableColumn<HistRow,String> tsCol  = col("TIMESTAMP", "timestamp", 180);
        TableColumn<HistRow,String> verCol = col("VERSION",   "version",   80);
        TableColumn<HistRow,String> stCol  = statusCol();
        TableColumn<HistRow,String> szCol  = col("SIZE",      "size",      120);
        
        table.getColumns().addAll(tsCol, verCol, stCol, szCol);

        ObservableList<HistRow> data = FXCollections.observableArrayList(
            new HistRow("2026-07-20 09:14","v1.8.2","success","42.1 MB"),
            new HistRow("2026-07-18 16:02","v1.8.1","success","38.6 MB"),
            new HistRow("2026-07-15 11:47","v1.8.0","rolled back","51.3 MB"),
            new HistRow("2026-07-11 08:30","v1.7.9","success","36.9 MB")
        );
        table.setItems(data);
        return table;
    }

    /** Helper to build standard text-based TableColumns. */
    private TableColumn<HistRow,String> col(String title, String prop, int w) {
        TableColumn<HistRow,String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setMinWidth(w);
        return c;
    }

    /** Helper to build the status column which renders custom visual badges instead of raw text. */
    private TableColumn<HistRow,String> statusCol() {
        TableColumn<HistRow,String> c = new TableColumn<>("STATUS");
        c.setMinWidth(110);
        c.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { 
                    setGraphic(null); 
                    return; 
                }
                Label badge = new Label(item);
                badge.getStyleClass().add(item.equals("success") ? "badge-success" : "badge-rolled");
                setGraphic(badge); 
                setText(null);
            }
        });
        return c;
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
