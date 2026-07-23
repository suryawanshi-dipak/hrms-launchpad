package com.hrms.deploytool.ui;

import com.hrms.deploytool.archive.ZipExtractor;
import com.hrms.deploytool.archive.ZipValidator;
import com.hrms.deploytool.archive.ZipValidator.ValidationResult;
import com.hrms.deploytool.archive.ZipExtractor.ExtractionResult;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 
 * Page 2 — Validation modal overlay with animated checklist.
 * Runs real ZIP validation (corruption, encryption, Zip Slip, structure)
 * followed by extraction, displaying progress via animated step indicators.
 */
public class ValidationModal {

    private final MainWindow nav;
    private final StackPane root = new StackPane();
    private final VBox checklistBox = new VBox(9);
    private final VBox resultBox    = new VBox(10);

    /** Validation steps displayed in the checklist — matches ZipValidator step callbacks. */
    private static final String[] STEPS = {
        "reading archive", "checking for corruption",
        "checking for encryption", "scanning for unsafe paths",
        "verifying structure"
    };

    private Label[] icons; 
    private Label[] texts;
    private Task<?> activeTask;
    private Label filenameLabel;

    /** Shared single-thread executor — ensures only one validation runs at a time (LLD §7). */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "deploy-worker");
        t.setDaemon(true);
        return t;
    });

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
        card.setMaxWidth(380); 
        card.setMaxHeight(320);
        card.setPadding(new Insets(18, 20, 18, 20));

        // Header row
        HBox hdr = new HBox(); 
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(0,0,14,0));
        
        HBox hl = new HBox(8); 
        hl.setAlignment(Pos.CENTER_LEFT);
        String filename = (nav.getSelectedZip() != null) ? nav.getSelectedZip().getName() : "hrms_update.zip";
        filenameLabel = UI.bold(filename,"modal-fname");
        hl.getChildren().addAll(UI.zipIcon(), filenameLabel);
        
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
     * Initiates the real validation + extraction flow.
     * Validation determines pass/fail based on actual zip content — no demo toggle.
     */
    public void startFlow() {
        if (nav.getSelectedZip() != null) {
            filenameLabel.setText(nav.getSelectedZip().getName());
        }

        resultBox.getChildren().clear();
        resetIcons();
        if (activeTask != null) activeTask.cancel();

        activeTask = new Task<ExtractionResult>() {
            @Override
            protected ExtractionResult call() throws Exception {
                if (nav.getSelectedZip() == null) {
                    throw new Exception("No zip file selected");
                }

                // Phase 1: Validate
                ValidationResult validation = ZipValidator.validate(
                    nav.getSelectedZip(),
                    step -> Platform.runLater(() -> tick(step, false))
                );

                if (!validation.valid()) {
                    throw new Exception(validation.errorMessage());
                }

                // Store validation result
                Platform.runLater(() -> nav.setValidationResult(validation));

                // Phase 2: Extract
                ExtractionResult extraction = ZipExtractor.extract(
                    nav.getSelectedZip(),
                    step -> {} // extraction is a single step, already shown as last validation step
                );

                return extraction;
            }
        };

        activeTask.setOnSucceeded(e -> {
            @SuppressWarnings("unchecked")
            ExtractionResult result = ((Task<ExtractionResult>) activeTask).getValue();
            nav.setExtractionResult(result);
            tick(STEPS.length, false); // Mark all steps complete
            showSuccess(result);
        });

        activeTask.setOnFailed(e -> {
            Throwable ex = activeTask.getException();
            System.err.println("Validation failed: " + ex.getMessage());
            // Mark the current step as failed
            markCurrentStepFailed();
            showError(ex.getMessage());
        });

        WORKER.submit(activeTask);
    }

    /**
     * Stops the validation and cleans up. Should be called if navigating away early.
     */
    public void stopFlow() {
        if (activeTask != null) activeTask.cancel();
    }

    /** Logic applied on every tick to update checklist row visuals. */
    private void tick(int idx, boolean fail) {
        // Mark previous step as done
        if (idx > 0) {
            int p = idx - 1;
            icons[p].setText("✓");
            icons[p].setStyle("-fx-text-fill:#7ec97e;-fx-font-size:14px;");
            texts[p].getStyleClass().setAll("check-row-text");
        }

        // Completion — all steps done
        if (idx >= STEPS.length) {
            return;
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

    /** Marks the most recently active step as failed. */
    private void markCurrentStepFailed() {
        for (int i = STEPS.length - 1; i >= 0; i--) {
            String iconText = icons[i].getText();
            if ("◉".equals(iconText) || "◌".equals(iconText)) {
                if ("◉".equals(iconText)) {
                    // This was the active step — mark it as failed
                    icons[i].setText("✗");
                    icons[i].setStyle("-fx-text-fill:#e06c75;-fx-font-size:14px;");
                    texts[i].getStyleClass().setAll("check-row-dim");
                    return;
                }
            }
        }
        // If no active step found, mark the first uncompleted one
        for (int i = 0; i < STEPS.length; i++) {
            if (!"✓".equals(icons[i].getText())) {
                icons[i].setText("✗");
                icons[i].setStyle("-fx-text-fill:#e06c75;-fx-font-size:14px;");
                texts[i].getStyleClass().setAll("check-row-dim");
                return;
            }
        }
    }

    /** Displays the success result box with extraction stats and continue button. */
    private void showSuccess(ExtractionResult result) {
        resultBox.getChildren().clear();
        VBox box = new VBox(2); 
        box.getStyleClass().add("result-success");
        
        Label t = new Label("zip extracted successfully"); 
        t.getStyleClass().add("result-title-ok");
        
        String sizeMb = String.format("%.1f", result.totalBytes() / 1024.0 / 1024.0);
        String wrapperNote = (result.wrapperFolderName() != null) 
            ? " · wrapper folder '" + result.wrapperFolderName() + "' detected"
            : "";
        Label s = new Label(result.fileCount() + " files · " + sizeMb + " mb" + wrapperNote); 
        s.getStyleClass().add("result-sub-ok");
        
        box.getChildren().addAll(t, s);
        resultBox.getChildren().add(box);

        // Structure warning — non-blocking dialog per LLD §6
        ValidationResult validation = nav.getValidationResult();
        if (validation != null && validation.structureWarning()) {
            VBox warnBox = new VBox(2);
            warnBox.setStyle("-fx-background-color:#3a2f1f;-fx-border-color:#5c3a1f;"
                + "-fx-border-width:1;-fx-border-radius:6;-fx-background-radius:6;"
                + "-fx-padding:8 12 8 12;");
            Label warnTitle = new Label("⚠ structure warning");
            warnTitle.setStyle("-fx-text-fill:#dcb67a;-fx-font-size:11px;-fx-font-weight:bold;");
            Label warnMsg = new Label(validation.structureWarningMsg());
            warnMsg.setStyle("-fx-text-fill:#c9b47a;-fx-font-size:11px;");
            warnMsg.setWrapText(true);
            warnBox.getChildren().addAll(warnTitle, warnMsg);
            resultBox.getChildren().add(warnBox);
        }

        Button cont = UI.primaryBtn("continue"); 
        cont.setMaxWidth(Double.MAX_VALUE);
        cont.setOnAction(e -> nav.showWorkspace());
        
        resultBox.getChildren().add(cont);
    }

    /** Displays the error result box with the actual error message and retry button. */
    private void showError(String errorMessage) {
        resultBox.getChildren().clear();
        VBox box = new VBox(2); 
        box.getStyleClass().add("result-error");
        
        Label t = new Label("validation failed"); 
        t.getStyleClass().add("result-title-err");
        
        String displayMsg = (errorMessage != null) ? errorMessage 
            : "This archive could not be validated. Choose a different file.";
        Label s = new Label(displayMsg); 
        s.getStyleClass().add("result-sub-err");
        s.setWrapText(true);
        
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
