package com.hrms.deploytool.ui;

import com.hrms.deploytool.archive.ZipExtractor;
import com.hrms.deploytool.archive.ZipValidator;

import javafx.scene.Parent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 
 * Root navigation controller.
 * Maintains the primary StackPane layout and handles swapping the visible page
 * (Landing vs. Workspace) as well as overlaying the Validation Modal.
 */
public class MainWindow {

    private final Stage stage;
    private final StackPane root = new StackPane();

    private LandingPage landingPage;
    private ValidationModal validationModal;
    private WorkspacePage workspacePage;

    private File selectedZip;
    private ZipValidator.ValidationResult validationResult;
    private ZipExtractor.ExtractionResult extractionResult;

    /** Tracks temp directories for cleanup on exit. */
    private final List<File> tempDirsToCleanup = new ArrayList<>();

    public void setSelectedZip(File selectedZip) { this.selectedZip = selectedZip; }
    public File getSelectedZip() { return selectedZip; }
    
    public void setValidationResult(ZipValidator.ValidationResult result) { this.validationResult = result; }
    public ZipValidator.ValidationResult getValidationResult() { return validationResult; }

    public void setExtractionResult(ZipExtractor.ExtractionResult result) {
        this.extractionResult = result;
        if (result != null && result.rawTempDir() != null) {
            tempDirsToCleanup.add(result.rawTempDir());
        }
    }
    public ZipExtractor.ExtractionResult getExtractionResult() { return extractionResult; }

    /**
     * Constructs the main window and initializes all UI pages.
     * @param stage The primary JavaFX stage.
     */
    public MainWindow(Stage stage) {
        this.stage = stage;
        landingPage      = new LandingPage(this);
        validationModal  = new ValidationModal(this);
        workspacePage    = new WorkspacePage(this);

        root.getStyleClass().add("bg-black");
        showLanding();
    }

    /** @return The base container holding the active pages. */
    public Parent getRoot() { return root; }

    /** @return The primary JavaFX stage. */
    public Stage getStage() { return stage; }

    /** Displays the initial landing dashboard page. */
    public void showLanding() {
        if (validationModal != null) validationModal.stopFlow();
        landingPage = new LandingPage(this);
        root.getChildren().setAll(landingPage.getNode());
    }

    /**
     * Overlays the validation modal over the landing page.
     * Validation results are determined by the actual zip file content,
     * not a demo toggle.
     */
    public void showValidation() {
        validationModal = new ValidationModal(this);
        root.getChildren().setAll(landingPage.getNode(), validationModal.getNode());
        validationModal.startFlow();
    }

    /** Displays the workspace planning page. */
    public void showWorkspace() {
        if (validationModal != null) validationModal.stopFlow();
        workspacePage = new WorkspacePage(this);
        root.getChildren().setAll(workspacePage.getNode());
    }

    /**
     * Cleans up all temporary extraction directories.
     * Called from App.stop() when the application is closing.
     */
    public void cleanup() {
        for (File dir : tempDirsToCleanup) {
            ZipExtractor.deleteRecursively(dir);
        }
        tempDirsToCleanup.clear();
    }
}
