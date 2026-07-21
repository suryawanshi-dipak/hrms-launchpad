package com.hrms.deploytool.ui;

import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

/** 
 * Page 2 — Validation modal overlay with animated checklist.
 * This class handles the mock progression of the ZIP validation logic,
 * utilizing a JavaFX Timeline to animate through checks.
 */
public class ValidationModal {

    private final MainWindow nav;
    private final StackPane root = new StackPane();
    private final VBox checklistBox = new VBox(9);
    private final VBox resultBox    = new VBox(10);

    private static final String[] STEPS = {
        "reading archive", "checking for corruption",
        "scanning for unsafe paths", "extracting files"
    };

    private Label[] icons; 
    private Label[] texts;
    private Timeline timeline;

    /**
     * Constructs the ValidationModal.
     * @param nav The main window controller used for page transitions.
     */
    public ValidationModal(MainWindow nav) {
        this.nav = nav;
        build();
    }

    /** @return The root node representing the modal overlay. */
    public Node getNode() { return root; }

    /** Builds the modal layout and semi-transparent background overlay. */
    private void build() {
        // Semi-transparent overlay
        VBox overlay = new VBox();
        overlay.setStyle("-fx-background-color:rgba(0,0,0,0.55);");
        overlay.setAlignment(Pos.CENTER);
        StackPane.setAlignment(overlay, Pos.CENTER);

        // Modal card container
        VBox card = new VBox(0);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(340); 
        card.setMaxHeight(280);
        card.setPadding(new Insets(18, 20, 18, 20));

        // Header row
        HBox hdr = new HBox(); 
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(0,0,14,0));
        
        HBox hl = new HBox(8); 
        hl.setAlignment(Pos.CENTER_LEFT);
        hl.getChildren().addAll(UI.zipIcon(), UI.bold("hrms_update.zip","modal-fname"));
        
        Region sp = new Region(); 
        HBox.setHgrow(sp, Priority.ALWAYS);
        
        Label pg = new Label("page 2 of 3"); 
        pg.getStyleClass().add("modal-page");
        hdr.getChildren().addAll(hl, sp, pg);

        // Checklist rows
        icons = new Label[STEPS.length];
        texts = new Label[STEPS.length];
        checklistBox.getStyleClass().add("padding-0");
        
        for (int i = 0; i < STEPS.length; i++) {
            icons[i] = new Label("◌");
            icons[i].setStyle("-fx-text-fill:#666666;-fx-font-size:14px;");
            
            texts[i] = new Label(STEPS[i]);
            texts[i].getStyleClass().add("check-row-dim");
            
            HBox row = new HBox(8); 
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(icons[i], texts[i]);
            checklistBox.getChildren().add(row);
        }

        resultBox.setPadding(new Insets(14,0,0,0));
        card.getChildren().addAll(hdr, checklistBox, resultBox);

        overlay.getChildren().add(card);
        root.getChildren().add(overlay);
    }

    /**
     * Initiates the validation flow animation.
     * @param fail If true, the validation process will mock a corruption failure.
     */
    public void startFlow(boolean fail) {
        resultBox.getChildren().clear();
        resetIcons();
        if (timeline != null) timeline.stop();

        int[] step = {0};
        timeline = new Timeline();

        for (int i = 0; i <= STEPS.length; i++) {
            final int idx = i;
            // Schedule keyframes to tick through the list every 600ms
            KeyFrame kf = new KeyFrame(Duration.millis(i * 600), e -> tick(idx, fail, step));
            timeline.getKeyFrames().add(kf);
        }
        timeline.setCycleCount(1);
        timeline.play();
    }

    /**
     * Stops the animation and cleans up. Should be called if navigating away early.
     */
    public void stopFlow() {
        if (timeline != null) timeline.stop();
    }

    /** Logic applied on every tick of the timeline animation to update rows. */
    private void tick(int idx, boolean fail, int[] step) {
        // Mark previous done / fail
        if (idx > 0) {
            int p = idx - 1;
            boolean pf = fail && p == 1;
            icons[p].setText(pf ? "✗" : "✓");
            icons[p].setStyle(pf ? "-fx-text-fill:#e06c75;-fx-font-size:14px;"
                                 : "-fx-text-fill:#7ec97e;-fx-font-size:14px;");
            texts[p].getStyleClass().setAll(pf ? "check-row-dim" : "check-row-text");
        }

        // Trigger failure midway
        if (fail && idx == 1) {
            icons[idx].setText("✗");
            icons[idx].setStyle("-fx-text-fill:#e06c75;-fx-font-size:14px;");
            texts[idx].getStyleClass().setAll("check-row-dim");
            timeline.stop();
            showError();
            return;
        }
        
        // Completion
        if (idx >= STEPS.length) {
            showSuccess(); return;
        }

        // Active state with spinning animation
        icons[idx].setText("◉");
        icons[idx].setStyle("-fx-text-fill:#4fc3f7;-fx-font-size:14px;");
        texts[idx].getStyleClass().setAll("check-row-text");

        RotateTransition spin = new RotateTransition(Duration.millis(500), icons[idx]);
        spin.setByAngle(360); 
        spin.setCycleCount(1); 
        spin.play();
    }

    /** Displays the success alert box and continue button. */
    private void showSuccess() {
        resultBox.getChildren().clear();
        VBox box = new VBox(2); 
        box.getStyleClass().add("result-success");
        
        Label t = new Label("zip extracted successfully"); 
        t.getStyleClass().add("result-title-ok");
        
        Label s = new Label("214 files · 18.4 mb · 3 excluded by policy"); 
        s.getStyleClass().add("result-sub-ok");
        
        box.getChildren().addAll(t, s);
        Button cont = UI.primaryBtn("continue"); 
        cont.setMaxWidth(Double.MAX_VALUE);
        cont.setOnAction(e -> nav.showWorkspace());
        
        resultBox.getChildren().addAll(box, cont);
    }

    /** Displays the error alert box and return button. */
    private void showError() {
        resultBox.getChildren().clear();
        VBox box = new VBox(2); 
        box.getStyleClass().add("result-error");
        
        Label t = new Label("zip is corrupted"); 
        t.getStyleClass().add("result-title-err");
        
        Label s = new Label("this archive could not be read. choose a different file."); 
        s.getStyleClass().add("result-sub-err");
        
        box.getChildren().addAll(t, s);
        Button choose = UI.secondaryBtn("choose another file"); 
        choose.setMaxWidth(Double.MAX_VALUE);
        choose.setOnAction(e -> nav.showLanding());
        
        resultBox.getChildren().addAll(box, choose);
    }

    /** Resets the visual state of all checklist items. */
    private void resetIcons() {
        for (int i = 0; i < STEPS.length; i++) {
            icons[i].setText("◌");
            icons[i].setStyle("-fx-text-fill:#666666;-fx-font-size:14px;");
            texts[i].getStyleClass().setAll("check-row-dim");
        }
    }
}
